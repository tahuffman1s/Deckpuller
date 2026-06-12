package com.deckpuller.ui.settings

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle

@Composable
actual fun SettingsRoute(onBack: () -> Unit) {
    val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "—"
    // status = null hides the Updates card; iOS can't self-install.
    SettingsScreen(
        currentVersion = version,
        status = null,
        onCheck = {},
        onUpdate = {},
        onBack = onBack,
    )
}
