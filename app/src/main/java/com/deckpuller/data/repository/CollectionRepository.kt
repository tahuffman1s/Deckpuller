package com.deckpuller.data.repository

import com.deckpuller.domain.model.OwnedInfo
import kotlinx.coroutines.flow.Flow

/** Result of importing a ManaBox CSV. */
data class CollectionImportResult(val imported: Int, val skipped: Int)

/** User-facing summary of a successful import, e.g. "Imported 812 cards · 3 skipped". */
fun CollectionImportResult.toUserMessage(): String =
    "Imported $imported cards" + if (skipped > 0) " · $skipped skipped" else ""

interface CollectionRepository {
    /** nameKey -> aggregated ownership, recomputed whenever the collection changes. */
    fun observeOwnedByName(): Flow<Map<String, OwnedInfo>>

    /** All collection rows, alphabetical, for the browser. */
    fun observeAll(): Flow<List<com.deckpuller.data.local.entity.CollectionCardEntity>>

    val importedAt: Flow<Long?>
    val count: Flow<Int>

    /**
     * Parse [csv], replace the stored collection wholesale, and stamp freshness.
     * @throws com.deckpuller.data.ManaBoxCsvParser.MissingColumnException for bad headers.
     */
    suspend fun importCsv(csv: String, now: Long): CollectionImportResult
}
