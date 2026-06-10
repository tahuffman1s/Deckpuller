package com.deckpuller.ui.pull

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.deckpuller.domain.model.DeckCard

@Composable
fun PullRoute(
    onBack: () -> Unit,
    onAddDeck: () -> Unit,
    viewModel: PullViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var celebrationDismissed by remember { mutableStateOf(false) }

    state?.let { pull ->
        PullScreen(
            state = pull,
            isRefreshing = isRefreshing,
            onIncrement = viewModel::increment,
            onDecrement = viewModel::decrement,
            onSearchChange = viewModel::onSearchChange,
            onRefresh = viewModel::refresh,
            onReset = viewModel::reset,
            onBack = onBack,
            onAddDeck = onAddDeck,
            onCelebrationFinished = { celebrationDismissed = true },
            showCelebration = pull.isComplete && !celebrationDismissed,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PullScreen(
    state: PullUiState,
    isRefreshing: Boolean,
    onIncrement: (DeckCard) -> Unit,
    onDecrement: (DeckCard) -> Unit,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onAddDeck: () -> Unit,
    onCelebrationFinished: () -> Unit,
    showCelebration: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var searching by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var zoomedCard by remember { mutableStateOf<DeckCard?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to decks")
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            placeholder = { Text("Search cards") },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search field" },
                        )
                    } else {
                        Text(state.deckName)
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = "Reset progress")
                        }
                        IconButton(onClick = onAddDeck) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Add another deck")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                PullHeader(pulled = state.pulled, total = state.total, isRefreshing = isRefreshing)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.groups.forEach { group ->
                        stickyHeader(key = "header-${group.type}") {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${group.type} (${group.cards.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }
                        items(group.cards, key = { it.id }) { card ->
                            CardRow(
                                card = card,
                                onIncrement = onIncrement,
                                onDecrement = onDecrement,
                                onImageClick = { zoomedCard = it },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            if (showCelebration) {
                CelebrationOverlay(onFinished = onCelebrationFinished)
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset progress?") },
            text = { Text("This sets every card's pulled count back to zero for this deck.") },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; onReset() }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }

    zoomedCard?.let { card ->
        CardImageDialog(card = card, onDismiss = { zoomedCard = null })
    }
}

@Composable
private fun PullHeader(pulled: Int, total: Int, isRefreshing: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("$pulled / $total pulled", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else pulled.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (isRefreshing) {
            Text("Refreshing…", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun CardImageDialog(card: DeckCard, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}
