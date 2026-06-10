package com.deckpuller.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.DeckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckListItem(
    val id: Long,
    val name: String,
    val pulled: Int,
    val total: Int,
)

@HiltViewModel
class DeckListViewModel @Inject constructor(
    private val repository: DeckRepository,
) : ViewModel() {

    val items: StateFlow<List<DeckListItem>> =
        repository.observeDecks()
            .map { decks ->
                decks.map { dwc ->
                    DeckListItem(
                        id = dwc.deck.id,
                        name = dwc.deck.name,
                        pulled = dwc.cards.sumOf { it.pulledQty },
                        total = dwc.cards.sumOf { it.requiredQty },
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(deckId: Long) {
        viewModelScope.launch { repository.deleteDeck(deckId) }
    }
}
