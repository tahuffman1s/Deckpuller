package com.deckpuller.domain

/**
 * Strips diacritics via Unicode NFKD decomposition + combining-mark removal. This is the one
 * platform-bound step of [CardName.normalize]: the JVM uses java.text.Normalizer + the `\p{Mn}`
 * regex (Unicode-property classes aren't reliable on Kotlin/Native), so it's an expect/actual.
 */
internal expect fun deaccentNfkd(raw: String): String

/** Canonical key for matching card names across Scryfall (deck) and ManaBox (collection). */
object CardName {

    private val whitespace = Regex("\\s+")
    // Any "/" run, optionally space-padded, becomes the canonical " // " separator.
    // A single bare "/" is INTENTIONALLY treated as a DFC/split separator: external sources
    // (ManaBox CSV, hand-typed lists) write the "//" separator inconsistently (e.g. "A//B",
    // "A / B"). Real MTG card names never contain a bare single slash, so this is safe.
    private val faceSeparator = Regex("\\s*/+\\s*")

    fun normalize(raw: String): String {
        return deaccentNfkd(raw)
            .replace(faceSeparator, " // ")
            // Whitespace collapse MUST run after faceSeparator replacement so it does not
            // destroy the single spaces injected around "//".
            .replace(whitespace, " ")
            .trim()
            .lowercase()
    }
}
