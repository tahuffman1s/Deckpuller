package com.deckpuller.ui.shopping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.CardName
import com.deckpuller.domain.StoreCartLinks
import com.deckpuller.domain.model.DeckCard
import com.deckpuller.domain.model.OwnedInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingItem(
    val name: String,
    val scryfallId: String,
    val need: Int,
    val unitPrice: Double?,
) {
    val lineTotal: Double get() = (unitPrice ?: 0.0) * need
}

data class ShoppingUiState(
    val deckName: String = "",
    val items: List<ShoppingItem> = emptyList(),
    val totalPrice: Double = 0.0,
) {
    fun buyItems(): List<StoreCartLinks.BuyItem> =
        items.map { StoreCartLinks.BuyItem(it.name, it.need) }
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val collectionRepository: CollectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle["deckId"])
    private val prices = MutableStateFlow<Map<String, Double?>>(emptyMap())

    val state: StateFlow<ShoppingUiState?> =
        combine(
            deckRepository.observeDeck(deckId),
            collectionRepository.observeOwnedByName(),
            prices,
        ) { deck, owned, priceMap ->
            deck?.let {
                val missing = it.cards.mapNotNull { card ->
                    val need = needFor(card, owned)
                    if (need <= 0) null
                    else ShoppingItem(
                        name = card.name,
                        scryfallId = card.scryfallId,
                        need = need,
                        unitPrice = priceMap[card.scryfallId],
                    )
                }.sortedBy { item -> item.name.lowercase() }
                ShoppingUiState(
                    deckName = it.name,
                    items = missing,
                    totalPrice = missing.sumOf { item -> item.lineTotal },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { loadPrices() }
    }

    /** Number still needed for [card] given the owned map (0 if fully owned). */
    private fun needFor(card: DeckCard, owned: Map<String, OwnedInfo>): Int =
        (card.requiredQty - (owned[CardName.normalize(card.name)]?.totalQty ?: 0)).coerceAtLeast(0)

    /** Fetch Scryfall prices for the MISSING cards' scryfallIds. */
    private suspend fun loadPrices() {
        val deck = deckRepository.observeDeck(deckId).first() ?: return
        val owned = collectionRepository.observeOwnedByName().first()
        val missingIds = deck.cards
            .filter { card -> needFor(card, owned) > 0 }
            .map { it.scryfallId }
            .filter { it.isNotBlank() }
        prices.value = deckRepository.prices(missingIds)
    }
}
