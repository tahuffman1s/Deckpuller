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
) {
    val isComplete: Boolean get() = total > 0 && pulled == total
}

@HiltViewModel
class PullViewModel @Inject constructor(
    private val repository: DeckRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle["deckId"])
    private val query = MutableStateFlow("")
    val isRefreshing = MutableStateFlow(false)

    val state: StateFlow<PullUiState?> =
        combine(repository.observeDeck(deckId), query) { deck, q ->
            deck?.let {
                val filtered = if (q.isBlank()) it.cards
                    else it.cards.filter { card -> card.name.contains(q, ignoreCase = true) }
                PullUiState(
                    deckName = it.name,
                    // One flat list, alphabetical by name (case-insensitive) — no type sections.
                    cards = filtered.sortedBy { card -> card.name.lowercase() },
                    pulled = it.cards.sumOf { card -> card.pulledQty },
                    total = it.cards.sumOf { card -> card.requiredQty },
                    searchQuery = q,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onSearchChange(value: String) { query.value = value }

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
