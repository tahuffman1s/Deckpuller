package com.deckpuller.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Drop-in overlay: checks GitHub for a newer release on first composition and,
 * if found, drives the download/install dialogs. Renders nothing when idle.
 */
@Composable
fun UpdateGate(viewModel: UpdateViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.checkOnce() }
    val status by viewModel.status.collectAsStateWithLifecycle()

    when (val s = status) {
        is UpdateStatus.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Update available") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text("DeckPuller ${s.info.versionName} is ready to install.")
                    if (s.info.notes.isNotBlank()) {
                        Text(
                            text = s.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.download(s.info) }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) { Text("Later") }
            },
        )

        is UpdateStatus.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
        )

        UpdateStatus.Installing -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Ready to install") },
            text = { Text("Follow the system prompt to finish installing the update.") },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("OK") } },
        )

        is UpdateStatus.Error -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Update") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("OK") } },
        )

        // The launch-time gate stays silent for these; Settings shows them inline.
        UpdateStatus.Idle, UpdateStatus.Checking, UpdateStatus.UpToDate -> Unit
    }
}
