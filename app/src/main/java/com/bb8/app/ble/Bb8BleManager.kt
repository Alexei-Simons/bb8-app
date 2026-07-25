package com.bb8.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.bb8.app.sphero.RollMode
import com.bb8.app.sphero.ReverseFlag
import com.bb8.app.sphero.SpheroCommands
import com.bb8.app.sphero.SpheroPacket
import com.bb8.app.sphero.SpheroPacketBuilder
import com.bb8.app.sphero.SpheroResponse
import com.bb8.app.sphero.SpheroResponseParser
import com.bb8.app.sphero.SpheroUuids
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

data class ScannedDevice(
    val name: String,
    val address: String,
)

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Scanning : ConnectionState()
    data object Connecting : ConnectionState()
    data object Handshaking : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

@SuppressLint("MissingPermission")
class Bb8BleManager(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private val packetBuilder = SpheroPacketBuilder()
    private val responseParser = SpheroResponseParser()
    private val writeMutex = Mutex()
    private val pendingWrite = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val pendingResponse = AtomicReference<CompletableDeferred<SpheroResponse>?>(null)

    private val deviceByAddress = mutableMapOf<String, BluetoothDevice>()
    private var connectionTimeoutJob: Job? = null
    private var keepaliveJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _batteryHealth = MutableStateFlow<BatteryHealth?>(null)
    val batteryHealth: StateFlow<BatteryHealth?> = _batteryHealth.asStateFlow()

    private val _batteryReadAttempted = MutableStateFlow(false)
    val batteryReadAttempted: StateFlow<Boolean> = _batteryReadAttempted.asStateFlow()

    private var targetDeviceName: String? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            val name = result.device.name ?: return
            if (!name.startsWith(SpheroUuids.NAME_PREFIX)) return

            deviceByAddress[result.device.address] = result.device
            val device = ScannedDevice(name = name, address = result.device.address)
            _scannedDevices.update { current ->
                if (current.any { it.address == device.address }) current
                else current + device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.Error("BLE scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        failConnection("GATT connect failed (status $status)")
                        return
                    }
                    connectionTimeoutJob?.cancel()
                    this@Bb8BleManager.gatt = gatt
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    gatt.requestMtu(517)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectionTimeoutJob?.cancel()
                    stopKeepalive()
                    if (_connectionState.value !is ConnectionState.Disconnected) {
                        val message = if (status != BluetoothGatt.GATT_SUCCESS) {
                            "Disconnected (status $status)"
                        } else {
                            null
                        }
                        cleanupGatt()
                        _connectionState.value = message?.let { ConnectionState.Error(it) }
                            ?: ConnectionState.Disconnected
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu (status=$status)")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("Service discovery failed: $status")
                return
            }
            scope.launch { runHandshake(gatt) }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            completePendingWrite(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            completePendingWrite(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { handleNotification(it) }
        }
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun startScan() {
        val bleAdapter = adapter
        if (bleAdapter == null || !bleAdapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth is off")
            return
        }

        stopScan()
        _scannedDevices.value = emptyList()
        _batteryHealth.value = null
        _batteryReadAttempted.value = false
        deviceByAddress.clear()
        _connectionState.value = ConnectionState.Scanning

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleAdapter.bluetoothLeScanner.startScan(null, settings, scanCallback)
    }

    fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun connect(device: ScannedDevice) {
        val bleAdapter = adapter
        if (bleAdapter == null || !bleAdapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth is off")
            return
        }

        stopScan()
        targetDeviceName = device.name
        _connectionState.value = ConnectionState.Connecting

        val bluetoothDevice = deviceByAddress[device.address]
            ?: bleAdapter.getRemoteDevice(device.address)

        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_connectionState.value is ConnectionState.Connecting ||
                _connectionState.value is ConnectionState.Handshaking
            ) {
                failConnection("Connection timed out. Take BB-8 off the charger and try again.")
            }
        }

        gatt?.close()
        responseParser.reset()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothDevice.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            bluetoothDevice.connectGatt(appContext, false, gattCallback)
        }
    }

    fun disconnect() {
        scope.launch {
            stopKeepalive()
            writeMutex.withLock {
                runCatching {
                    sendPacketLocked(SpheroCommands.roll(packetBuilder, 0, 0, RollMode.STOP, ReverseFlag.OFF))
                }
            }
            connectionTimeoutJob?.cancel()
            gatt?.disconnect()
            cleanupGatt()
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    suspend fun drive(speed: Int, heading: Int): Boolean {
        if (commandCharacteristic == null || gatt == null) return false
        if (_connectionState.value !is ConnectionState.Connected) return false

        return runCatching {
            sendPacket(SpheroCommands.roll(
                packetBuilder,
                speed = speed,
                heading = heading,
                mode = if (speed == 0) RollMode.STOP else RollMode.GO,
                reverse = ReverseFlag.OFF,
            ))
            true
        }.getOrElse { error ->
            Log.w(TAG, "Drive command failed", error)
            false
        }
    }

    suspend fun calibrateHeading(heading: Int): Boolean {
        if (commandCharacteristic == null || gatt == null) return false
        if (_connectionState.value !is ConnectionState.Connected) return false

        return runCatching {
            sendPacket(SpheroCommands.calibrateHeading(packetBuilder, heading))
            true
        }.getOrElse { error ->
            Log.w(TAG, "Calibrate heading failed", error)
            false
        }
    }

    suspend fun resetHeading(): Boolean {
        if (commandCharacteristic == null || gatt == null) return false
        if (_connectionState.value !is ConnectionState.Connected) return false

        return runCatching {
            sendPacket(SpheroCommands.setStabilization(packetBuilder, false))
            sendPacket(SpheroCommands.setHeading(packetBuilder, 0))
            sendPacket(SpheroCommands.setStabilization(packetBuilder, true))
            true
        }.getOrElse { error ->
            Log.w(TAG, "Reset heading failed", error)
            false
        }
    }

    private suspend fun sendPacket(packet: SpheroPacket) {
        writeMutex.withLock {
            sendPacketLocked(packet)
        }
    }

    private suspend fun sendPacketWithResponse(packet: SpheroPacket): SpheroResponse? {
        return writeMutex.withLock {
            val responseDeferred = CompletableDeferred<SpheroResponse>()
            pendingResponse.set(responseDeferred)
            try {
                sendPacketLocked(packet)
                withTimeout(RESPONSE_TIMEOUT_MS) {
                    val response = responseDeferred.await()
                    if (response.resultCode != 0) {
                        Log.w(TAG, "Command response error code ${response.resultCode}")
                    }
                    response
                }
            } catch (e: Exception) {
                Log.w(TAG, "Command response timeout or failure", e)
                null
            } finally {
                pendingResponse.set(null)
            }
        }
    }

    private suspend fun sendPacketLocked(packet: SpheroPacket) {
        val characteristic = commandCharacteristic ?: return
        val gattClient = gatt ?: return

        for (chunk in packetBuilder.chunks(packet)) {
            writeBytes(gattClient, characteristic, chunk)
            delay(SpheroUuids.WRITE_INTERVAL_MS)
        }
    }

    private suspend fun runHandshake(gatt: BluetoothGatt) {
        _connectionState.value = ConnectionState.Handshaking

        try {
            writeMutex.withLock {
                val antiDos = findCharacteristic(gatt, SpheroUuids.ANTI_DOS)
                    ?: throw IllegalStateException("Anti-DOS characteristic not found")
                val txPower = findCharacteristic(gatt, SpheroUuids.TX_POWER)
                    ?: throw IllegalStateException("TX power characteristic not found")
                val response = findCharacteristic(gatt, SpheroUuids.RESPONSE)
                    ?: throw IllegalStateException("Response characteristic not found")
                val command = findCharacteristic(gatt, SpheroUuids.COMMAND)
                    ?: throw IllegalStateException("Command characteristic not found")
                val wake = findCharacteristic(gatt, SpheroUuids.WAKE)

                writeBytes(gatt, antiDos, SpheroUuids.ANTI_DOS_PAYLOAD.toByteArray(Charsets.US_ASCII))
                writeBytes(gatt, txPower, byteArrayOf(SpheroUuids.TX_POWER_PAYLOAD))
                wake?.let { writeBytes(gatt, it, byteArrayOf(SpheroUuids.WAKE_PAYLOAD)) }
                enableNotifications(gatt, response)

                commandCharacteristic = command
                sendPacketLocked(SpheroCommands.setStabilization(packetBuilder, true))
            }

            configureSession()
            readBatteryHealth()

            connectionTimeoutJob?.cancel()
            val name = targetDeviceName ?: gatt.device.name ?: "BB-8"
            _connectionState.value = ConnectionState.Connected(name)
            startKeepalive()
            Log.d(TAG, "Handshake complete for $name")
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed", e)
            failConnection(e.message ?: "Handshake failed")
        }
    }

    private suspend fun configureSession() {
        val timeoutPacket = SpheroCommands.setInactivityTimeout(packetBuilder, INACTIVITY_TIMEOUT_SEC)
        sendPacketWithResponse(timeoutPacket)
        sendPacketWithResponse(SpheroCommands.ping(packetBuilder))
    }

    private suspend fun readBatteryHealth() {
        val response = sendPacketWithResponse(SpheroCommands.getPowerState(packetBuilder))
        _batteryReadAttempted.value = true
        if (response == null) {
            Log.w(TAG, "Battery read failed — no response from droid")
            return
        }
        Log.d(TAG, "Power state raw: ${response.data.joinToString(" ") { "%02x".format(it) }}")
        val health = BatteryHealth.fromRaw(response.data)
        if (health != null) {
            _batteryHealth.value = health
            Log.d(
                TAG,
                "Battery: ${health.voltageVolts}V ${health.powerState} cycles=${health.chargeCycles} ${health.level}",
            )
        } else {
            Log.w(TAG, "Battery read failed — unexpected payload (${response.data.size} bytes)")
        }
    }

    private fun startKeepalive() {
        stopKeepalive()
        keepaliveJob = scope.launch {
            var tick = 0
            while (isActive && _connectionState.value is ConnectionState.Connected) {
                runCatching {
                    sendPacketWithResponse(SpheroCommands.ping(packetBuilder))
                }
                if (tick % 3 == 0) {
                    readBatteryHealth()
                }
                tick++
                delay(KEEPALIVE_INTERVAL_MS)
            }
        }
    }

    private fun stopKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = null
    }

    private fun handleNotification(data: ByteArray) {
        Log.d(TAG, "Notification: ${data.joinToString(" ") { "%02x".format(it) }}")
        val responses = responseParser.append(data)
        for (response in responses) {
            Log.d(TAG, "Parsed response seq=${response.sequence} code=${response.resultCode} data=${response.data.size}b")
            pendingResponse.get()?.complete(response)
        }
    }

    private suspend fun writeBytes(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        withTimeout(WRITE_TIMEOUT_MS) {
            val deferred = CompletableDeferred<Boolean>()
            pendingWrite.set(deferred)

            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = value
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }

            if (!started) {
                pendingWrite.set(null)
                throw IllegalStateException("Failed to write ${characteristic.uuid}")
            }

            if (!deferred.await()) {
                throw IllegalStateException("Write failed for ${characteristic.uuid}")
            }
        }
    }

    private suspend fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID)
            ?: throw IllegalStateException("CCCD descriptor not found")

        withTimeout(WRITE_TIMEOUT_MS) {
            val deferred = CompletableDeferred<Boolean>()
            pendingWrite.set(deferred)

            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }

            if (!started) {
                pendingWrite.set(null)
                throw IllegalStateException("Failed to enable notifications")
            }

            if (!deferred.await()) {
                throw IllegalStateException("Notification setup failed")
            }
        }
    }

    private fun completePendingWrite(success: Boolean) {
        pendingWrite.getAndSet(null)?.complete(success)
    }

    private fun failConnection(message: String) {
        Log.e(TAG, message)
        connectionTimeoutJob?.cancel()
        stopKeepalive()
        cleanupGatt()
        _connectionState.value = ConnectionState.Error(message)
    }

    private fun findCharacteristic(gatt: BluetoothGatt, uuid: UUID): BluetoothGattCharacteristic? {
        gatt.services.forEach { service ->
            service.getCharacteristic(uuid)?.let { return it }
        }
        return null
    }

    private fun cleanupGatt() {
        gatt?.close()
        gatt = null
        commandCharacteristic = null
        targetDeviceName = null
        pendingWrite.getAndSet(null)?.cancel()
        pendingResponse.getAndSet(null)?.cancel()
        responseParser.reset()
    }

    companion object {
        private const val TAG = "Bb8BleManager"
        private const val CONNECT_TIMEOUT_MS = 20_000L
        private const val WRITE_TIMEOUT_MS = 5_000L
        private const val RESPONSE_TIMEOUT_MS = 5_000L
        private const val KEEPALIVE_INTERVAL_MS = 10_000L
        private const val INACTIVITY_TIMEOUT_SEC = 600
        private val CLIENT_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
