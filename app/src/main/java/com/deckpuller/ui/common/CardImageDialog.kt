package com.deckpuller.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Standard Magic card aspect ratio (63mm × 88mm). */
private const val CARD_RATIO = 0.716f

/** Max pitch (degrees) the card tilts when dragged vertically; it springs back to flat. */
private const val MAX_PITCH_DEGREES = 24f

/** Degrees of yaw per full-width horizontal drag — enough to comfortably flip past 180°. */
private const val YAW_PER_WIDTH = 220f

/**
 * Full-screen card viewer, shared by every screen that zooms a card (pull, collection,
 * shopping). The card is a real 3D object: drag horizontally to spin it around and see its
 * back (it snaps to whichever face you let go nearest), drag vertically to tilt it (that
 * springs back to flat). When [isFoil] is set the front catches a holographic sheen that
 * slides with the motion; the back never does. With a [scryfallId] the back is the card's
 * true reverse for double-faced cards, otherwise the standard Magic card back. Tapping
 * outside dismisses.
 */
@Composable
fun CardImageDialog(
    imageUrl: String?,
    name: String,
    onDismiss: () -> Unit,
    scryfallId: String? = null,
    isFoil: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val scope = rememberCoroutineScope()
        // Drag writes these directly (synchronous and lossless — the canonical Compose drag
        // pattern); release animates them home. Driving an Animatable via a launched snapTo
        // per drag delta drops deltas under rapid input, so yaw never accumulates enough to
        // flip past 90°.
        var yaw by remember { mutableFloatStateOf(0f) }
        var pitch by remember { mutableFloatStateOf(0f) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        val springBack = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        )
        val snapFace = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

        // Which face points at us: the back is showing while yaw sits in the rear half-turn.
        val norm = ((yaw % 360f) + 360f) % 360f
        val showBack = norm > 90f && norm < 270f

        val idleTransition = rememberInfiniteTransition(label = "foil-idle")
        val idle by idleTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "foil-idle-sweep",
        )
        // Idle drift plus a slide that tracks how far the card is turned/tilted.
        val sweep = idle + sin(yaw * PI.toFloat() / 180f) * 0.6f -
            (pitch / MAX_PITCH_DEGREES) * 0.4f

        // Back face: the real reverse of a double-faced card, falling back to the standard
        // Magic back when that 404s (single-faced cards have no real back).
        val genericBack = remember { scryfallGenericCardBackUrl("normal") }
        var backUrl by remember(scryfallId) {
            mutableStateOf(scryfallBackFaceUrl(scryfallId, "normal") ?: genericBack)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(CARD_RATIO)
                    // The drag lives on this stationary footprint, never on the rotated visual
                    // below. A graphicsLayer transform also foreshortens a node's touch area, so
                    // were the gesture on the spinning layer the target would collapse to a sliver
                    // as the card turns edge-on near 90° — the finger slips off mid-turn, which is
                    // why only a fast flick used to flip it. A flat, full-size target lets a slow,
                    // natural drag accumulate yaw smoothly all the way around.
                    .pointerInput(Unit) {
                        // On release, spring the tilt flat and snap yaw to the nearest face.
                        val settle: () -> Unit = {
                            settleJob = scope.launch {
                                launch { animate(pitch, 0f, animationSpec = springBack) { v, _ -> pitch = v } }
                                val target = (yaw / 180f).roundToInt() * 180f
                                animate(yaw, target, animationSpec = snapFace) { v, _ -> yaw = v }
                            }
                        }
                        detectDragGestures(
                            onDragStart = { settleJob?.cancel() },
                            onDragEnd = settle,
                            onDragCancel = settle,
                        ) { change, drag ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            yaw += drag.x / w * YAW_PER_WIDTH
                            pitch = (pitch - drag.y / h * 60f).coerceIn(-MAX_PITCH_DEGREES, MAX_PITCH_DEGREES)
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationY = yaw
                            rotationX = pitch
                            cameraDistance = 16f * density
                        },
                ) {
                    if (showBack) {
                        AsyncImage(
                            model = backUrl,
                            contentDescription = "$name (back)",
                            contentScale = ContentScale.Crop,
                            // Counter-rotate so the back reads upright (not mirrored) at 180°.
                            onError = { if (backUrl != genericBack) backUrl = genericBack },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationY = 180f }
                                .clip(RoundedCornerShape(14.dp)),
                        )
                    } else {
                        val foil = if (isFoil) {
                            Modifier.foilSheen(sweep = sweep, shape = RoundedCornerShape(14.dp), intensity = 0.9f)
                        } else {
                            Modifier
                        }
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .then(foil),
                        )
                    }
                }
            }
        }
    }
}
