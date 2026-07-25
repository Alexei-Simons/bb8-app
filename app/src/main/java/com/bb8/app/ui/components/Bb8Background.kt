package com.bb8.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.bb8.app.ui.theme.Bb8Orange
import com.bb8.app.ui.theme.Bb8Teal
import com.bb8.app.ui.theme.SpaceBlack
import com.bb8.app.ui.theme.SpaceNavy

@Composable
fun Bb8Background(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(SpaceBlack, SpaceNavy, SpaceBlack),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Bb8Orange.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.12f),
                    radius = size.width * 0.55f,
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.85f, size.height * 0.12f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Bb8Teal.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.1f, size.height * 0.75f),
                    radius = size.width * 0.45f,
                ),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.1f, size.height * 0.75f),
            )
        }
        content()
    }
}
