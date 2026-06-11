package com.deckpuller.domain.model

/** A single physical printing the user owns, for the pull-screen detail line. */
data class OwnedPrinting(
    val setCode: String,
    val finish: String,
    val quantity: Int,
    val binderName: String,
    val scryfallId: String? = null,
)

/** Aggregated ownership for one card name (across all printings). */
data class OwnedInfo(
    val totalQty: Int,
    val printings: List<OwnedPrinting>,
)
