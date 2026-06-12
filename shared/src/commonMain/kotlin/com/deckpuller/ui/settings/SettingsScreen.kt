package com.deckpuller.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deckpuller.data.update.UpdateInfo
import com.deckpuller.ui.update.UpdateStatus

private const val GITHUB_URL = "https://github.com/tahuffman1s/Deckpuller"

/**
 * Settings entry point. Android wires the self-update [UpdateViewModel]; iOS supplies a null
 * status so the Updates card is hidden (app-managed installs aren't permitted there).
 */
@Composable
expect fun SettingsRoute(onBack: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentVersion: String,
    status: UpdateStatus?,
    onCheck: () -> Unit,
    onUpdate: (UpdateInfo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = status is UpdateStatus.Checking || status is UpdateStatus.Downloading

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    LabeledValue(label = "App", value = "DeckPuller")
                    LabeledValue(label = "Version", value = currentVersion)
                    LabeledValue(label = "Author", value = "Travis Huffman")
                    val uriHandler = LocalUriHandler.current
                    OutlinedButton(
                        onClick = { uriHandler.openUri(GITHUB_URL) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "View on GitHub",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // The Updates card only appears on platforms that can self-install (Android). iOS
            // passes a null status and the card is omitted entirely.
            if (status != null) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Updates", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = onCheck,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Check for updates") }

                        UpdateStatusBlock(status = status, onUpdate = onUpdate)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusBlock(status: UpdateStatus, onUpdate: (UpdateInfo) -> Unit) {
    when (status) {
        UpdateStatus.Idle -> Text(
            "Updates are checked automatically on launch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        UpdateStatus.Checking -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Checking for updates…", style = MaterialTheme.typography.bodyMedium)
        }

        UpdateStatus.UpToDate -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("You're on the latest version.", style = MaterialTheme.typography.bodyMedium)
        }

        is UpdateStatus.Available -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Update available: ${status.info.versionName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (status.info.notes.isNotBlank()) {
                Text(
                    status.info.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onUpdate(status.info) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download & install") }
        }

        is UpdateStatus.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(
                progress = { status.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Downloading… ${(status.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }

        UpdateStatus.Installing -> Text(
            "Follow the system prompt to finish installing.",
            style = MaterialTheme.typography.bodyMedium,
        )

        is UpdateStatus.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                status.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
