package com.deckpuller.ui.update

import androidx.compose.runtime.Composable

/** iOS forbids app-managed installs, so the self-update gate is a no-op. */
@Composable
actual fun UpdateGate() {
    // Intentionally empty.
}
