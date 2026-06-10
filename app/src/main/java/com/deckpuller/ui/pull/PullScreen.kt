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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.deckpuller.domain.model.DeckCard

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PullScreen(
    state: PullUiState,
    onIncrement: (DeckCard) -> Unit,
    onDecrement: (DeckCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoomedCard by remember { mutableStateOf<DeckCard?>(null) }

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
                        onImageClick = { zoomedCard = it },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    zoomedCard?.let { card ->
        CardImageDialog(card = card, onDismiss = { zoomedCard = null })
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
            )
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
