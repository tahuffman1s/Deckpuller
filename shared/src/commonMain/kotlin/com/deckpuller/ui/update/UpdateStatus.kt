package com.deckpuller.ui.update

import com.deckpuller.data.update.UpdateInfo

/** Self-update state surfaced to Settings and the launch-time update gate (Android only). */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class Downloading(val progress: Float) : UpdateStatus
    data object Installing : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}
