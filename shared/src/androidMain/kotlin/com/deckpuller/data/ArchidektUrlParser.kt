package com.deckpuller.data

object ArchidektUrlParser {
    private val DECK_ID = Regex("""decks/(\d+)""")

    fun parseDeckId(input: String): String? =
        DECK_ID.find(input)?.groupValues?.get(1)
}
