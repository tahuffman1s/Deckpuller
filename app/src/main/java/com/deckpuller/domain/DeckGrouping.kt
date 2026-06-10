package com.deckpuller.domain

import com.deckpuller.domain.model.CardGroup
import com.deckpuller.domain.model.DeckCard

object DeckGrouping {
    private val ORDER = CardTypeClassifier.TYPE_ORDER + listOf("Other", "Unknown")

    fun group(cards: List<DeckCard>): List<CardGroup> =
        cards
            .groupBy { CardTypeClassifier.primaryType(it.typeLine) }
            .map { (type, groupCards) -> CardGroup(type, groupCards.sortedBy { it.name }) }
            .sortedBy { group ->
                ORDER.indexOf(group.type).let { if (it == -1) ORDER.size else it }
            }
}
