package com.deckpuller.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * Opens [url] via UIKit. Uses `openURL:options:completionHandler:` — the deprecated synchronous
 * `openURL:` no-ops on modern iOS, which is why TCGplayer / Card Kingdom links silently failed.
 * `canOpenURL` decides the return value (the actual open is async); for `https` it is always true,
 * so a malformed/unparseable URL is the only path that reports failure and falls back to clipboard.
 */
@Composable
actual fun rememberUrlOpener(): (url: String) -> Boolean = { url ->
    val nsUrl = NSURL.URLWithString(url)
    if (nsUrl != null && UIApplication.sharedApplication.canOpenURL(nsUrl)) {
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
        true
    } else {
        false
    }
}
