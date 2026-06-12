package com.deckpuller.data.image

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** iOS app Caches directory (purgeable by the OS), with a Coil-specific subfolder. */
actual fun imageCacheDir(context: PlatformContext): Path {
    val caches = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true,
    ).first() as String
    return "$caches/coil_image_cache".toPath()
}
