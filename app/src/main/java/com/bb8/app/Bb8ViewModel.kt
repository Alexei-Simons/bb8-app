package com.bb8.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bb8.app.ble.Bb8BleManager
import com.bb8.app.ble.BatteryHealth
import com.bb8.app.ble.ConnectionState
import com.bb8.app.ble.ScannedDevice
import com.bb8.app.data.Bb8Preferences
import com.bb8.app.data.MacroRepository
import com.bb8.app.data.MacroStepDto
import com.bb8.app.data.SavedMacro
import com.bb8.app.sphero.Bb8Animation
import com.bb8.app.sphero.MacroStep
import com.bb8.app.sphero.SensorStreamSample
import com.bb8.app.util.Haptics
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
    private val preferences = Bb8Preferences(application)
    private val macroRepository = MacroRepository(application)
    private val haptics = Haptics(application)

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val scannedDevices: StateFlow<List<ScannedDevice>> = bleManager.scannedDevices
    val batteryHealth: StateFlow<BatteryHealth?> = bleManager.batteryHealth
    val batteryReadAttempted: StateFlow<Boolean> = bleManager.batteryReadAttempted
    val sensorSample: StateFlow<SensorStreamSample> = bleManager.sensorSample
    val sensorStreamingEnabled: StateFlow<Boolean> = bleManager.sensorStreamingEnabled

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _aimOffsetDegrees = MutableStateFlow(0)
    val aimOffsetDegrees: StateFlow<Int> = _aimOffsetDegrees.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(preferences.onboardingComplete)
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    private val _autoReconnectEnabled = MutableStateFlow(preferences.autoReconnectEnabled)
    val autoReconnectEnabled: StateFlow<Boolean> = _autoReconnectEnabled.asStateFlow()

    private val _lastDevice = MutableStateFlow(preferences.lastDevice())
    val lastDevice: StateFlow<ScannedDevice?> = _lastDevice.asStateFlow()

    private val _patrolActive = MutableStateFlow(false)
    val patrolActive: StateFlow<Boolean> = _patrolActive.asStateFlow()

    private val _boostCooldown = MutableStateFlow(false)
    val boostCooldown: StateFlow<Boolean> = _boostCooldown.asStateFlow()

    private val _ledColor = MutableStateFlow(LedPreset.ORANGE)
    val ledColor: StateFlow<LedPreset> = _ledColor.asStateFlow()

    private val _savedMacros = MutableStateFlow(macroRepository.loadAll())
    val savedMacros: StateFlow<List<SavedMacro>> = _savedMacros.asStateFlow()

    private val _macroRecording = MutableStateFlow(false)
    val macroRecording: StateFlow<Boolean> = _macroRecording.asStateFlow()

    private val _macroPlaying = MutableStateFlow(false)
    val macroPlaying: StateFlow<Boolean> = _macroPlaying.asStateFlow()

    private val _recordedSteps = MutableStateFlow<List<MacroStep>>(emptyList())
    val recordedSteps: StateFlow<List<MacroStep>> = _recordedSteps.asStateFlow()

    private var driveJob: Job? = null
    private var aimJob: Job? = null
    private var patrolJob: Job? = null
    private var macroJob: Job? = null
    private var lastMacroRecordMs = 0L
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
                    is ConnectionState.Connected -> {
                        haptics.confirm()
                        _lastDevice.value = preferences.lastDevice()
                        startDriveLoop()
                    }
                    else -> {
                        driveJob?.cancel()
                        driveJob = null
                        aimJob?.cancel()
                        aimJob = null
                        stopPatrolInternal()
                        stopMacroPlaybackInternal()
                        isMoving = false
                        _aimOffsetDegrees.value = 0
                        if (state is ConnectionState.Disconnected) {
                            haptics.tick()
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            batteryHealth.collect { health ->
                if (health?.diagnosticsOnly == true && _patrolActive.value) {
                    stopPatrolInternal()
                    _statusMessage.value = "Patrol stopped: battery too weak to drive"
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

    fun completeOnboarding() {
        preferences.onboardingComplete = true
        _onboardingComplete.value = true
        haptics.confirm()
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        preferences.autoReconnectEnabled = enabled
        bleManager.setAutoReconnectEnabled(enabled)
        _autoReconnectEnabled.value = enabled
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

    fun connectLastDevice() {
        val device = _lastDevice.value ?: return
        connect(device)
    }

    fun disconnect() {
        stopPatrolInternal()
        stopMacroPlaybackInternal()
        _macroRecording.value = false
        _recordedSteps.value = emptyList()
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
        if (batteryHealth.value?.diagnosticsOnly == true) return
        if (_patrolActive.value) return
        if (_macroPlaying.value) return

        val magnitude = hypot(stickX.toDouble(), stickY.toDouble()).toFloat()
        if (magnitude < 0.08f) {
            targetSpeed = 0
            return
        }

        val stickHeading = Math.toDegrees(atan2(stickX.toDouble(), -stickY.toDouble())).roundToInt()
        targetHeading = normalizeHeading(stickHeading + _aimOffsetDegrees.value)
        targetSpeed = (magnitude * 255).roundToInt().coerceIn(20, 255)
        recordDriveStepIfNeeded()
    }

    fun stopDriving() {
        targetSpeed = 0
        if (_macroRecording.value) {
            appendRecordedStep(MacroStep.DelayMs(300))
        }
    }

    fun setLedColor(preset: LedPreset) {
        _ledColor.value = preset
        if (_macroRecording.value) {
            appendRecordedStep(MacroStep.Rgb(preset.r, preset.g, preset.b))
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = bleManager.setMainLed(preset.r, preset.g, preset.b)
            if (ok) haptics.tick()
        }
    }

    fun activateBoost() {
        if (_boostCooldown.value || batteryHealth.value?.diagnosticsOnly == true) return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = bleManager.activateBoost()
            if (ok) {
                haptics.alert()
                _boostCooldown.value = true
                delay(BOOST_COOLDOWN_MS)
                _boostCooldown.value = false
            }
        }
    }

    fun playAnimation(animation: Bb8Animation) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = bleManager.playAnimation(animation)
            if (ok) haptics.tick()
        }
    }

    fun stopAnimation() {
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.stopAnimation()
        }
    }

    fun toggleSensorStreaming() {
        viewModelScope.launch(Dispatchers.IO) {
            val enabled = !sensorStreamingEnabled.value
            val ok = bleManager.setSensorStreamingEnabled(enabled)
            if (ok) haptics.tick()
        }
    }

    fun toggleMacroRecording() {
        if (_macroRecording.value) {
            _macroRecording.value = false
            haptics.confirm()
        } else {
            _recordedSteps.value = emptyList()
            lastMacroRecordMs = 0L
            _macroRecording.value = true
            haptics.tick()
        }
    }

    fun saveRecordedMacro(name: String) {
        val steps = _recordedSteps.value
        if (steps.isEmpty()) return
        val macro = SavedMacro(
            id = macroRepository.newId(),
            name = name,
            steps = steps.map(MacroStepDto::from),
        )
        macroRepository.save(macro)
        _savedMacros.value = macroRepository.loadAll()
        _macroRecording.value = false
        _recordedSteps.value = emptyList()
        haptics.confirm()
    }

    fun addSampleSquareMacro() {
        val steps = buildList {
            for (heading in listOf(0, 90, 180, 270)) {
                add(MacroStep.Roll(100, heading))
                add(MacroStep.DelayMs(2000))
                add(MacroStep.Roll(0, heading))
                add(MacroStep.DelayMs(400))
            }
        }
        val macro = SavedMacro(
            id = macroRepository.newId(),
            name = "Square patrol",
            steps = steps.map(MacroStepDto::from),
        )
        macroRepository.save(macro)
        _savedMacros.value = macroRepository.loadAll()
        haptics.confirm()
    }

    fun playMacro(macro: SavedMacro) {
        if (batteryHealth.value?.diagnosticsOnly == true) {
            _statusMessage.value = "Battery too weak for macro playback."
            return
        }
        stopPatrolInternal()
        driveJob?.cancel()
        driveJob = null
        targetSpeed = 0
        isMoving = false
        val steps = macro.steps.mapNotNull { it.toMacroStep() }
        if (steps.isEmpty()) return
        _macroPlaying.value = true
        haptics.confirm()
        macroJob = viewModelScope.launch(Dispatchers.IO) {
            playMacroSteps(steps)
            _macroPlaying.value = false
            if (connectionState.value is ConnectionState.Connected) {
                startDriveLoop()
            }
        }
    }

    fun uploadMacroToDevice(macro: SavedMacro) {
        if (batteryHealth.value?.diagnosticsOnly == true) {
            _statusMessage.value = "Battery too weak for device macros."
            return
        }
        val steps = macro.steps.mapNotNull { it.toMacroStep() }
        if (steps.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = bleManager.uploadAndRunDeviceMacro(steps)
            if (ok) haptics.confirm() else _statusMessage.value = "Macro upload failed"
        }
    }

    fun stopMacroPlayback() {
        stopMacroPlaybackInternal()
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.abortDeviceMacro()
            bleManager.drive(0, targetHeading)
        }
        if (connectionState.value is ConnectionState.Connected) {
            startDriveLoop()
        }
    }

    fun deleteMacro(macro: SavedMacro) {
        macroRepository.delete(macro.id)
        _savedMacros.value = macroRepository.loadAll()
    }

    private suspend fun playMacroSteps(steps: List<MacroStep>) {
        for (step in steps) {
            if (!_macroPlaying.value) break
            when (step) {
                is MacroStep.Roll -> {
                    bleManager.drive(step.speed, step.heading)
                    if (step.speed == 0) delay(200)
                }
                is MacroStep.Rgb -> bleManager.setMainLed(step.r, step.g, step.b)
                is MacroStep.DelayMs -> delay(step.millis.toLong())
            }
        }
        bleManager.drive(0, targetHeading)
    }

    private fun stopMacroPlaybackInternal() {
        _macroPlaying.value = false
        macroJob?.cancel()
        macroJob = null
    }

    private fun recordDriveStepIfNeeded() {
        if (!_macroRecording.value || targetSpeed <= 0) return
        val now = System.currentTimeMillis()
        if (now - lastMacroRecordMs < MACRO_RECORD_INTERVAL_MS) return
        lastMacroRecordMs = now
        appendRecordedStep(MacroStep.Roll(targetSpeed, targetHeading))
    }

    private fun appendRecordedStep(step: MacroStep) {
        _recordedSteps.value = _recordedSteps.value + step
    }

    fun togglePatrol() {
        if (_patrolActive.value) {
            stopPatrolInternal()
            if (connectionState.value is ConnectionState.Connected) {
                startDriveLoop()
            }
        } else {
            if (batteryHealth.value?.diagnosticsOnly == true) {
                _statusMessage.value = "Battery too weak for patrol. Diagnostics only."
                return
            }
            driveJob?.cancel()
            driveJob = null
            targetSpeed = 0
            isMoving = false
            startPatrol()
        }
    }

    private fun startPatrol() {
        _patrolActive.value = true
        haptics.confirm()
        patrolJob = viewModelScope.launch(Dispatchers.IO) {
            val headings = listOf(0, 90, 180, 270)
            while (isActive && _patrolActive.value) {
                for (heading in headings) {
                    if (!_patrolActive.value) break
                    var elapsed = 0L
                    while (elapsed < PATROL_LEG_MS && isActive && _patrolActive.value) {
                        bleManager.drive(PATROL_SPEED, heading)
                        delay(DRIVE_LOOP_MS)
                        elapsed += DRIVE_LOOP_MS
                    }
                    bleManager.drive(0, heading)
                    delay(PATROL_PAUSE_MS)
                }
            }
        }
    }

    private fun stopPatrolInternal() {
        _patrolActive.value = false
        patrolJob?.cancel()
        patrolJob = null
        viewModelScope.launch(Dispatchers.IO) {
            bleManager.drive(0, targetHeading)
        }
    }

    private fun startDriveLoop() {
        if (_patrolActive.value) return
        driveJob?.cancel()
        driveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (targetSpeed > 0) {
                    val ok = bleManager.drive(targetSpeed, targetHeading)
                    if (!ok && connectionState.value !is ConnectionState.Connected) break
                    isMoving = true
                    delay(DRIVE_LOOP_MS)
                } else if (isMoving) {
                    bleManager.drive(0, targetHeading)
                    isMoving = false
                    delay(DRIVE_LOOP_MS)
                } else {
                    delay(DRIVE_LOOP_MS)
                }
            }
        }
    }

    private fun normalizeHeading(heading: Int): Int = ((heading % 360) + 360) % 360

    override fun onCleared() {
        driveJob?.cancel()
        aimJob?.cancel()
        patrolJob?.cancel()
        macroJob?.cancel()
        bleManager.disconnect()
        super.onCleared()
    }

    companion object {
        private const val AIM_DEBOUNCE_MS = 120L
        private const val DRIVE_LOOP_MS = 150L
        private const val BOOST_COOLDOWN_MS = 3_000L
        private const val PATROL_SPEED = 110
        private const val PATROL_LEG_MS = 2_000L
        private const val PATROL_PAUSE_MS = 450L
        private const val MACRO_RECORD_INTERVAL_MS = 400L
    }
}

enum class LedPreset(val label: String, val r: Int, val g: Int, val b: Int) {
    ORANGE("Orange", 245, 166, 35),
    TEAL("Teal", 0, 200, 180),
    RED("Red", 255, 40, 40),
    BLUE("Blue", 40, 120, 255),
    WHITE("White", 255, 255, 255),
    OFF("Off", 0, 0, 0),
}
