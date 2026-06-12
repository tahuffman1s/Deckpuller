package com.deckpuller.ui.pull

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import coil3.toBitmap
import org.jetbrains.skia.Image as SkiaImage

/**
 * iOS decode: run the URL through the shared (Ktor-backed) singleton Coil loader so the burst
 * hits the same memory/disk cache as the on-screen [coil3.compose.AsyncImage]s — no re-download.
 * Coil returns a Skia-backed [coil3.Image]; [coil3.toBitmap] hands back a readable software
 * `org.jetbrains.skia.Bitmap`, which we wrap into a Compose [ImageBitmap] so the shatter/cascade
 * overlays can draw slices of it through their custom canvas. Returns null on any failure, which
 * the overlays treat as "skip the flourish".
 *
 * There is no `allowHardware` knob on non-Android Coil — Skia bitmaps are always pixel-readable.
 */
actual suspend fun loadCardBitmap(
    context: PlatformContext,
    url: String,
    targetHeightPx: Int?,
): ImageBitmap? {
    val builder = ImageRequest.Builder(context).data(url)
    if (targetHeightPx != null && targetHeightPx > 0) {
        builder.size((targetHeightPx * 0.716f).toInt().coerceAtLeast(1), targetHeightPx).scale(Scale.FIT)
    }
    val result = SingletonImageLoader.get(context).execute(builder.build())
    val image = (result as? SuccessResult)?.image ?: return null
    return runCatching {
        SkiaImage.makeFromBitmap(image.toBitmap()).toComposeImageBitmap()
    }.getOrNull()
}
