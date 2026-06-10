package com.deckpuller.domain.model

data class DeckSummary(
    val archidektId: String,
    val name: String,
    val cardCount: Int,
    val thumbnailUrl: String?,
)
