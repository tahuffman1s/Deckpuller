package com.deckpuller.domain

import java.text.Normalizer

/** Canonical key for matching card names across Scryfall (deck) and ManaBox (collection). */
object CardName {

    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")
    // Any "/" run, optionally space-padded, becomes the canonical " // " separator.
    private val faceSeparator = Regex("\\s*/+\\s*")

    fun normalize(raw: String): String {
        val deaccented = Normalizer.normalize(raw, Normalizer.Form.NFKD)
            .replace(combiningMarks, "")
        return deaccented
            .replace(faceSeparator, " // ")
            .replace(whitespace, " ")
            .trim()
            .lowercase()
    }
}
