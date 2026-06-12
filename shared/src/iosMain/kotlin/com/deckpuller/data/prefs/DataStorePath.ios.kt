package com.deckpuller.data.prefs

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Full path to the user-prefs DataStore file in the iOS Documents directory. */
@OptIn(ExperimentalForeignApi::class)
internal fun iosDataStorePath(): String {
    val documentsUrl = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentsUrl?.URLByAppendingPathComponent("user_prefs.preferences_pb")?.path) {
        "Could not resolve the iOS Documents directory for preferences"
    }
}
