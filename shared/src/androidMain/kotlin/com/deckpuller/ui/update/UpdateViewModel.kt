package com.deckpuller.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.update.UpdateInfo
import com.deckpuller.data.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class Downloading(val progress: Float) : UpdateStatus
    data object Installing : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

class UpdateViewModel(
    private val updateManager: UpdateManager,
) : ViewModel() {

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private var alreadyChecked = false

    /** The installed app version (e.g. "1.0.0"), for display in Settings. */
    val currentVersion: String get() = updateManager.currentVersionName

    /** Checks once per process; stays silent unless a newer release exists. */
    fun checkOnce() {
        if (alreadyChecked) return
        alreadyChecked = true
        viewModelScope.launch {
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { info ->
                    if (info != null) _status.value = UpdateStatus.Available(info)
                }
            // Network errors / up-to-date are intentionally silent — no nagging.
        }
    }

    /** Explicit, user-initiated check that surfaces every outcome (for Settings). */
    fun checkNow() {
        alreadyChecked = true
        viewModelScope.launch {
            _status.value = UpdateStatus.Checking
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { info ->
                    _status.value =
                        if (info != null) UpdateStatus.Available(info) else UpdateStatus.UpToDate
                }
                .onFailure {
                    _status.value = UpdateStatus.Error(
                        it.message ?: "Couldn't check for updates. Check your connection.",
                    )
                }
        }
    }

    fun download(info: UpdateInfo) {
        viewModelScope.launch {
            if (!updateManager.canInstallPackages()) {
                updateManager.openInstallPermissionSettings()
                _status.value = UpdateStatus.Error(
                    "Allow DeckPuller to install apps, then tap Update again.",
                )
                return@launch
            }
            _status.value = UpdateStatus.Downloading(0f)
            runCatching {
                val file = updateManager.downloadApk(info) { progress ->
                    _status.value = UpdateStatus.Downloading(progress)
                }
                _status.value = UpdateStatus.Installing
                updateManager.installApk(file)
            }.onFailure {
                _status.value = UpdateStatus.Error(it.message ?: "Update failed. Try again.")
            }
        }
    }

    /** Re-show the update prompt after an error (info is retried from scratch). */
    fun retry(info: UpdateInfo) {
        _status.value = UpdateStatus.Available(info)
    }

    fun dismiss() {
        _status.value = UpdateStatus.Idle
    }
}
