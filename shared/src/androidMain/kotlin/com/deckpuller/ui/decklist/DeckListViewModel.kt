package com.deckpuller.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.DeckRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeckListItem(
    val id: Long,
    val name: String,
    val pulled: Int,
    val total: Int,
    val commanderImageUrl: String? = null,
    val commanderScryfallId: String? = null,
)

class DeckListViewModel(
    private val repository: DeckRepository,
) : ViewModel() {

    val items: StateFlow<List<DeckListItem>> =
        repository.observeDecks()
            .map { decks ->
                decks.map { dwc ->
                    val commander = dwc.cards.firstOrNull {
                        it.category.contains("Commander", ignoreCase = true)
                    }
                    DeckListItem(
                        id = dwc.deck.id,
                        name = dwc.deck.name,
                        pulled = dwc.cards.sumOf { it.pulledQty },
                        total = dwc.cards.sumOf { it.requiredQty },
                        commanderImageUrl = commander?.imageUrl,
                        commanderScryfallId = commander?.scryfallId,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(deckId: Long) {
        viewModelScope.launch { repository.deleteDeck(deckId) }
    }
}
