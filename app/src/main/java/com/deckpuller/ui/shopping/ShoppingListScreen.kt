package com.deckpuller.ui.shopping

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckpuller.domain.StoreCartLinks
import com.deckpuller.ui.common.CardImageDialog
import com.deckpuller.ui.common.CardThumbnail
import com.deckpuller.ui.common.scryfallImageUrl
import kotlinx.coroutines.launch

@Composable
fun ShoppingListRoute(onBack: () -> Unit) {
    val viewModel: ShoppingListViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShoppingListScreen(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(state: ShoppingUiState?, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val items = state?.buyItems().orEmpty()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var zoomedItem by remember { mutableStateOf<ShoppingItem?>(null) }

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            clipboard.setText(AnnotatedString(StoreCartLinks.clipboardText(items)))
            scope.launch { snackbar.showSnackbar("No app to open the store — card list copied to clipboard") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Missing cards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (state == null || state.items.isEmpty()) {
                Text("Nothing to buy — you own everything in this deck (or no collection imported).")
                return@Column
            }

            Text(
                "${state.items.size} cards · ~$${"%.2f".format(state.totalPrice)} (Scryfall)",
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { open(StoreCartLinks.tcgPlayerUrl(items)) }) { Text("TCGplayer") }
                Button(onClick = {
                    // Always copy as the guaranteed fallback, then open the builder.
                    clipboard.setText(AnnotatedString(StoreCartLinks.clipboardText(items)))
                    open(StoreCartLinks.cardKingdomUrl(items))
                }) { Text("Card Kingdom") }
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(StoreCartLinks.clipboardText(items)))
                }) { Text("Copy") }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { "${it.scryfallId}|${it.name}" }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CardThumbnail(
                            imageUrl = scryfallImageUrl(item.scryfallId),
                            contentDescription = item.name,
                            onClick = { zoomedItem = item },
                        )
                        Text(
                            text = "${item.need}× ${item.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = item.unitPrice?.let { "$${"%.2f".format(it * item.need)}" } ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    zoomedItem?.let { item ->
        CardImageDialog(
            imageUrl = scryfallImageUrl(item.scryfallId, version = "normal"),
            name = item.name,
            onDismiss = { zoomedItem = null },
        )
    }
}
