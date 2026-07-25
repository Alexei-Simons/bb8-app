package com.bb8.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bb8.app.LedPreset
import com.bb8.app.ble.BatteryHealth
import com.bb8.app.ble.BatteryHealthLevel
import com.bb8.app.sphero.Bb8Animation
import com.bb8.app.ui.components.ConnectionPill
import com.bb8.app.ui.components.SecondaryActionButton
import com.bb8.app.ui.theme.Bb8Orange
import com.bb8.app.ui.theme.Bb8Teal
import com.bb8.app.ui.theme.SpaceBorder
import com.bb8.app.ui.theme.SpaceElevated
import com.bb8.app.ui.theme.SpacePanel
import com.bb8.app.ui.theme.StatusCritical
import com.bb8.app.ui.theme.StatusFair
import com.bb8.app.ui.theme.StatusGood
import com.bb8.app.ui.theme.StatusPoor
import com.bb8.app.ui.theme.TextMuted
import com.bb8.app.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriveScreen(
    deviceName: String,
    statusMessage: String?,
    batteryHealth: BatteryHealth?,
    batteryReadAttempted: Boolean,
    aimOffsetDegrees: Int,
    diagnosticsOnly: Boolean,
    patrolActive: Boolean,
    boostCooldown: Boolean,
    selectedLed: LedPreset,
    onDrive: (Float, Float) -> Unit,
    onStopDriving: () -> Unit,
    onAimChange: (Int) -> Unit,
    onResetAim: () -> Unit,
    onDisconnect: () -> Unit,
    onLedColor: (LedPreset) -> Unit,
    onBoost: () -> Unit,
    onTogglePatrol: () -> Unit,
    onPlayAnimation: (Bb8Animation) -> Unit,
    onStopAnimation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stickX by remember { mutableStateOf(0f) }
    var stickY by remember { mutableStateOf(0f) }
    var batteryExpanded by remember { mutableStateOf(true) }
    var extrasExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
            ConnectionPill(label = "LINKED", isActive = true)
        }

        when {
            batteryHealth != null -> BatteryHealthCard(
                health = batteryHealth,
                expanded = batteryExpanded,
                onToggle = { batteryExpanded = !batteryExpanded },
            )
            batteryReadAttempted -> BatteryStatusUnavailableCard()
            else -> BatteryStatusLoadingCard()
        }

        if (diagnosticsOnly) {
            DiagnosticsBanner()
        }

        DriveExtrasPanel(
            expanded = extrasExpanded,
            onToggle = { extrasExpanded = !extrasExpanded },
            diagnosticsOnly = diagnosticsOnly,
            patrolActive = patrolActive,
            boostCooldown = boostCooldown,
            selectedLed = selectedLed,
            onLedColor = onLedColor,
            onBoost = onBoost,
            onTogglePatrol = onTogglePatrol,
            onPlayAnimation = onPlayAnimation,
            onStopAnimation = onStopAnimation,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = SpacePanel.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, SpaceBorder),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = if (diagnosticsOnly) {
                        "Diagnostics mode: aim ring only. Driving disabled."
                    } else {
                        "Rotate outer ring to aim · Drag center to drive"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
                Text(
                    text = "Aim: ${aimOffsetDegrees}°",
                    style = MaterialTheme.typography.labelMedium,
                    color = Bb8Orange,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Joystick(
                stickX = stickX,
                stickY = stickY,
                aimDegrees = aimOffsetDegrees,
                driveEnabled = !diagnosticsOnly && !patrolActive,
                onStickChange = { x, y ->
                    stickX = x
                    stickY = y
                    onDrive(x, y)
                },
                onStickRelease = {
                    stickX = 0f
                    stickY = 0f
                    onStopDriving()
                },
                onAimChange = onAimChange,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryActionButton(
                text = "Reset aim",
                onClick = onResetAim,
                modifier = Modifier.weight(1f),
            )
            SecondaryActionButton(
                text = "Disconnect",
                onClick = onDisconnect,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DiagnosticsBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = StatusCritical.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, StatusCritical.copy(alpha = 0.35f)),
    ) {
        Text(
            text = "Battery critically low. Charger-only diagnostics: LED and animations may work, driving is disabled.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = StatusCritical,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriveExtrasPanel(
    expanded: Boolean,
    onToggle: () -> Unit,
    diagnosticsOnly: Boolean,
    patrolActive: Boolean,
    boostCooldown: Boolean,
    selectedLed: LedPreset,
    onLedColor: (LedPreset) -> Unit,
    onBoost: () -> Unit,
    onTogglePatrol: () -> Unit,
    onPlayAnimation: (Bb8Animation) -> Unit,
    onStopAnimation: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SpacePanel.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, SpaceBorder),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LED · Boost · Patrol · Animations",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = TextMuted,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "LED color", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LedPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = selectedLed == preset,
                                onClick = { onLedColor(preset) },
                                label = { Text(preset.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Bb8Orange.copy(alpha = 0.25f),
                                ),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryActionButton(
                            text = if (boostCooldown) "Boost…" else "Boost",
                            onClick = onBoost,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryActionButton(
                            text = if (patrolActive) "Stop patrol" else "Patrol",
                            onClick = onTogglePatrol,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (diagnosticsOnly) {
                        Text(
                            text = "Boost and patrol need a healthier battery.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                    Text(text = "Animations", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Bb8Animation.entries.forEach { animation ->
                            FilterChip(
                                selected = false,
                                onClick = { onPlayAnimation(animation) },
                                label = { Text(animation.label) },
                            )
                        }
                        FilterChip(
                            selected = false,
                            onClick = onStopAnimation,
                            label = { Text("Stop") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryStatusLoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SpaceElevated,
        border = BorderStroke(1.dp, SpaceBorder),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Bb8Teal,
            )
            Text(text = "Reading battery from droid…", color = TextSecondary)
        }
    }
}

@Composable
private fun BatteryStatusUnavailableCard() {
    BatterySurface(level = BatteryHealthLevel.POOR) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BatteryHeader(
                level = BatteryHealthLevel.POOR,
                title = "Battery data unavailable",
                expanded = true,
                onToggle = null,
            )
            Text(
                text = "The droid connected but did not report power state. " +
                    "A warm housing and instant shutdown usually mean a dead LiPo.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun BatteryHealthCard(
    health: BatteryHealth,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    BatterySurface(level = health.level) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BatteryHeader(
                level = health.level,
                title = health.summary,
                subtitle = "${"%.2f".format(health.voltageVolts)}V · ${health.powerState.name}",
                expanded = expanded,
                onToggle = onToggle,
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Text(
                    text = health.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun BatterySurface(
    level: BatteryHealthLevel,
    content: @Composable () -> Unit,
) {
    val accent = accentForLevel(level)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun BatteryHeader(
    level: BatteryHealthLevel,
    title: String,
    subtitle: String? = null,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
) {
    val accent = accentForLevel(level)
    val icon = when (level) {
        BatteryHealthLevel.GOOD, BatteryHealthLevel.FAIR -> Icons.Default.BatteryFull
        BatteryHealthLevel.POOR, BatteryHealthLevel.CRITICAL -> Icons.Default.BatteryAlert
        BatteryHealthLevel.UNKNOWN -> Icons.Default.BatteryChargingFull
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                )
            }
        }
        if (onToggle != null) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextMuted,
                )
            }
        }
    }
}

private fun accentForLevel(level: BatteryHealthLevel): Color = when (level) {
    BatteryHealthLevel.GOOD -> StatusGood
    BatteryHealthLevel.FAIR -> StatusFair
    BatteryHealthLevel.POOR -> StatusPoor
    BatteryHealthLevel.CRITICAL -> StatusCritical
    BatteryHealthLevel.UNKNOWN -> TextMuted
}

@Composable
private fun Joystick(
    stickX: Float,
    stickY: Float,
    aimDegrees: Int,
    driveEnabled: Boolean,
    onStickChange: (Float, Float) -> Unit,
    onStickRelease: () -> Unit,
    onAimChange: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val center = Offset(width / 2f, height / 2f)
                    val maxRadius = min(width, height) / 2f
                    val distance = (down.position - center).getDistance()
                    val normalizedDistance = if (maxRadius > 0f) distance / maxRadius else 0f
                    val isAimDrag = normalizedDistance in 0.50f..0.95f

                    if (isAimDrag) {
                        onAimChange(offsetToHeading(down.position, center))
                        drag(down.id) { change ->
                            val delta = change.positionChange()
                            if (delta != Offset.Zero) {
                                change.consume()
                                onAimChange(offsetToHeading(change.position, center))
                            }
                        }
                    } else if (normalizedDistance < 0.48f && driveEnabled) {
                        val (startX, startY) = offsetToStick(down.position, width, height)
                        onStickChange(startX, startY)
                        drag(down.id) { change ->
                            val delta = change.positionChange()
                            if (delta != Offset.Zero) {
                                change.consume()
                                val (x, y) = offsetToStick(change.position, width, height)
                                onStickChange(x, y)
                            }
                        }
                        onStickRelease()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val aimRadians = Math.toRadians(aimDegrees.toDouble())

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF252545), Color(0xFF141428)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )

            drawCircle(
                color = Bb8Teal.copy(alpha = 0.12f),
                radius = radius * 0.95f,
                center = center,
                style = Stroke(width = 14.dp.toPx()),
            )
            drawCircle(
                color = SpaceBorder,
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )

            for (i in 0 until 12) {
                val angle = i * PI / 6
                val inner = radius * 0.84f
                val outer = radius * 0.96f
                val start = Offset(
                    center.x + cos(angle).toFloat() * inner,
                    center.y + sin(angle).toFloat() * inner,
                )
                val end = Offset(
                    center.x + cos(angle).toFloat() * outer,
                    center.y + sin(angle).toFloat() * outer,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.1f),
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val aimRadius = radius * 0.88f
            val aimTip = Offset(
                center.x + sin(aimRadians).toFloat() * aimRadius,
                center.y - cos(aimRadians).toFloat() * aimRadius,
            )
            val aimBaseLeft = Offset(
                center.x + sin(aimRadians - 0.22).toFloat() * radius * 0.62f,
                center.y - cos(aimRadians - 0.22).toFloat() * radius * 0.62f,
            )
            val aimBaseRight = Offset(
                center.x + sin(aimRadians + 0.22).toFloat() * radius * 0.62f,
                center.y - cos(aimRadians + 0.22).toFloat() * radius * 0.62f,
            )
            drawLine(
                color = Bb8Teal.copy(alpha = 0.35f),
                start = center,
                end = aimTip,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Bb8Teal,
                radius = 10.dp.toPx(),
                center = aimTip,
            )
            drawLine(color = Bb8Teal, start = aimBaseLeft, end = aimTip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color = Bb8Teal, start = aimBaseRight, end = aimTip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)

            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = radius * 0.38f,
                center = center,
            )
            drawCircle(
                color = Bb8Teal.copy(alpha = 0.2f),
                radius = radius * 0.06f,
                center = center,
            )

            val knobRadius = radius * 0.18f
            val driveRadius = radius * 0.42f
            val knobCenter = Offset(
                center.x + stickX * (driveRadius - knobRadius),
                center.y + stickY * (driveRadius - knobRadius),
            )
            drawCircle(
                color = Bb8Orange.copy(alpha = 0.25f),
                radius = knobRadius + 6.dp.toPx(),
                center = knobCenter,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFC04D), Bb8Orange),
                    center = knobCenter,
                    radius = knobRadius,
                ),
                radius = knobRadius,
                center = knobCenter,
            )
        }
    }
}

private fun offsetToHeading(position: Offset, center: Offset): Int {
    val dx = position.x - center.x
    val dy = position.y - center.y
    return Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).roundToInt().let {
        ((it % 360) + 360) % 360
    }
}

private fun offsetToStick(offset: Offset, width: Float, height: Float): Pair<Float, Float> {
    val centerX = width / 2f
    val centerY = height / 2f
    val maxRadius = min(width, height) / 2f * 0.38f

    var dx = offset.x - centerX
    var dy = offset.y - centerY
    val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

    if (distance > maxRadius && distance > 0f) {
        val scale = maxRadius / distance
        dx *= scale
        dy *= scale
    }

    val normalizedX = if (maxRadius > 0f) dx / maxRadius else 0f
    val normalizedY = if (maxRadius > 0f) dy / maxRadius else 0f
    return normalizedX to normalizedY
}
