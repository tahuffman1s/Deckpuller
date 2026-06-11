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
    val commanderImageUrl: String? = null,
)

@HiltViewModel
class DeckListViewModel @Inject constructor(
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
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(deckId: Long) {
        viewModelScope.launch { repository.deleteDeck(deckId) }
    }
}
