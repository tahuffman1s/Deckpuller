package com.deckpuller.ui.pull

import androidx.compose.ui.graphics.ImageBitmap
import coil3.PlatformContext

/**
 * iOS bitmap decode. Returning null degrades gracefully: the shatter/cascade burst overlays
 * call onFinished immediately, so a completed card simply skips the particle flourish while
 * everything else works.
 *
 * TODO(ios): decode a readable software [ImageBitmap] (fetch bytes via Coil/Ktor, decode with
 * Skia → `asComposeImageBitmap()`) so the burst animations run on iOS too. Verify on-device.
 */
actual suspend fun loadCardBitmap(
    context: PlatformContext,
    url: String,
    targetHeightPx: Int?,
): ImageBitmap? = null
