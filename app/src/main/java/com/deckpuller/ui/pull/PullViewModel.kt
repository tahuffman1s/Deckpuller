package com.deckpuller.ui.pull

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.DeckCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PullUiState(
    val deckName: String,
    val cards: List<DeckCard>,
    val pulled: Int,
    val total: Int,
    val searchQuery: String,
    val subtitles: List<String> = emptyList(),
    val activeFilter: String? = null,
) {
    val isComplete: Boolean get() = total > 0 && pulled == total
}

/** The label shown under a card (Archidekt category, falling back to its type line). */
internal fun subtitleOf(card: DeckCard): String = card.category.ifBlank { card.typeLine }.trim()

@HiltViewModel
class PullViewModel @Inject constructor(
    private val repository: DeckRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle["deckId"])
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<String?>(null)
    val isRefreshing = MutableStateFlow(false)

    val state: StateFlow<PullUiState?> =
        combine(repository.observeDeck(deckId), query, filter) { deck, q, f ->
            deck?.let {
                // Subtitle filter narrows first, then the name search, then alphabetise.
                val byFilter = if (f == null) it.cards
                    else it.cards.filter { card -> subtitleOf(card) == f }
                val filtered = if (q.isBlank()) byFilter
                    else byFilter.filter { card -> card.name.contains(q, ignoreCase = true) }
                PullUiState(
                    deckName = it.name,
                    // One flat list, alphabetical by name (case-insensitive) — no type sections.
                    cards = filtered.sortedBy { card -> card.name.lowercase() },
                    pulled = it.cards.sumOf { card -> card.pulledQty },
                    total = it.cards.sumOf { card -> card.requiredQty },
                    searchQuery = q,
                    subtitles = it.cards.map(::subtitleOf)
                        .filter { s -> s.isNotBlank() && s != "Unknown" }
                        .distinct()
                        .sorted(),
                    activeFilter = f,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onSearchChange(value: String) { query.value = value }

    fun onFilterChange(value: String?) { filter.value = value }

    fun increment(card: DeckCard) {
        if (card.pulledQty >= card.requiredQty) return
        viewModelScope.launch { repository.setPulled(card.id, card.pulledQty + 1) }
    }

    fun decrement(card: DeckCard) {
        if (card.pulledQty <= 0) return
        viewModelScope.launch { repository.setPulled(card.id, card.pulledQty - 1) }
    }

    fun reset() {
        viewModelScope.launch { repository.resetProgress(deckId) }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                repository.refreshDeck(deckId)
            } finally {
                isRefreshing.value = false
            }
        }
    }
}
