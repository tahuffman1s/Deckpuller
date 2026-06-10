package com.deckpuller.ui.pull

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.DeckGrouping
import com.deckpuller.domain.model.CardGroup
import com.deckpuller.domain.model.DeckCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PullUiState(
    val deckName: String,
    val groups: List<CardGroup>,
    val pulled: Int,
    val total: Int,
) {
    val isComplete: Boolean get() = total > 0 && pulled == total
}

@HiltViewModel
class PullViewModel @Inject constructor(
    private val repository: DeckRepository,
) : ViewModel() {

    val state: StateFlow<PullUiState?> = repository.observeDeck()
        .map { deck ->
            deck?.let {
                PullUiState(
                    deckName = it.name,
                    groups = DeckGrouping.group(it.cards),
                    pulled = it.cards.sumOf { card -> card.pulledQty },
                    total = it.cards.sumOf { card -> card.requiredQty },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun increment(card: DeckCard) {
        if (card.pulledQty >= card.requiredQty) return
        viewModelScope.launch { repository.setPulled(card.id, card.pulledQty + 1) }
    }

    fun decrement(card: DeckCard) {
        if (card.pulledQty <= 0) return
        viewModelScope.launch { repository.setPulled(card.id, card.pulledQty - 1) }
    }

    fun clear() {
        viewModelScope.launch { repository.clearDeck() }
    }
}
