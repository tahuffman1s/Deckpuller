package com.deckpuller.platform

/**
 * Whether the OS shows its own "copied" confirmation when text is placed on the clipboard.
 * Android 13+ pops a system clipboard toast, so DeckPuller stays quiet there to avoid a double
 * confirmation; iOS shows nothing, so the app must surface its own snackbar after a copy.
 */
expect fun platformShowsClipboardConfirmation(): Boolean
