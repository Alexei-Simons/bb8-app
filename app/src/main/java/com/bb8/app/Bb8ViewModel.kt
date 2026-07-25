package com.bb8.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bb8.app.ble.Bb8BleManager
import com.bb8.app.ble.BatteryHealth
import com.bb8.app.ble.ConnectionState
import com.bb8.app.ble.ScannedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

class Bb8ViewModel(application: Application) : AndroidViewModel(application) {
    private val bleManager = Bb8BleManager(application)

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val scannedDevices: StateFlow<List<ScannedDevice>> = bleManager.scannedDevices
    val batteryHealth: StateFlow<BatteryHealth?> = bleManager.batteryHealth
    val batteryReadAttempted: StateFlow<Boolean> = bleManager.batteryReadAttempted

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _aimOffsetDegrees = MutableStateFlow(0)
    val aimOffsetDegrees: StateFlow<Int> = _aimOffsetDegrees.asStateFlow()

    private var driveJob: Job? = null
    private var aimJob: Job? = null
    private var targetSpeed = 0
    private var targetHeading = 0
    private var isMoving = false
    private var pendingAimDegrees: Int? = null

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                _statusMessage.value = when (state) {
                    is ConnectionState.Connecting -> "Connecting to BB-8..."
                    is ConnectionState.Handshaking -> "Handshaking..."
                    is ConnectionState.Connected -> "Connected to ${state.deviceName}"
                    is ConnectionState.Error -> state.message
                    is ConnectionState.Scanning -> "Scanning for BB-8..."
                    is ConnectionState.Disconnected -> null
                }
                when (state) {
                    is ConnectionState.Connected -> startDriveLoop()
                    else -> {
                        driveJob?.cancel()
                        driveJob = null
                        aimJob?.cancel()
                        aimJob = null
                        isMoving = false
                        _aimOffsetDegrees.value = 0
                    }
                }
            }
        }
    }

    fun onPermissionsGranted() {
        if (!bleManager.isBluetoothEnabled()) {
            _statusMessage.value = "Enable Bluetooth to scan for BB-8"
        }
    }

    fun onPermissionsDenied() {
        _statusMessage.value = "Bluetooth permissions are required"
    }

    fun startScan() {
        if (!bleManager.isBluetoothEnabled()) {
            _statusMessage.value = "Enable Bluetooth first"
            return
        }
        _statusMessage.value = "Scanning for BB-8..."
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
        _statusMessage.value = null
    }

    fun connect(device: ScannedDevice) {
        _statusMessage.value = "Connecting to ${device.name}..."
        bleManager.connect(device)
    }

    fun disconnect() {
        driveJob?.cancel()
        driveJob = null
        aimJob?.cancel()
        aimJob = null
        targetSpeed = 0
        isMoving = false
        _aimOffsetDegrees.value = 0
        bleManager.disconnect()
        _statusMessage.value = null
    }

    fun setAimOffset(degrees: Int) {
        val normalized = normalizeHeading(degrees)
        if (_aimOffsetDegrees.value == normalized) return

        _aimOffsetDegrees.value = normalized
        pendingAimDegrees = normalized
        aimJob?.cancel()
        aimJob = viewModelScope.launch(Dispatchers.IO) {
            delay(AIM_DEBOUNCE_MS)
            val aim = pendingAimDegrees ?: return@launch
            bleManager.calibrateHeading(aim)
        }
    }

    fun resetAim() {
        aimJob?.cancel()
        pendingAimDegrees = null
        _aimOffsetDegrees.value = 0
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.resetHeading()
        }
    }

    fun drive(stickX: Float, stickY: Float) {
        val magnitude = hypot(stickX.toDouble(), stickY.toDouble()).toFloat()
        if (magnitude < 0.08f) {
            targetSpeed = 0
            return
        }

        val stickHeading = Math.toDegrees(atan2(stickX.toDouble(), -stickY.toDouble())).roundToInt()
        targetHeading = normalizeHeading(stickHeading + _aimOffsetDegrees.value)
        targetSpeed = (magnitude * 255).roundToInt().coerceIn(20, 255)
    }

    fun stopDriving() {
        targetSpeed = 0
    }

    private fun startDriveLoop() {
        driveJob?.cancel()
        driveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (targetSpeed > 0) {
                    val ok = bleManager.drive(targetSpeed, targetHeading)
                    if (!ok && connectionState.value !is ConnectionState.Connected) break
                    isMoving = true
                    delay(200)
                } else if (isMoving) {
                    bleManager.drive(0, targetHeading)
                    isMoving = false
                    delay(200)
                } else {
                    delay(200)
                }
            }
        }
    }

    private fun normalizeHeading(heading: Int): Int = ((heading % 360) + 360) % 360

    override fun onCleared() {
        driveJob?.cancel()
        aimJob?.cancel()
        bleManager.disconnect()
        super.onCleared()
    }

    companion object {
        private const val AIM_DEBOUNCE_MS = 120L
    }
}
