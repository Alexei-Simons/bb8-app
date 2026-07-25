package com.bb8.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bb8.app.ble.ConnectionState
import com.bb8.app.ui.components.Bb8Background
import com.bb8.app.ui.screens.DriveScreen
import com.bb8.app.ui.screens.OnboardingScreen
import com.bb8.app.ui.screens.ScanScreen
import com.bb8.app.ui.theme.Bb8Theme

@Composable
fun Bb8App(viewModel: Bb8ViewModel = viewModel()) {
    val onboardingComplete by viewModel.onboardingComplete.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val batteryHealth by viewModel.batteryHealth.collectAsState()
    val batteryReadAttempted by viewModel.batteryReadAttempted.collectAsState()
    val aimOffsetDegrees by viewModel.aimOffsetDegrees.collectAsState()
    val autoReconnectEnabled by viewModel.autoReconnectEnabled.collectAsState()
    val lastDevice by viewModel.lastDevice.collectAsState()
    val patrolActive by viewModel.patrolActive.collectAsState()
    val boostCooldown by viewModel.boostCooldown.collectAsState()
    val ledColor by viewModel.ledColor.collectAsState()

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            viewModel.onPermissionsGranted()
        } else {
            viewModel.onPermissionsDenied()
        }
    }

    LaunchedEffect(Unit) {
        if (permissions.isEmpty()) {
            viewModel.onPermissionsGranted()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    Bb8Theme {
        Bb8Background(modifier = Modifier.fillMaxSize()) {
            when {
                !onboardingComplete -> OnboardingScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    onComplete = viewModel::completeOnboarding,
                )
                connectionState is ConnectionState.Connected -> DriveScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    deviceName = (connectionState as ConnectionState.Connected).deviceName,
                    statusMessage = statusMessage,
                    batteryHealth = batteryHealth,
                    batteryReadAttempted = batteryReadAttempted,
                    aimOffsetDegrees = aimOffsetDegrees,
                    diagnosticsOnly = batteryHealth?.diagnosticsOnly == true,
                    patrolActive = patrolActive,
                    boostCooldown = boostCooldown,
                    selectedLed = ledColor,
                    onDrive = viewModel::drive,
                    onStopDriving = viewModel::stopDriving,
                    onAimChange = viewModel::setAimOffset,
                    onResetAim = viewModel::resetAim,
                    onDisconnect = viewModel::disconnect,
                    onLedColor = viewModel::setLedColor,
                    onBoost = viewModel::activateBoost,
                    onTogglePatrol = viewModel::togglePatrol,
                    onPlayAnimation = viewModel::playAnimation,
                    onStopAnimation = viewModel::stopAnimation,
                )
                else -> ScanScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    connectionState = connectionState,
                    scannedDevices = scannedDevices,
                    statusMessage = statusMessage,
                    lastDevice = lastDevice,
                    autoReconnectEnabled = autoReconnectEnabled,
                    onStartScan = viewModel::startScan,
                    onStopScan = viewModel::stopScan,
                    onConnect = viewModel::connect,
                    onConnectLastDevice = viewModel::connectLastDevice,
                    onAutoReconnectChange = viewModel::setAutoReconnectEnabled,
                )
            }
        }
    }
}
