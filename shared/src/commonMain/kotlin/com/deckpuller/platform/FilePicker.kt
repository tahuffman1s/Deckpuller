package com.deckpuller.platform

import androidx.compose.runtime.Composable

/**
 * Remembers a launcher that opens the platform document picker filtered to CSV/text and reads
 * the chosen file. [onText] is called with the file's contents, or `null` if the user cancelled
 * or the read failed. Returns the function to invoke (e.g. from a button's onClick).
 */
@Composable
expect fun rememberCsvPicker(onText: (String?) -> Unit): () -> Unit
