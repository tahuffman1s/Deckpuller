package com.deckpuller.platform

import androidx.compose.runtime.Composable

/**
 * Remembers a function that opens [url] in the platform browser / handler. Returns `true` if a
 * handler was launched, `false` if none could be found (e.g. no browser installed) so the caller
 * can fall back — DeckPuller copies the card list to the clipboard instead.
 */
@Composable
expect fun rememberUrlOpener(): (url: String) -> Boolean
