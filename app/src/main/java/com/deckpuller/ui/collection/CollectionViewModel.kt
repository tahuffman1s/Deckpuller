package com.deckpuller.ui.collection

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.CollectionImporter
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CollectionUiState(
    val cards: List<CollectionCardEntity> = emptyList(),
    val searchQuery: String = "",
    val importedAt: Long? = null,
    val totalCount: Int = 0,
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val repository: CollectionRepository,
    private val importer: CollectionImporter,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /** One-shot import feedback ("Imported 812 · 3 skipped" / error), consumed by the screen. */
    val importMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<CollectionUiState> =
        combine(
            repository.observeAll(),
            query,
            repository.importedAt,
            repository.count,
        ) { all, q, importedAt, count ->
            val filtered = if (q.isBlank()) all
            else all.filter { it.name.contains(q, ignoreCase = true) }
            CollectionUiState(
                cards = filtered,
                searchQuery = q,
                importedAt = importedAt,
                totalCount = count,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState())

    fun onSearchChange(value: String) { query.value = value }

    fun importCsv(csv: String, now: Long) {
        viewModelScope.launch {
            importMessage.value = runCatching { repository.importCsv(csv, now) }
                .fold(
                    onSuccess = { r -> "Imported ${r.imported} cards" + if (r.skipped > 0) " · ${r.skipped} skipped" else "" },
                    onFailure = { e -> "Import failed: ${e.message ?: e.javaClass.simpleName}" },
                )
        }
    }

    /** Read a picked/shared CSV Uri off the main thread, then import it. */
    fun importUri(uri: Uri, now: Long) {
        viewModelScope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { importer.readText(uri) } }
                .getOrElse { e ->
                    importMessage.value = "Import failed: ${e.message ?: e.javaClass.simpleName}"
                    return@launch
                }
            importCsv(text, now)
        }
    }

    fun clearMessage() { importMessage.value = null }
}
