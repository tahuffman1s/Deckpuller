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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val activeFilters: Set<String> = emptySet(),
    val commander: DeckCard? = null,
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
    private val filters = MutableStateFlow<Set<String>>(emptySet())
    val isRefreshing = MutableStateFlow(false)

    val state: StateFlow<PullUiState?> =
        combine(repository.observeDeck(deckId), query, filters) { deck, q, f ->
            deck?.let {
                // Category filters narrow first (a card matches if it's in ANY selected
                // category), then the name search, then alphabetise.
                val byFilter = if (f.isEmpty()) it.cards
                    else it.cards.filter { card -> subtitleOf(card) in f }
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
                    activeFilters = f,
                    // Surfaced next to the deck title; taken from the full deck so it
                    // shows even while a filter/search hides it from the list.
                    commander = it.cards.firstOrNull { card ->
                        card.category.contains("Commander", ignoreCase = true)
                    },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The commander's Scryfall colour identity, fetched once whenever the commander
     * changes. Drives the pull screen's colour theme. Empty for colourless / no commander.
     */
    val commanderColors: StateFlow<List<String>> =
        repository.observeDeck(deckId)
            .map { deck ->
                deck?.cards?.firstOrNull { it.category.contains("Commander", ignoreCase = true) }
                    ?.scryfallId
            }
            .distinctUntilChanged()
            .map { scryfallId ->
                if (scryfallId.isNullOrBlank()) emptyList()
                else repository.colorIdentity(scryfallId)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchChange(value: String) { query.value = value }

    /** Toggle a category in/out of the active filter set (multi-select). */
    fun onFilterToggle(value: String) {
        val current = filters.value
        filters.value = if (value in current) current - value else current + value
    }

    fun onClearFilters() { filters.value = emptySet() }

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
