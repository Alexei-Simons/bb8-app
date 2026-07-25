package com.bb8.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bb8.app.sphero.SensorStreamSample
import com.bb8.app.ui.theme.Bb8Teal
import com.bb8.app.ui.theme.SpaceBorder
import com.bb8.app.ui.theme.SpacePanel
import com.bb8.app.ui.theme.TextMuted
import com.bb8.app.ui.theme.TextSecondary
import kotlin.math.hypot

@Composable
fun SensorStreamPanel(
    sample: SensorStreamSample,
    streamingEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = SpacePanel.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, SpaceBorder),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (streamingEnabled) "Sensor stream (4 Hz)" else "Sensor stream off",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric(label = "X", value = sample.locatorXCm?.let { "%.0f cm".format(it) } ?: "-")
                Metric(label = "Y", value = sample.locatorYCm?.let { "%.0f cm".format(it) } ?: "-")
                Metric(
                    label = "Speed",
                    value = sample.speedCmPerSec?.let { "%.0f cm/s".format(it) }
                        ?: velocityMagnitude(sample),
                )
            }
            LocatorRadar(
                x = sample.locatorXCm,
                y = sample.locatorYCm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
            Text(
                text = "Odometry from Sphero locator (configure_locator + SET_DATA_STREAMING). BB-8 does not swap axes.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun LocatorRadar(
    x: Float?,
    y: Float?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.9f
        drawCircle(
            color = Color.White.copy(alpha = 0.06f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        if (x != null && y != null) {
            val scale = radius / 120f
            val point = Offset(center.x + x * scale, center.y - y * scale)
            drawCircle(color = Bb8Teal, radius = 8.dp.toPx(), center = point)
            drawLine(color = Bb8Teal.copy(alpha = 0.4f), start = center, end = point, strokeWidth = 2.dp.toPx())
        }
    }
}

private fun velocityMagnitude(sample: SensorStreamSample): String {
    val vx = sample.velocityXCmPerSec
    val vy = sample.velocityYCmPerSec
    if (vx == null || vy == null) return "-"
    return "%.0f cm/s".format(hypot(vx.toDouble(), vy.toDouble()))
}
