package com.deckpuller.ui.pull

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale

/**
 * Load [url] as a software [ImageBitmap] (returns null on any failure). Hardware bitmaps
 * are disabled deliberately: the card-burst overlays re-draw slices of the result through a
 * custom canvas with per-piece transforms, which needs a readable software bitmap. Coil's
 * memory/disk cache means this is cheap when the same art was already shown elsewhere.
 *
 * Pass [targetHeightPx] to have Coil decode a downscaled bitmap (width follows the card's
 * aspect ratio). The deck-complete cascade loads *every* card at once, so decoding at the
 * tiny size it actually renders keeps total memory in check even for a full deck.
 */
suspend fun Context.loadCardBitmap(url: String, targetHeightPx: Int? = null): ImageBitmap? {
    val builder = ImageRequest.Builder(this).data(url).allowHardware(false)
    if (targetHeightPx != null && targetHeightPx > 0) {
        builder.size((targetHeightPx * 0.716f).toInt().coerceAtLeast(1), targetHeightPx).scale(Scale.FIT)
    }
    val result = imageLoader.execute(builder.build())
    return (result.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
}
