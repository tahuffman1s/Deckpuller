package com.deckpuller.ui.pull

import androidx.compose.ui.graphics.ImageBitmap
import coil3.PlatformContext

/**
 * Load [url] as a software [ImageBitmap] (returns null on any failure). The card-burst overlays
 * re-draw slices of the result through a custom canvas with per-piece transforms, which needs a
 * readable software bitmap. Coil's memory/disk cache means this is cheap when the same art was
 * already shown elsewhere.
 *
 * Pass [targetHeightPx] to decode a downscaled bitmap (width follows the card's aspect ratio);
 * the deck-complete cascade loads every card at once, so decoding small keeps memory in check.
 *
 * The pixel readback is platform-specific (Android `Bitmap`, iOS Skia), hence expect/actual.
 */
expect suspend fun loadCardBitmap(
    context: PlatformContext,
    url: String,
    targetHeightPx: Int? = null,
): ImageBitmap?
