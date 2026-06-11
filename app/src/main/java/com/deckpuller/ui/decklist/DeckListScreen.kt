package com.deckpuller.ui.decklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.deckpuller.ui.common.CardImageDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    decks: List<DeckListItem>,
    onDeckClick: (Long) -> Unit,
    onAddDeck: () -> Unit,
    onDeleteDeck: (Long) -> Unit,
    onSettings: () -> Unit = {},
    onCollection: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("My Decks") },
                actions = {
                    IconButton(onClick = onCollection) {
                        Icon(Icons.Default.Style, contentDescription = "Collection")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDeck) {
                Icon(Icons.Filled.Add, contentDescription = "Add deck")
            }
        },
    ) { padding ->
        if (decks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No decks yet. Tap + to add one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(decks, key = { it.id }) { deck ->
                    DeckRow(deck = deck, onClick = { onDeckClick(deck.id) }, onDelete = { onDeleteDeck(deck.id) })
                }
            }
        }
    }
}

@Composable
private fun DeckRow(deck: DeckListItem, onClick: () -> Unit, onDelete: () -> Unit) {
    var zoomed by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        val hasArt = deck.commanderImageUrl != null
        AsyncImage(
            model = deck.commanderImageUrl,
            contentDescription = "Commander",
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            modifier = Modifier
                .size(width = 48.dp, height = 64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // Tapping the commander art zooms it, just like in the deck's card list.
                .then(if (hasArt) Modifier.clickable { zoomed = true } else Modifier),
        )

        // Name + count + progress, vertically centered between art and menu.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                deck.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${deck.pulled} / ${deck.total}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (deck.total == 0) 0f else deck.pulled.toFloat() / deck.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DeckMenu(deckName = deck.name, onDelete = onDelete)
    }

    if (zoomed && deck.commanderImageUrl != null) {
        CardImageDialog(
            imageUrl = deck.commanderImageUrl,
            name = deck.name,
            onDismiss = { zoomed = false },
            scryfallId = deck.commanderScryfallId,
        )
    }
}

/**
 * Per-deck actions presented as a speed dial: tapping the kebab expands a stack of
 * labelled, icon-bearing mini buttons above it — the same visual language as the
 * pull screen's actions FAB.
 */
@Composable
private fun DeckMenu(deckName: String, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.MoreVert,
                contentDescription = "Options for $deckName",
            )
        }
        if (expanded) {
            // Anchor the speed dial just below the kebab, right-aligned to it.
            val positionProvider = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset = IntOffset(
                        x = (anchorBounds.right - popupContentSize.width).coerceAtLeast(0),
                        y = anchorBounds.bottom
                            .coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0)),
                    )
                }
            }
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(8.dp),
                ) {
                    SpeedDialAction("Delete", Icons.Filled.Delete) {
                        expanded = false
                        onDelete()
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedDialAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    // Whole row is tappable so the label pill and the mini FAB both trigger the action.
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        SmallFloatingActionButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
    }
}
