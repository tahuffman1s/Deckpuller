package com.deckpuller.ui.pull

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap

/**
 * Android decode: hardware bitmaps are disabled deliberately so the burst overlays can read the
 * pixels back through a software canvas. Uses the shared singleton loader (Ktor-backed) so it
 * hits the same cache as the on-screen [coil3.compose.AsyncImage]s.
 */
actual suspend fun loadCardBitmap(
    context: PlatformContext,
    url: String,
    targetHeightPx: Int?,
): ImageBitmap? {
    val builder = ImageRequest.Builder(context).data(url).allowHardware(false)
    if (targetHeightPx != null && targetHeightPx > 0) {
        builder.size((targetHeightPx * 0.716f).toInt().coerceAtLeast(1), targetHeightPx).scale(Scale.FIT)
    }
    val result = SingletonImageLoader.get(context).execute(builder.build())
    val image = (result as? SuccessResult)?.image ?: return null
    return image.toBitmap().asImageBitmap()
}
