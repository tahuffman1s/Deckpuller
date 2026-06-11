package com.deckpuller.data

import com.deckpuller.domain.CardName

/** Parses a ManaBox collection CSV export into rows, mapping columns by header name. */
object ManaBoxCsvParser {

    data class ParsedCollectionCard(
        val nameKey: String,
        val name: String,
        val setCode: String,
        val setName: String,
        val collectorNumber: String,
        val scryfallId: String?,
        val finish: String,
        val condition: String,
        val language: String,
        val binderName: String,
        val quantity: Int,
    )

    data class ParsedCollection(
        val cards: List<ParsedCollectionCard>,
        val failedLines: List<Int>,
    )

    class MissingColumnException(val column: String) :
        IllegalArgumentException("Required column missing: $column")

    fun parse(csv: String): ParsedCollection {
        val lines = csv.split("\n").map { it.removeSuffix("\r") }.filter { it.isNotBlank() }
        if (lines.isEmpty()) throw MissingColumnException("Name")

        val header = splitCsvLine(lines.first()).map { it.trim() }
        val index = header.withIndex().associate { (i, h) -> h.lowercase() to i }
        fun col(name: String): Int? = index[name.lowercase()]

        val nameIdx = col("Name") ?: throw MissingColumnException("Name")
        val qtyIdx = col("Quantity") ?: throw MissingColumnException("Quantity")
        val setCodeIdx = col("Set code")
        val setNameIdx = col("Set name")
        val collectorIdx = col("Collector number")
        val scryfallIdx = col("Scryfall ID")
        val foilIdx = col("Foil")
        val conditionIdx = col("Condition")
        val languageIdx = col("Language")
        val binderIdx = col("Binder Name")

        val cards = mutableListOf<ParsedCollectionCard>()
        val failed = mutableListOf<Int>()

        lines.drop(1).forEachIndexed { i, line ->
            val lineNumber = i + 2 // 1-based, accounting for header
            val fields = splitCsvLine(line)
            fun at(idx: Int?): String = idx?.let { fields.getOrNull(it) }?.trim().orEmpty()
            val name = at(nameIdx)
            val qty = at(qtyIdx).toIntOrNull()
            if (name.isBlank() || qty == null) {
                failed += lineNumber
                return@forEachIndexed
            }
            cards += ParsedCollectionCard(
                nameKey = CardName.normalize(name),
                name = name,
                setCode = at(setCodeIdx),
                setName = at(setNameIdx),
                collectorNumber = at(collectorIdx),
                scryfallId = at(scryfallIdx).ifBlank { null },
                finish = at(foilIdx).ifBlank { "normal" },
                condition = at(conditionIdx),
                language = at(languageIdx),
                binderName = at(binderIdx),
                quantity = qty,
            )
        }
        return ParsedCollection(cards, failed)
    }

    /** RFC-4180-ish split: handles double-quoted fields containing commas and escaped quotes. */
    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
