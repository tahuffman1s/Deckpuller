package com.deckpuller.data

import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckCard

fun DeckWithCards.toDomain(): Deck = Deck(
    name = deck.name,
    cards = cards.map { it.toDomain() },
)

fun CardEntity.toDomain(): DeckCard = DeckCard(
    id = id,
    scryfallId = scryfallId,
    name = name,
    typeLine = typeLine,
    imageUrl = imageUrl,
    requiredQty = requiredQty,
    pulledQty = pulledQty,
)
