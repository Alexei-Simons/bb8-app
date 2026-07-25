package com.bb8.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bb8.app.ble.ConnectionState
import com.bb8.app.ble.ScannedDevice
import com.bb8.app.ui.components.DeviceCard
import com.bb8.app.ui.components.PrimaryActionButton
import com.bb8.app.ui.components.ScanRadar
import com.bb8.app.ui.components.ScreenHeader
import com.bb8.app.ui.components.StatusBanner
import com.bb8.app.ui.components.TipCard
import com.bb8.app.ui.theme.Bb8Orange
import com.bb8.app.ui.theme.Bb8Teal
import com.bb8.app.ui.theme.TextMuted
import com.bb8.app.ui.theme.TextSecondary

@Composable
fun ScanScreen(
    connectionState: ConnectionState,
    scannedDevices: List<ScannedDevice>,
    statusMessage: String?,
    lastDevice: ScannedDevice?,
    autoReconnectEnabled: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (ScannedDevice) -> Unit,
    onConnectLastDevice: () -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isScanning = connectionState is ConnectionState.Scanning
    val isBusy = connectionState is ConnectionState.Connecting ||
        connectionState is ConnectionState.Handshaking

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "BB-8",
            subtitle = "Modern controller for your droid",
        )

        TipCard(
            text = "Wake BB-8 off the charger (shake gently), then scan. " +
                "Keep him nearby - BLE range is short.",
        )

        lastDevice?.let { device ->
            DeviceCard(
                name = "Reconnect to ${device.name}",
                address = device.address,
                enabled = !isBusy,
                onClick = onConnectLastDevice,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Auto-reconnect",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Switch(
                checked = autoReconnectEnabled,
                onCheckedChange = onAutoReconnectChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Bb8Orange,
                    checkedTrackColor = Bb8Teal.copy(alpha = 0.4f),
                ),
            )
        }

        when {
            connectionState is ConnectionState.Error -> {
                StatusBanner(message = connectionState.message, isError = true)
            }
            isBusy -> {
                val message = when (connectionState) {
                    is ConnectionState.Connecting -> "Connecting over Bluetooth…"
                    is ConnectionState.Handshaking -> "Handshaking with droid firmware…"
                    else -> statusMessage.orEmpty()
                }
                StatusBanner(message = message)
            }
            statusMessage != null -> {
                StatusBanner(message = statusMessage)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            ScanRadar(isScanning = isScanning || isBusy)
        }

        PrimaryActionButton(
            text = when {
                isBusy -> "Connecting…"
                isScanning -> "Stop Scan"
                else -> "Scan for Droids"
            },
            onClick = if (isScanning) onStopScan else onStartScan,
            enabled = !isBusy,
            isLoading = isBusy,
        )

        Text(
            text = "NEARBY DEVICES",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (scannedDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isScanning) {
                        "Searching for BB-* devices…"
                    } else {
                        "No droids found yet.\nTap scan when BB-8 is awake."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(scannedDevices, key = { it.address }) { device ->
                    DeviceCard(
                        name = device.name,
                        address = device.address,
                        enabled = !isBusy,
                        onClick = { onConnect(device) },
                    )
                }
            }
        }

        Text(
            text = "Unofficial · Not affiliated with Sphero",
            style = MaterialTheme.typography.labelMedium,
            color = Bb8Orange.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )
    }
}
