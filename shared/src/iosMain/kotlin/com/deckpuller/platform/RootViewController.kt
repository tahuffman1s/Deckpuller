package com.deckpuller.platform

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/** The frontmost presented view controller, for presenting pickers / share sheets from Compose. */
@Suppress("DEPRECATION") // keyWindow is the simplest single-scene lookup; fine for this app
internal fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}
