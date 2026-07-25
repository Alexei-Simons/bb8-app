package com.bb8.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bb8.app.ui.components.PrimaryActionButton
import com.bb8.app.ui.components.ScreenHeader
import com.bb8.app.ui.components.SecondaryActionButton
import com.bb8.app.ui.components.TipCard
import com.bb8.app.ui.theme.Bb8Orange
import com.bb8.app.ui.theme.TextMuted

private data class OnboardingPage(
    val title: String,
    val body: String,
    val tip: String? = null,
)

private val pages = listOf(
    OnboardingPage(
        title = "Wake your droid",
        body = "Take BB-8 off the charging base and shake gently. He should appear as BB-* in a Bluetooth scan when awake.",
        tip = "A warm shell or instant shutdown often means a failing LiPo. You can still connect for battery diagnostics.",
    ),
    OnboardingPage(
        title = "Aim, then drive",
        body = "On the drive screen, drag the outer ring to aim so forward on the stick matches BB-8's head direction. Drag the center stick to roll.",
    ),
    OnboardingPage(
        title = "Extras & safety",
        body = "LED colors, boost, patrol, and animations are on the drive screen. Disconnect when finished for a cleaner reconnect next time.",
        tip = "This app is unofficial and not affiliated with Sphero or Lucasfilm.",
    ),
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = pages[pageIndex]
    val isLast = pageIndex == pages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = "Welcome",
            subtitle = "BB-8 controller · step ${pageIndex + 1} of ${pages.size}",
        )

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        page.tip?.let { TipCard(text = it) }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "●".repeat(pageIndex + 1) + "○".repeat(pages.size - pageIndex - 1),
            color = Bb8Orange,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isLast) {
            PrimaryActionButton(
                text = "Get started",
                onClick = onComplete,
            )
        } else {
            PrimaryActionButton(
                text = "Next",
                onClick = { pageIndex++ },
            )
            SecondaryActionButton(
                text = "Skip",
                onClick = onComplete,
            )
        }

        if (!isLast) {
            Text(
                text = "Unofficial · Not affiliated with Sphero",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}
