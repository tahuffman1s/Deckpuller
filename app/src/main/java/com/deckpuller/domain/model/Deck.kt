package com.deckpuller.domain.model

data class Deck(
    val name: String,
    val cards: List<DeckCard>,
)

data class DeckCard(
    val id: Long,
    val scryfallId: String,
    val name: String,
    val typeLine: String,
    val imageUrl: String?,
    val requiredQty: Int,
    val pulledQty: Int,
    val category: String = "",
    // Transient ownership, populated in the ViewModel from the imported collection
    // (NOT persisted in Room). Defaults keep existing mappers/tests unchanged.
    val ownedQty: Int = 0,
    val ownedPrintings: List<OwnedPrinting> = emptyList(),
) {
    val isComplete: Boolean get() = pulledQty >= requiredQty
    val isOwned: Boolean get() = ownedQty >= requiredQty
}
