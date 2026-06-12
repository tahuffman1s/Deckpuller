package com.deckpuller.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckpuller.ui.update.UpdateViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
actual fun SettingsRoute(onBack: () -> Unit) {
    val viewModel: UpdateViewModel = koinViewModel()
    val status by viewModel.status.collectAsStateWithLifecycle()
    SettingsScreen(
        currentVersion = viewModel.currentVersion,
        status = status,
        onCheck = viewModel::checkNow,
        onUpdate = viewModel::download,
        onBack = onBack,
    )
}
