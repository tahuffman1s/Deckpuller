package com.deckpuller.ui.pull

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

private const val CELEBRATION_MS = 3500L

/** A festive default for colourless commanders / unknown identities. */
private val DEFAULT_CONFETTI = listOf(
    Color(0xFFFFC107), Color(0xFFE53935), Color(0xFF1E88E5),
    Color(0xFF43A047), Color(0xFF8E24AA),
)

@Composable
fun CelebrationOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = emptyList(),
) {
    LaunchedEffect(Unit) {
        delay(CELEBRATION_MS)
        onFinished()
    }

    val confettiColors = colors.ifEmpty { DEFAULT_CONFETTI }.map { it.toArgb() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
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
            }
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = listOf(
                    Party(
                        emitter = Emitter(duration = 2, TimeUnit.SECONDS).max(200),
                        colors = confettiColors,
                        position = Position.Relative(0.5, 0.3),
                    ),
                ),
            )
        }
    }
}
