package com.deckpuller.domain

object CardTypeClassifier {
    // Order = display/priority order. First match in the (pre-dash, front-face)
    // segment of the type line wins, so "Artifact Creature" classifies as Creature.
    val TYPE_ORDER = listOf(
        "Creature",
        "Planeswalker",
        "Instant",
        "Sorcery",
        "Artifact",
        "Enchantment",
        "Battle",
        "Land",
    )

    fun primaryType(typeLine: String?): String {
        if (typeLine.isNullOrBlank()) return "Unknown"
        val front = typeLine.substringBefore("//").substringBefore("—")
        return TYPE_ORDER.firstOrNull { front.contains(it, ignoreCase = true) } ?: "Other"
    }
}
