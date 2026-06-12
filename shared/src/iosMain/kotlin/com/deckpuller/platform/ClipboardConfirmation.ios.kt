package com.deckpuller.platform

/** iOS shows no system clipboard confirmation, so the app surfaces its own snackbar. */
actual fun platformShowsClipboardConfirmation(): Boolean = false
