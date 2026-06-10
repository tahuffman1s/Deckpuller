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
) {
    val isComplete: Boolean get() = pulledQty >= requiredQty
}
