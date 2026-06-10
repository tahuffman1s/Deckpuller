package com.deckpuller.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckpuller.ui.importdeck.ImportScreen
import com.deckpuller.ui.importdeck.ImportViewModel
import com.deckpuller.ui.pull.CelebrationOverlay
import com.deckpuller.ui.pull.PullScreen
import com.deckpuller.ui.pull.PullViewModel

@Composable
fun AppRoot(
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val hasDeck by mainViewModel.hasDeck.collectAsStateWithLifecycle()

    when (hasDeck) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        false -> ImportRoute()
        true -> PullRoute()
    }
}

@Composable
private fun ImportRoute(viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImportScreen(state = state, onImport = viewModel::import)
}

@Composable
private fun PullRoute(viewModel: PullViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state?.let { pull ->
        PullScreen(
            state = pull,
            onIncrement = viewModel::increment,
            onDecrement = viewModel::decrement,
        )
        if (pull.isComplete) {
            CelebrationOverlay(onFinished = viewModel::clear)
        }
    }
}
