package com.deckpuller.ui.shopping

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.deckpuller.R
import com.deckpuller.domain.StoreCartLinks
import com.deckpuller.ui.common.CardImageDialog
import com.deckpuller.ui.common.CardThumbnail
import com.deckpuller.ui.common.SpeedDialAction
import com.deckpuller.ui.common.SpeedDialFab
import com.deckpuller.ui.common.scryfallImageUrl
import com.deckpuller.ui.pull.AlphabetIndexedColumn
import kotlinx.coroutines.launch

@Composable
fun ShoppingListRoute(onBack: () -> Unit) {
    val viewModel: ShoppingListViewModel = koinViewModel()
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
    val listState = rememberLazyListState()

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            clipboard.setText(AnnotatedString(StoreCartLinks.clipboardText(items)))
            scope.launch { snackbar.showSnackbar("No app to open the store — card list copied to clipboard") }
        }
    }

    fun copy() {
        // Android 13+ shows its own clipboard confirmation, so don't add our own snackbar.
        clipboard.setText(AnnotatedString(StoreCartLinks.clipboardText(items)))
    }

    val hasItems = state != null && state.items.isNotEmpty()
    val storeActions = if (hasItems) listOf(
        SpeedDialAction(
            label = "TCGplayer",
            onClick = { open(StoreCartLinks.tcgPlayerUrl(items)) },
            icon = { StoreIcon(R.drawable.ic_tcgplayer, "Buy on TCGplayer") },
        ),
        SpeedDialAction(
            label = "Card Kingdom",
            // Always copy as the guaranteed fallback, then open the builder.
            onClick = {
                clipboard.setText(AnnotatedString(StoreCartLinks.clipboardText(items)))
                open(StoreCartLinks.cardKingdomUrl(items))
            },
            icon = { StoreIcon(R.drawable.ic_cardkingdom, "Buy on Card Kingdom") },
        ),
        SpeedDialAction(
            label = "Copy list",
            onClick = ::copy,
            icon = { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy card list") },
        ),
    ) else emptyList()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (hasItems) {
                SpeedDialFab(
                    actions = storeActions,
                    collapsedIcon = Icons.Filled.ShoppingCart,
                    collapsedDescription = "Buy options",
                )
            }
        },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Missing cards", style = MaterialTheme.typography.titleMedium)
                        if (state != null && state.items.isNotEmpty()) {
                            Text(
                                text = "${state.items.size} cards · ~$${"%.2f".format(state.totalPrice)} (Scryfall)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!hasItems) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Nothing to buy — you own everything in this deck (or no collection imported).")
                }
            } else {
                AlphabetIndexedColumn(
                    names = state!!.items.map { it.name },
                    listState = listState,
                    modifier = Modifier.fillMaxSize(),
                ) { listModifier ->
                    LazyColumn(
                        state = listState,
                        modifier = listModifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
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
        }
    }

    zoomedItem?.let { item ->
        CardImageDialog(
            imageUrl = scryfallImageUrl(item.scryfallId, version = "normal"),
            name = item.name,
            onDismiss = { zoomedItem = null },
            scryfallId = item.scryfallId,
        )
    }
}

/** A monochrome store mark, tinted to the speed-dial mini-FAB's content colour. */
@Composable
private fun StoreIcon(resId: Int, description: String) {
    Icon(
        painter = painterResource(resId),
        contentDescription = description,
        modifier = Modifier.size(24.dp),
    )
}
