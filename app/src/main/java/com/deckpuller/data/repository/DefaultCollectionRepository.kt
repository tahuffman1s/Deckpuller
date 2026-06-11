package com.deckpuller.data.repository

import com.deckpuller.data.ManaBoxCsvParser
import com.deckpuller.data.local.CollectionDao
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.prefs.UserPreferences
import com.deckpuller.domain.model.OwnedInfo
import com.deckpuller.domain.model.OwnedPrinting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultCollectionRepository(
    private val dao: CollectionDao,
    private val prefs: UserPreferences,
) : CollectionRepository {

    override fun observeAll(): Flow<List<CollectionCardEntity>> = dao.observeAll()

    override val importedAt: Flow<Long?> = prefs.collectionImportedAt
    override val count: Flow<Int> = prefs.collectionCount

    override fun observeOwnedByName(): Flow<Map<String, OwnedInfo>> =
        dao.observeAll().map { rows ->
            rows.groupBy { it.nameKey }.mapValues { (_, group) ->
                OwnedInfo(
                    totalQty = group.sumOf { it.quantity },
                    printings = group.map {
                        OwnedPrinting(it.setCode, it.finish, it.quantity, it.binderName, it.scryfallId)
                    },
                )
            }
        }

    override suspend fun importCsv(csv: String, now: Long): CollectionImportResult {
        val parsed = withContext(Dispatchers.Default) { ManaBoxCsvParser.parse(csv) }
        val rows = withContext(Dispatchers.Default) {
            parsed.cards.map {
                CollectionCardEntity(
                    nameKey = it.nameKey,
                    name = it.name,
                    setCode = it.setCode,
                    setName = it.setName,
                    collectorNumber = it.collectorNumber,
                    scryfallId = it.scryfallId,
                    finish = it.finish,
                    condition = it.condition,
                    language = it.language,
                    binderName = it.binderName,
                    quantity = it.quantity,
                )
            }
        }
        dao.replaceAll(rows)
        val stored = dao.count()
        prefs.setCollectionImported(now, stored)
        return CollectionImportResult(imported = parsed.cards.size, skipped = parsed.failedLines.size)
    }
}
