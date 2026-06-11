package com.deckpuller.ui.collection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.ui.common.CardImageDialog
import com.deckpuller.ui.common.CardThumbnail
import com.deckpuller.ui.common.scryfallImageUrl
import java.text.DateFormat
import java.util.Date

@Composable
fun CollectionRoute(onBack: () -> Unit) {
    val viewModel: CollectionViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.importMessage.collectAsStateWithLifecycle()
    CollectionScreen(
        state = state,
        importMessage = message,
        onSearchChange = viewModel::onSearchChange,
        onImportUri = { uri -> viewModel.importUri(uri, System.currentTimeMillis()) },
        onMessageShown = viewModel::clearMessage,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    state: CollectionUiState,
    importMessage: String?,
    onSearchChange: (String) -> Unit,
    onImportUri: (android.net.Uri) -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var zoomedCard by remember { mutableStateOf<CollectionCardEntity?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImportUri(uri) }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Collection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Button(
                onClick = {
                    // Accept any text-ish mime; ManaBox exports vary (text/csv, text/comma-separated-values).
                    picker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "*/*"))
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) { Text("Import ManaBox CSV") }

            if (state.totalCount > 0) {
                val when_ = state.importedAt?.let {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                } ?: "—"
                Text("${state.totalCount} cards · imported $when_")
            }

            if (state.totalCount == 0) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No collection imported yet. Export a CSV from ManaBox and import it here.")
                }
            } else {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Search collection") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.cards, key = { it.id }) { card ->
                        val foil = if (card.finish != "normal") " · ${card.finish}" else ""
                        CollectionCardRow(
                            name = card.name,
                            subtitle = "${card.quantity}× · ${card.setCode}$foil",
                            imageUrl = scryfallImageUrl(card.scryfallId),
                            onImageClick = { zoomedCard = card },
                        )
                    }
                }
            }
        }
    }

    zoomedCard?.let { card ->
        CardImageDialog(
            imageUrl = scryfallImageUrl(card.scryfallId, version = "normal"),
            name = card.name,
            onDismiss = { zoomedCard = null },
        )
    }
}

/** Collection row styled like the pull screen's card rows: thumbnail + name + details. */
@Composable
private fun CollectionCardRow(
    name: String,
    subtitle: String,
    imageUrl: String?,
    onImageClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardThumbnail(
            imageUrl = imageUrl,
            contentDescription = name,
            onClick = onImageClick,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
