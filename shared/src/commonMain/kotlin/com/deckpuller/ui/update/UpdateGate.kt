package com.deckpuller.ui.update

import androidx.compose.runtime.Composable

/**
 * Launch-time self-update overlay. On Android it checks GitHub for a newer release and drives the
 * download/install dialogs; on platforms that forbid app-managed installs (iOS) it is a no-op.
 */
@Composable
expect fun UpdateGate()
