package com.deckpuller.ui.pull

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deckpuller.domain.model.DeckCard

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PullScreen(
    state: PullUiState,
    onIncrement: (DeckCard) -> Unit,
    onDecrement: (DeckCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PullHeader(deckName = state.deckName, pulled = state.pulled, total = state.total)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            state.groups.forEach { group ->
                stickyHeader(key = "header-${group.type}") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PullHeader(deckName: String, pulled: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(deckName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "$pulled / $total pulled",
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else pulled.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
