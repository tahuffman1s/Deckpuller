package com.deckpuller.platform

import android.os.Build

/** Android 13 (Tiramisu)+ shows a system clipboard confirmation; older versions show nothing. */
actual fun platformShowsClipboardConfirmation(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
