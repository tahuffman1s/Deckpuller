package com.deckpuller.ui.pull

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.vinceglb.confettikit.compose.ConfettiKit
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.Position
import io.github.vinceglb.confettikit.core.emitter.Emitter
import kotlin.time.Duration.Companion.seconds

/** A festive default for colourless commanders / unknown identities. */
private val DEFAULT_CONFETTI = listOf(
    Color(0xFFFFC107), Color(0xFFE53935), Color(0xFF1E88E5),
    Color(0xFF43A047), Color(0xFF8E24AA),
)

/**
 * Full-screen deck-complete celebration: a dark scrim, a banner, and a confetti burst. It
 * stays up until the user taps anywhere ([onFinished]) — the card cascade plays on top.
 */
@Composable
fun CelebrationOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = emptyList(),
) {
    val confettiColors = colors.ifEmpty { DEFAULT_CONFETTI }.map { it.toArgb() }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished,
            ),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Deck complete! 🎉",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Text(
                    "Tap anywhere to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            ConfettiKit(
                modifier = Modifier.fillMaxSize(),
                parties = listOf(
                    Party(
                        emitter = Emitter(duration = 2.seconds).max(200),
                        colors = confettiColors,
                        position = Position.Relative(0.5, 0.3),
                    ),
                ),
            )
        }
    }
}
