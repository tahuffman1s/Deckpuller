package com.deckpuller.data.image

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

/** `PlatformContext` is `android.content.Context` here, so its `cacheDir` is directly available. */
actual fun imageCacheDir(context: PlatformContext): Path =
    File(context.cacheDir, "coil_image_cache").toOkioPath()
