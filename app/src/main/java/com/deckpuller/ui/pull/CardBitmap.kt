package com.deckpuller.ui.pull

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.imageLoader
import coil.request.ImageRequest

/**
 * Load [url] as a software [ImageBitmap] (returns null on any failure). Hardware bitmaps
 * are disabled deliberately: the card-burst overlays re-draw slices of the result through a
 * custom canvas with per-piece transforms, which needs a readable software bitmap. Coil's
 * memory/disk cache means this is cheap when the same art was already shown elsewhere.
 */
suspend fun Context.loadCardBitmap(url: String): ImageBitmap? {
    val result = imageLoader.execute(
        ImageRequest.Builder(this).data(url).allowHardware(false).build(),
    )
    return (result.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
}
