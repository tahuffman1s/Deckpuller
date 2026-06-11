package com.deckpuller.ui.pull

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.CardName
import com.deckpuller.domain.model.DeckCard
import com.deckpuller.domain.model.OwnedPrinting
import com.deckpuller.ui.common.scryfallImageUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PullUiState(
    val deckName: String,
    val cards: List<DeckCard>,
    val pulled: Int,
    val total: Int,
    val searchQuery: String,
    val subtitles: List<String> = emptyList(),
    val activeFilters: Set<String> = emptySet(),
    val commander: DeckCard? = null,
    val ownedCards: Int = 0,
    val ownedTotalCards: Int = 0,
    val collectionPresent: Boolean = false,
) {
    val isComplete: Boolean get() = total > 0 && pulled == total
}

/** The label shown under a card (Archidekt category, falling back to its type line). */
internal fun subtitleOf(card: DeckCard): String = card.category.ifBlank { card.typeLine }.trim()

class PullViewModel(
    private val repository: DeckRepository,
    private val collectionRepository: CollectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle["deckId"])
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow<Set<String>>(emptySet())
    val isRefreshing = MutableStateFlow(false)

    val state: StateFlow<PullUiState?> =
        combine(
            repository.observeDeck(deckId),
            query,
            filters,
            collectionRepository.observeOwnedByName(),
        ) { deck, q, f, owned ->
            deck?.let {
                val enriched = it.cards.map { card ->
                    val info = owned[CardName.normalize(card.name)]
                    // When the card is in the collection, prefer the printing the user
                    // actually owns. With several printings, show the "common" one — the
                    // one they hold the most copies of (ties favour the non-foil).
                    val ownedImage = info?.printings
                        ?.filter { p -> !p.scryfallId.isNullOrBlank() }
                        ?.maxWithOrNull(
                            compareBy({ p: OwnedPrinting -> p.quantity }, { p -> if (p.finish == "normal") 1 else 0 }),
                        )
                        ?.let { p -> scryfallImageUrl(p.scryfallId, version = "normal") }
                    card.copy(
                        ownedQty = info?.totalQty ?: 0,
                        ownedPrintings = info?.printings ?: emptyList(),
                        imageUrl = ownedImage ?: card.imageUrl,
                    )
                }
                val byFilter = if (f.isEmpty()) enriched
                    else enriched.filter { card -> subtitleOf(card) in f }
                val filtered = if (q.isBlank()) byFilter
                    else byFilter.filter { card -> card.name.contains(q, ignoreCase = true) }
                PullUiState(
                    deckName = it.name,
                    cards = filtered.sortedBy { card -> card.name.lowercase() },
                    pulled = it.cards.sumOf { card -> card.pulledQty },
                    total = it.cards.sumOf { card -> card.requiredQty },
                    searchQuery = q,
                    subtitles = enriched.map(::subtitleOf)
                        .filter { s -> s.isNotBlank() && s != "Unknown" }
                        .distinct()
                        .sorted(),
                    activeFilters = f,
                    commander = enriched.firstOrNull { card ->
                        card.category.contains("Commander", ignoreCase = true)
                    },
                    ownedCards = enriched.count { card -> card.ownedQty >= card.requiredQty },
                    ownedTotalCards = enriched.size,
                    collectionPresent = owned.isNotEmpty(),
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
