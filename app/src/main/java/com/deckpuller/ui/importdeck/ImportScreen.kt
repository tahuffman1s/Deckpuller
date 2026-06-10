package com.deckpuller.ui.importdeck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ImportScreen(
    state: ImportUiState,
    onImport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }
    val isLoading = state is ImportUiState.Loading

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Import a deck", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Paste an Archidekt deck URL to start pulling cards.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Archidekt deck URL") },
            singleLine = true,
            isError = state is ImportUiState.Error,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state is ImportUiState.Error) {
            Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .semantics { contentDescription = "Importing" },
            )
        } else {
            Button(
                onClick = { onImport(url) },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import")
            }
        }
    }
}
