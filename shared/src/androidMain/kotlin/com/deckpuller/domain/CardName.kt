package com.deckpuller.domain

import java.text.Normalizer

/** Canonical key for matching card names across Scryfall (deck) and ManaBox (collection). */
object CardName {

    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")
    // Any "/" run, optionally space-padded, becomes the canonical " // " separator.
    // A single bare "/" is INTENTIONALLY treated as a DFC/split separator: external sources
    // (ManaBox CSV, hand-typed lists) write the "//" separator inconsistently (e.g. "A//B",
    // "A / B"). Real MTG card names never contain a bare single slash, so this is safe.
    private val faceSeparator = Regex("\\s*/+\\s*")

    fun normalize(raw: String): String {
        val deaccented = Normalizer.normalize(raw, Normalizer.Form.NFKD)
            .replace(combiningMarks, "")
        return deaccented
            .replace(faceSeparator, " // ")
            // Whitespace collapse MUST run after faceSeparator replacement so it does not
            // destroy the single spaces injected around "//".
            .replace(whitespace, " ")
            .trim()
            .lowercase()
    }
}
