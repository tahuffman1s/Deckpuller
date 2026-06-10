package com.deckpuller.ui.importdeck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deckpuller.domain.model.DeckSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeckScreen(
    state: ImportUiState,
    results: List<DeckSummary>,
    savedUsername: String?,
    onImportUrl: (String) -> Unit,
    onFindMyDecks: (String) -> Unit,
    onPickDeck: (DeckSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(savedUsername) {
        if (username.isBlank() && !savedUsername.isNullOrBlank()) username = savedUsername
    }
    val isLoading = state is ImportUiState.Loading

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Add a deck") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Archidekt deck URL") },
                singleLine = true,
                isError = state is ImportUiState.Error,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onImportUrl(url) },
                enabled = !isLoading && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import") }

            HorizontalDivider()
            Text("Or browse your Archidekt decks", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Archidekt username") },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { onFindMyDecks(username) },
                enabled = !isLoading && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Find my decks") }

            if (state is ImportUiState.Error) {
                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(results, key = { it.archidektId }) { summary ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickDeck(summary) }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(summary.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("${summary.cardCount} cards", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
