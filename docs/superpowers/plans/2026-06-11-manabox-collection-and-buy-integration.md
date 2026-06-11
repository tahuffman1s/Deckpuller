# ManaBox Collection Import + Store Buy Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import a ManaBox collection CSV so the pull screen shows owned/missing cards, add a collection browser, and turn missing cards into one-tap purchases on TCGplayer / Card Kingdom (or clipboard).

**Architecture:** A new collection module parallel to the existing deck stack. Pure logic (`CardName`, `ManaBoxCsvParser`, `StoreCartLinks`) is separated from Android I/O (`CollectionImporter`, Room). A single global collection is replaced wholesale on each import. The pull screen and a new shopping-list screen consume collection ownership; the shopping list also fetches Scryfall prices via the existing batched endpoint.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, Room (KSP), Retrofit + kotlinx.serialization, DataStore, Coroutines/Flow. Tests: JUnit4, Robolectric, Turbine, MockK, room-testing, coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-11-manabox-collection-and-buy-integration-design.md`

**Branch:** `feature/manabox-collection-buy-integration` (already created).

**Convention note:** All commit commands below end with the required trailer:
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## File Structure

**New files:**
- `app/src/main/java/com/deckpuller/domain/CardName.kt` — pure name normalization.
- `app/src/main/java/com/deckpuller/domain/model/OwnedInfo.kt` — ownership value types.
- `app/src/main/java/com/deckpuller/data/ManaBoxCsvParser.kt` — pure CSV parser + result types.
- `app/src/main/java/com/deckpuller/data/local/entity/CollectionCardEntity.kt` — Room entity + `OwnedTotal`.
- `app/src/main/java/com/deckpuller/data/local/CollectionDao.kt` — collection DAO.
- `app/src/main/java/com/deckpuller/data/CollectionImporter.kt` — Uri → text (Android I/O).
- `app/src/main/java/com/deckpuller/data/repository/CollectionRepository.kt` — interface.
- `app/src/main/java/com/deckpuller/data/repository/DefaultCollectionRepository.kt` — impl.
- `app/src/main/java/com/deckpuller/domain/StoreCartLinks.kt` — pure buy-link/clipboard builder.
- `app/src/main/java/com/deckpuller/ui/collection/CollectionViewModel.kt`
- `app/src/main/java/com/deckpuller/ui/collection/CollectionScreen.kt`
- `app/src/main/java/com/deckpuller/ui/shopping/ShoppingListViewModel.kt`
- `app/src/main/java/com/deckpuller/ui/shopping/ShoppingListScreen.kt`
- Tests mirroring each under `app/src/test/java/...`.

**Modified files:**
- `app/src/main/java/com/deckpuller/data/local/AppDatabase.kt` — add entity, version 4, migration.
- `app/src/main/java/com/deckpuller/di/DataModule.kt` — provide DAO, bind repo, register migration.
- `app/src/main/java/com/deckpuller/data/prefs/UserPreferences.kt` — collection freshness keys.
- `app/src/main/java/com/deckpuller/domain/model/Deck.kt` — transient ownership fields on `DeckCard`.
- `app/src/main/java/com/deckpuller/ui/pull/PullViewModel.kt` — ownership enrichment + nav callback.
- `app/src/main/java/com/deckpuller/ui/pull/CardRow.kt` — owned/missing indicator.
- `app/src/main/java/com/deckpuller/ui/pull/PullScreen.kt` — app-bar action to shopping list.
- `app/src/main/java/com/deckpuller/data/remote/dto/ScryfallDto.kt` — `prices` object.
- `app/src/main/java/com/deckpuller/data/repository/DeckRepository.kt` + `DefaultDeckRepository.kt` — `prices()`.
- `app/src/main/java/com/deckpuller/ui/AppRoot.kt` — `collection` + `shopping/{deckId}` routes.
- `app/src/main/java/com/deckpuller/ui/decklist/DeckListScreen.kt` — top-bar collection entry.
- `app/src/main/java/com/deckpuller/MainActivity.kt` — handle incoming CSV share intent.
- `app/src/main/AndroidManifest.xml` — CSV share/view intent-filter.

---

## Phase A — Collection data foundation

### Task 1: Card name normalization

**Files:**
- Create: `app/src/main/java/com/deckpuller/domain/CardName.kt`
- Test: `app/src/test/java/com/deckpuller/domain/CardNameTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.deckpuller.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CardNameTest {

    @Test
    fun `lowercases and trims`() {
        assertEquals("sol ring", CardName.normalize("  Sol Ring  "))
    }

    @Test
    fun `collapses internal whitespace`() {
        assertEquals("rhystic study", CardName.normalize("Rhystic   Study"))
    }

    @Test
    fun `folds accents`() {
        assertEquals("lim-dul the necromancer", CardName.normalize("Lim-Dûl the Necromancer"))
    }

    @Test
    fun `keeps double-faced separator consistent`() {
        assertEquals(
            "pestilent cauldron // restorative burst",
            CardName.normalize("Pestilent Cauldron // Restorative Burst"),
        )
    }

    @Test
    fun `normalizes loose dfc separator spacing to double-space slashes`() {
        assertEquals("a // b", CardName.normalize("A//B"))
        assertEquals("a // b", CardName.normalize("A / B"))
    }

    @Test
    fun `keeps apostrophes and commas but compares case-insensitively`() {
        assertEquals("conqueror's foothold", CardName.normalize("Conqueror's Foothold"))
        assertEquals("mazirek, kraul death priest", CardName.normalize("Mazirek, Kraul Death Priest"))
    }

    @Test
    fun `blank in blank out`() {
        assertEquals("", CardName.normalize("   "))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.domain.CardNameTest"`
Expected: FAIL — `CardName` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.domain.CardNameTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/domain/CardName.kt app/src/test/java/com/deckpuller/domain/CardNameTest.kt
git commit -m "feat: add CardName normalization for collection matching

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: ManaBox CSV parser

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/ManaBoxCsvParser.kt`
- Test: `app/src/test/java/com/deckpuller/data/ManaBoxCsvParserTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.deckpuller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManaBoxCsvParserTest {

    private val header =
        "Binder Name,Binder Type,Name,Set code,Set name,Collector number,Foil,Rarity," +
            "Quantity,ManaBox ID,Scryfall ID,Purchase price,Misprint,Altered,Condition," +
            "Language,Purchase price currency,Added"

    private fun parse(vararg rows: String) =
        ManaBoxCsvParser.parse((listOf(header) + rows).joinToString("\n"))

    @Test
    fun `parses a simple row`() {
        val result = parse(
            "EDH,binder,Sol Ring,LTC,Commander Legends,472,normal,uncommon,2,1,sid-1,1.5,false,false,near_mint,en,USD,2026-01-01",
        )
        assertEquals(1, result.cards.size)
        val c = result.cards.single()
        assertEquals("Sol Ring", c.name)
        assertEquals("sol ring", c.nameKey)
        assertEquals("LTC", c.setCode)
        assertEquals("472", c.collectorNumber)
        assertEquals("normal", c.finish)
        assertEquals(2, c.quantity)
        assertEquals("sid-1", c.scryfallId)
        assertEquals("EDH", c.binderName)
        assertTrue(result.failedLines.isEmpty())
    }

    @Test
    fun `handles quoted name containing a comma`() {
        val result = parse(
            "EDH,binder,\"Mazirek, Kraul Death Priest\",EOC,Edge of Eternities Commander,122,normal,rare,1,2,sid-2,0.4,false,false,near_mint,en,USD,2026-01-01",
        )
        assertEquals("Mazirek, Kraul Death Priest", result.cards.single().name)
        assertEquals("mazirek, kraul death priest", result.cards.single().nameKey)
    }

    @Test
    fun `handles double-faced names and quoted dfc with comma`() {
        val result = parse(
            "EDH,binder,Pestilent Cauldron // Restorative Burst,STX,Strixhaven,154,normal,rare,1,3,sid-3,0.29,false,false,near_mint,en,USD,2026-01-01",
            "EDH,binder,\"Lluwen, Exchange Student // Pest Friend\",SOS,Secrets,199,foil,uncommon,1,4,sid-4,0.28,false,false,near_mint,en,USD,2026-01-01",
        )
        assertEquals("pestilent cauldron // restorative burst", result.cards[0].nameKey)
        assertEquals("lluwen, exchange student // pest friend", result.cards[1].nameKey)
        assertEquals("foil", result.cards[1].finish)
    }

    @Test
    fun `maps columns by header name regardless of order`() {
        val reordered = "Name,Quantity,Set code\nSol Ring,3,LTC"
        val result = ManaBoxCsvParser.parse(reordered)
        assertEquals(1, result.cards.size)
        assertEquals(3, result.cards.single().quantity)
        assertEquals("LTC", result.cards.single().setCode)
        assertEquals("", result.cards.single().scryfallId.orEmpty())
    }

    @Test
    fun `records failed lines but imports the rest`() {
        val result = parse(
            "EDH,binder,Sol Ring,LTC,Commander,472,normal,uncommon,2,1,sid-1,1.5,false,false,near_mint,en,USD,2026-01-01",
            "EDH,binder,Broken Row,LTC,Commander,472,normal,uncommon,notanumber,1,sid-x,1.5,false,false,near_mint,en,USD,2026-01-01",
        )
        assertEquals(1, result.cards.size)
        assertEquals(1, result.failedLines.size)
    }

    @Test
    fun `throws when required column missing`() {
        val ex = runCatching { ManaBoxCsvParser.parse("Set code,Quantity\nLTC,1") }.exceptionOrNull()
        assertTrue(ex is ManaBoxCsvParser.MissingColumnException)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.ManaBoxCsvParserTest"`
Expected: FAIL — `ManaBoxCsvParser` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.ManaBoxCsvParserTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/ManaBoxCsvParser.kt app/src/test/java/com/deckpuller/data/ManaBoxCsvParserTest.kt
git commit -m "feat: parse ManaBox collection CSV exports

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Collection Room entity, DAO, and migration

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/local/entity/CollectionCardEntity.kt`
- Create: `app/src/main/java/com/deckpuller/data/local/CollectionDao.kt`
- Modify: `app/src/main/java/com/deckpuller/data/local/AppDatabase.kt`
- Test: `app/src/test/java/com/deckpuller/data/local/CollectionDaoTest.kt`

- [ ] **Step 1: Create the entity and DAO**

`CollectionCardEntity.kt`:
```kotlin
package com.deckpuller.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collection_cards", indices = [Index("nameKey")])
data class CollectionCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

/** Projection for owned-by-name aggregation. */
data class OwnedTotal(val nameKey: String, val qty: Int)
```

`CollectionDao.kt`:
```kotlin
package com.deckpuller.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.local.entity.OwnedTotal
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collection_cards ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CollectionCardEntity>>

    @Query("SELECT nameKey, SUM(quantity) AS qty FROM collection_cards GROUP BY nameKey")
    fun observeOwnedTotals(): Flow<List<OwnedTotal>>

    @Query("SELECT COUNT(*) FROM collection_cards")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(rows: List<CollectionCardEntity>)

    @Query("DELETE FROM collection_cards")
    suspend fun clear()

    /** Replace the entire collection atomically. */
    @Transaction
    suspend fun replaceAll(rows: List<CollectionCardEntity>) {
        clear()
        insertAll(rows)
    }
}
```

- [ ] **Step 2: Wire the entity into `AppDatabase` (modify)**

Replace the body of `AppDatabase.kt` with:
```kotlin
package com.deckpuller.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.local.entity.DeckEntity

@Database(
    entities = [DeckEntity::class, CardEntity::class, CollectionCardEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        /** Adds the Archidekt category column without wiping existing decks. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Adds the ManaBox collection table. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS collection_cards (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "nameKey TEXT NOT NULL, name TEXT NOT NULL, setCode TEXT NOT NULL, " +
                        "setName TEXT NOT NULL, collectorNumber TEXT NOT NULL, scryfallId TEXT, " +
                        "finish TEXT NOT NULL, condition TEXT NOT NULL, language TEXT NOT NULL, " +
                        "binderName TEXT NOT NULL, quantity INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_cards_nameKey ON collection_cards(nameKey)")
            }
        }
    }
}
```

- [ ] **Step 3: Write the failing DAO test**

`CollectionDaoTest.kt`:
```kotlin
package com.deckpuller.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deckpuller.data.local.entity.CollectionCardEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CollectionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.collectionDao()
    }

    @After
    fun tearDown() = db.close()

    private fun row(nameKey: String, qty: Int, setCode: String = "AAA") = CollectionCardEntity(
        nameKey = nameKey, name = nameKey, setCode = setCode, setName = "Set",
        collectorNumber = "1", scryfallId = "sid-$setCode", finish = "normal",
        condition = "near_mint", language = "en", binderName = "EDH", quantity = qty,
    )

    @Test
    fun `replaceAll wipes prior rows`() = runTest {
        dao.replaceAll(listOf(row("sol ring", 1)))
        dao.replaceAll(listOf(row("rhystic study", 2), row("rhystic study", 1, setCode = "BBB")))
        assertEquals(2, dao.count())
        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun `observeOwnedTotals sums quantity across printings`() = runTest {
        dao.replaceAll(
            listOf(
                row("rhystic study", 2, setCode = "AAA"),
                row("rhystic study", 1, setCode = "BBB"),
                row("sol ring", 4),
            ),
        )
        val totals = dao.observeOwnedTotals().first().associate { it.nameKey to it.qty }
        assertEquals(3, totals["rhystic study"])
        assertEquals(4, totals["sol ring"])
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.local.CollectionDaoTest"`
Expected: PASS (2 tests). (Room/KSP regenerates the DAO during the test build.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/local/entity/CollectionCardEntity.kt \
  app/src/main/java/com/deckpuller/data/local/CollectionDao.kt \
  app/src/main/java/com/deckpuller/data/local/AppDatabase.kt \
  app/src/test/java/com/deckpuller/data/local/CollectionDaoTest.kt
git commit -m "feat: add collection_cards table, DAO, and migration 3->4

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Collection freshness in UserPreferences

**Files:**
- Modify: `app/src/main/java/com/deckpuller/data/prefs/UserPreferences.kt`

- [ ] **Step 1: Add keys and accessors (modify)**

Add the imports and members to `UserPreferences`:
```kotlin
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
```
Below the existing `ARCHIDEKT_USERNAME` key:
```kotlin
private val COLLECTION_IMPORTED_AT = longPreferencesKey("collection_imported_at")
private val COLLECTION_COUNT = intPreferencesKey("collection_count")
```
Inside the class, after `setUsername`:
```kotlin
    val collectionImportedAt: Flow<Long?> =
        context.dataStore.data.map { it[COLLECTION_IMPORTED_AT] }

    val collectionCount: Flow<Int> =
        context.dataStore.data.map { it[COLLECTION_COUNT] ?: 0 }

    suspend fun setCollectionImported(timestamp: Long, count: Int) {
        context.dataStore.edit {
            it[COLLECTION_IMPORTED_AT] = timestamp
            it[COLLECTION_COUNT] = count
        }
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/prefs/UserPreferences.kt
git commit -m "feat: track collection import timestamp and count in prefs

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Ownership value types

**Files:**
- Create: `app/src/main/java/com/deckpuller/domain/model/OwnedInfo.kt`

- [ ] **Step 1: Create the types**

```kotlin
package com.deckpuller.domain.model

/** A single physical printing the user owns, for the pull-screen detail line. */
data class OwnedPrinting(
    val setCode: String,
    val finish: String,
    val quantity: Int,
    val binderName: String,
)

/** Aggregated ownership for one card name (across all printings). */
data class OwnedInfo(
    val totalQty: Int,
    val printings: List<OwnedPrinting>,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deckpuller/domain/model/OwnedInfo.kt
git commit -m "feat: add OwnedInfo/OwnedPrinting ownership value types

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: CollectionImporter (Uri → text)

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/CollectionImporter.kt`

- [ ] **Step 1: Create the importer**

This is a thin Android I/O wrapper (not unit-tested directly; exercised via manual import and the repository).
```kotlin
package com.deckpuller.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the text content of a user-picked / shared CSV Uri. */
@Singleton
class CollectionImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** @throws java.io.IOException if the Uri can't be opened. */
    fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw java.io.IOException("Could not open $uri")
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/CollectionImporter.kt
git commit -m "feat: add CollectionImporter to read CSV content from a Uri

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: CollectionRepository

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/repository/CollectionRepository.kt`
- Create: `app/src/main/java/com/deckpuller/data/repository/DefaultCollectionRepository.kt`
- Test: `app/src/test/java/com/deckpuller/data/repository/DefaultCollectionRepositoryTest.kt`

- [ ] **Step 1: Define the interface**

`CollectionRepository.kt`:
```kotlin
package com.deckpuller.data.repository

import com.deckpuller.domain.model.OwnedInfo
import kotlinx.coroutines.flow.Flow

/** Result of importing a ManaBox CSV. */
data class CollectionImportResult(val imported: Int, val skipped: Int)

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
```

- [ ] **Step 2: Write the failing test**

`DefaultCollectionRepositoryTest.kt`:
```kotlin
package com.deckpuller.data.repository

import com.deckpuller.data.ManaBoxCsvParser
import com.deckpuller.data.local.CollectionDao
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.local.entity.OwnedTotal
import com.deckpuller.data.prefs.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCollectionRepositoryTest {

    private val dao = mockk<CollectionDao>(relaxed = true)
    private val prefs = mockk<UserPreferences>(relaxed = true)
    private val repo = DefaultCollectionRepository(dao, prefs)

    private val header =
        "Name,Set code,Set name,Collector number,Foil,Quantity,Scryfall ID,Condition,Language,Binder Name"

    @Test
    fun `importCsv replaces rows and stamps freshness`() = runTest {
        val csv = listOf(
            header,
            "Sol Ring,LTC,Commander,472,normal,2,sid-1,near_mint,en,EDH",
            "Bad Row,LTC,Commander,472,normal,notanumber,sid-x,near_mint,en,EDH",
        ).joinToString("\n")

        val captured = slot<List<CollectionCardEntity>>()
        coEvery { dao.replaceAll(capture(captured)) } returns Unit
        coEvery { dao.count() } returns 1

        val result = repo.importCsv(csv, now = 123L)

        assertEquals(CollectionImportResult(imported = 1, skipped = 1), result)
        assertEquals(1, captured.captured.size)
        assertEquals("sol ring", captured.captured.single().nameKey)
        coVerify { prefs.setCollectionImported(123L, 1) }
    }

    @Test
    fun `observeOwnedByName aggregates printings`() = runTest {
        every { dao.observeAll() } returns flowOf(
            listOf(
                CollectionCardEntity(
                    nameKey = "rhystic study", name = "Rhystic Study", setCode = "CMR",
                    setName = "Commander Legends", collectorNumber = "1", scryfallId = "s1",
                    finish = "normal", condition = "nm", language = "en", binderName = "EDH", quantity = 1,
                ),
                CollectionCardEntity(
                    nameKey = "rhystic study", name = "Rhystic Study", setCode = "PCY",
                    setName = "Prophecy", collectorNumber = "2", scryfallId = "s2",
                    finish = "foil", condition = "nm", language = "en", binderName = "Box", quantity = 2,
                ),
            ),
        )

        val map = repo.observeOwnedByName().first()
        assertEquals(3, map["rhystic study"]!!.totalQty)
        assertEquals(2, map["rhystic study"]!!.printings.size)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.repository.DefaultCollectionRepositoryTest"`
Expected: FAIL — `DefaultCollectionRepository` unresolved.

- [ ] **Step 4: Write the implementation**

`DefaultCollectionRepository.kt`:
```kotlin
package com.deckpuller.data.repository

import com.deckpuller.data.ManaBoxCsvParser
import com.deckpuller.data.local.CollectionDao
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.prefs.UserPreferences
import com.deckpuller.domain.model.OwnedInfo
import com.deckpuller.domain.model.OwnedPrinting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultCollectionRepository @Inject constructor(
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
                        OwnedPrinting(it.setCode, it.finish, it.quantity, it.binderName)
                    },
                )
            }
        }

    override suspend fun importCsv(csv: String, now: Long): CollectionImportResult {
        val parsed = ManaBoxCsvParser.parse(csv)
        val rows = parsed.cards.map {
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
        dao.replaceAll(rows)
        val stored = dao.count()
        prefs.setCollectionImported(now, stored)
        return CollectionImportResult(imported = parsed.cards.size, skipped = parsed.failedLines.size)
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.repository.DefaultCollectionRepositoryTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/repository/CollectionRepository.kt \
  app/src/main/java/com/deckpuller/data/repository/DefaultCollectionRepository.kt \
  app/src/test/java/com/deckpuller/data/repository/DefaultCollectionRepositoryTest.kt
git commit -m "feat: add CollectionRepository for import + ownership flows

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: DI wiring (DAO, repo binding, migration)

**Files:**
- Modify: `app/src/main/java/com/deckpuller/di/DataModule.kt`

- [ ] **Step 1: Add the binding and providers (modify)**

Add imports:
```kotlin
import com.deckpuller.data.local.CollectionDao
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.data.repository.DefaultCollectionRepository
```
Add a `@Binds` next to `bindDeckRepository`:
```kotlin
    @Binds
    @Singleton
    abstract fun bindCollectionRepository(impl: DefaultCollectionRepository): CollectionRepository
```
Update `provideDatabase` to register the new migration:
```kotlin
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "deckpuller.db")
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
```
Add a DAO provider next to `provideDeckDao`:
```kotlin
        @Provides
        @Singleton
        fun provideCollectionDao(db: AppDatabase): CollectionDao = db.collectionDao()
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Hilt graph resolves).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deckpuller/di/DataModule.kt
git commit -m "feat: wire CollectionDao/Repository into Hilt and register migration

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phase B — Collection browser + in-app import

### Task 9: CollectionViewModel

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/collection/CollectionViewModel.kt`
- Test: `app/src/test/java/com/deckpuller/ui/collection/CollectionViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.deckpuller.ui.collection

import app.cash.turbine.test
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.repository.CollectionImportResult
import com.deckpuller.data.repository.CollectionRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {

    private val repo = mockk<CollectionRepository>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun row(name: String) = CollectionCardEntity(
        nameKey = name.lowercase(), name = name, setCode = "AAA", setName = "Set",
        collectorNumber = "1", scryfallId = "s", finish = "normal", condition = "nm",
        language = "en", binderName = "EDH", quantity = 1,
    )

    @Test
    fun `filters by search query`() = runTest {
        coEvery { repo.observeAll() } returns flowOf(listOf(row("Sol Ring"), row("Rhystic Study")))
        coEvery { repo.importedAt } returns flowOf(123L)
        coEvery { repo.count } returns flowOf(2)
        val vm = CollectionViewModel(repo)

        vm.onSearchChange("sol")
        vm.state.test {
            // Skip emissions until the filtered one arrives.
            var s = awaitItem()
            while (s.cards.size != 1) s = awaitItem()
            assertEquals("Sol Ring", s.cards.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.collection.CollectionViewModelTest"`
Expected: FAIL — `CollectionViewModel` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.deckpuller.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.local.entity.CollectionCardEntity
import com.deckpuller.data.repository.CollectionImportResult
import com.deckpuller.data.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionUiState(
    val cards: List<CollectionCardEntity> = emptyList(),
    val searchQuery: String = "",
    val importedAt: Long? = null,
    val totalCount: Int = 0,
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val repository: CollectionRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /** One-shot import feedback ("Imported 812 · 3 skipped" / error), consumed by the screen. */
    val importMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<CollectionUiState> =
        combine(
            repository.observeAll(),
            query,
            repository.importedAt,
            repository.count,
        ) { all, q, importedAt, count ->
            val filtered = if (q.isBlank()) all
            else all.filter { it.name.contains(q, ignoreCase = true) }
            CollectionUiState(
                cards = filtered,
                searchQuery = q,
                importedAt = importedAt,
                totalCount = count,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState())

    fun onSearchChange(value: String) { query.value = value }

    fun importCsv(csv: String, now: Long) {
        viewModelScope.launch {
            importMessage.value = runCatching { repository.importCsv(csv, now) }
                .fold(
                    onSuccess = { r -> "Imported ${r.imported} cards" + if (r.skipped > 0) " · ${r.skipped} skipped" else "" },
                    onFailure = { e -> "Import failed: ${e.message}" },
                )
        }
    }

    fun clearMessage() { importMessage.value = null }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.collection.CollectionViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/collection/CollectionViewModel.kt \
  app/src/test/java/com/deckpuller/ui/collection/CollectionViewModelTest.kt
git commit -m "feat: add CollectionViewModel (search + import feedback)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: CollectionScreen (browser + file picker)

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/collection/CollectionScreen.kt`

- [ ] **Step 1: Create the screen + route**

```kotlin
package com.deckpuller.ui.collection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@Composable
fun CollectionRoute(onBack: () -> Unit) {
    val viewModel: CollectionViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.importMessage.collectAsStateWithLifecycle()
    CollectionScreen(
        state = state,
        importMessage = message,
        onSearchChange = viewModel::onSearchChange,
        onImportCsv = { csv -> viewModel.importCsv(csv, System.currentTimeMillis()) },
        onMessageShown = viewModel::clearMessage,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    state: CollectionUiState,
    importMessage: String?,
    onSearchChange: (String) -> Unit,
    onImportCsv: (String) -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            }
            if (text != null) onImportCsv(text)
        }
    }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Collection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Button(
                onClick = {
                    // Accept any text-ish mime; ManaBox exports vary (text/csv, text/comma-separated-values).
                    picker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "*/*"))
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) { Text("Import ManaBox CSV") }

            if (state.totalCount > 0) {
                val when_ = state.importedAt?.let {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                } ?: "—"
                Text("${state.totalCount} cards · imported $when_")
            }

            if (state.totalCount == 0) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No collection imported yet. Export a CSV from ManaBox and import it here.")
                }
            } else {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("Search collection") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.cards, key = { it.id }) { card ->
                        ListItem(
                            headlineContent = {
                                Text(card.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                val foil = if (card.finish != "normal") " · ${card.finish}" else ""
                                Text("${card.quantity}× · ${card.setCode}$foil")
                            },
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/collection/CollectionScreen.kt
git commit -m "feat: add CollectionScreen with browser, search, and file-picker import

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Navigation — collection route + deck-list entry

**Files:**
- Modify: `app/src/main/java/com/deckpuller/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/deckpuller/ui/decklist/DeckListScreen.kt`

- [ ] **Step 1: Add the route to AppRoot (modify)**

Add import:
```kotlin
import com.deckpuller.ui.collection.CollectionRoute
```
Add a route constant near the others:
```kotlin
private const val COLLECTION = "collection"
```
Add a `composable` inside `NavHost`:
```kotlin
        composable(COLLECTION) {
            CollectionRoute(onBack = { navController.popBackStack() })
        }
```
Pass a navigation callback into `DeckListScreen` (in the `DECK_LIST` composable):
```kotlin
            DeckListScreen(
                decks = items,
                onDeckClick = { id: Long -> navController.navigate("$PULL/$id") },
                onAddDeck = { navController.navigate(ADD_DECK) },
                onDeleteDeck = viewModel::delete,
                onSettings = { navController.navigate(SETTINGS) },
                onCollection = { navController.navigate(COLLECTION) },
            )
```

- [ ] **Step 2: Add the `onCollection` param + top-bar action to DeckListScreen (modify)**

In `DeckListScreen`'s signature add the parameter `onCollection: () -> Unit`. In the existing top-app-bar actions block, add an icon button alongside the settings one:
```kotlin
import androidx.compose.material.icons.filled.Style // collection-style icon
```
```kotlin
                    IconButton(onClick = onCollection) {
                        Icon(Icons.Default.Style, contentDescription = "Collection")
                    }
```
(Place it before the existing Settings `IconButton`. If the screen's previews call `DeckListScreen`, add `onCollection = {}` to those preview invocations.)

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the deck-list screen test to confirm no regression**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.decklist.DeckListScreenTest"`
Expected: PASS (update the test's `DeckListScreen(...)` call to pass `onCollection = {}` if it fails to compile).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/AppRoot.kt \
  app/src/main/java/com/deckpuller/ui/decklist/DeckListScreen.kt \
  app/src/test/java/com/deckpuller/ui/decklist/DeckListScreenTest.kt
git commit -m "feat: add collection route and deck-list entry point

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Share-target import (MainActivity + Manifest)

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/deckpuller/MainActivity.kt`

- [ ] **Step 1: Add the intent-filter (modify Manifest)**

Inside the `<activity android:name=".MainActivity" ...>` element, after the existing LAUNCHER `intent-filter`, add:
```xml
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/csv" />
                <data android:mimeType="text/comma-separated-values" />
                <data android:mimeType="text/plain" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="content" android:mimeType="text/csv" />
                <data android:scheme="content" android:mimeType="text/comma-separated-values" />
            </intent-filter>
```

- [ ] **Step 2: Handle the incoming Uri in MainActivity (modify)**

`MainActivity` reads a shared/viewed CSV `Uri` and imports it via a Hilt entry point. Add to `MainActivity`:
```kotlin
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import com.deckpuller.data.CollectionImporter
import com.deckpuller.data.repository.CollectionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import android.widget.Toast
```
Add inside the class:
```kotlin
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImportEntryPoint {
        fun collectionImporter(): CollectionImporter
        fun collectionRepository(): CollectionRepository
    }

    private fun handleIncomingCsv(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri == null) return
        val entry = EntryPointAccessors.fromApplication(applicationContext, ImportEntryPoint::class.java)
        lifecycleScope.launch {
            val result = runCatching {
                val text = entry.collectionImporter().readText(uri)
                entry.collectionRepository().importCsv(text, System.currentTimeMillis())
            }
            val msg = result.fold(
                onSuccess = { "Imported ${it.imported} cards" + if (it.skipped > 0) " · ${it.skipped} skipped" else "" },
                onFailure = { "Import failed: ${it.message}" },
            )
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }
```
Call it at the end of `onCreate` and add `onNewIntent`:
```kotlin
        handleIncomingCsv(intent)
```
```kotlin
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingCsv(intent)
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/deckpuller/MainActivity.kt
git commit -m "feat: accept ManaBox CSV via share/view intent

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phase C — Owned/missing on the pull screen

### Task 13: Add transient ownership fields to DeckCard

**Files:**
- Modify: `app/src/main/java/com/deckpuller/domain/model/Deck.kt`

- [ ] **Step 1: Extend `DeckCard` (modify)**

Add the import and two defaulted, non-persisted fields:
```kotlin
import com.deckpuller.domain.model.OwnedPrinting
```
Update `DeckCard`:
```kotlin
data class DeckCard(
    val id: Long,
    val scryfallId: String,
    val name: String,
    val typeLine: String,
    val imageUrl: String?,
    val requiredQty: Int,
    val pulledQty: Int,
    val category: String = "",
    // Transient ownership, populated in the ViewModel from the imported collection
    // (NOT persisted in Room). Defaults keep existing mappers/tests unchanged.
    val ownedQty: Int = 0,
    val ownedPrintings: List<OwnedPrinting> = emptyList(),
) {
    val isComplete: Boolean get() = pulledQty >= requiredQty
    val isOwned: Boolean get() = ownedQty >= requiredQty
}
```
(`OwnedPrinting` is in the same package, so the import is optional; include it for clarity if referenced.)

- [ ] **Step 2: Verify existing tests still pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.pull.PullViewModelTest"`
Expected: PASS (defaults mean no call sites break).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deckpuller/domain/model/Deck.kt
git commit -m "feat: add transient ownership fields to DeckCard

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 14: Enrich PullViewModel with ownership

**Files:**
- Modify: `app/src/main/java/com/deckpuller/ui/pull/PullViewModel.kt`
- Test: `app/src/test/java/com/deckpuller/ui/pull/PullViewModelTest.kt`

- [ ] **Step 1: Write the failing test (append to existing test class)**

Add a test that seeds a collection and asserts enrichment. (Match the existing test's construction of `PullViewModel`; it injects `DeckRepository` + `SavedStateHandle`. Add a mocked `CollectionRepository`.)
```kotlin
    @Test
    fun `marks cards owned from the collection`() = runTest {
        // deckRepository is the existing mock returning a deck with a "Sol Ring" card (requiredQty 1).
        coEvery { collectionRepository.observeOwnedByName() } returns kotlinx.coroutines.flow.flowOf(
            mapOf(
                "sol ring" to com.deckpuller.domain.model.OwnedInfo(
                    totalQty = 2,
                    printings = listOf(
                        com.deckpuller.domain.model.OwnedPrinting("LTC", "normal", 2, "EDH"),
                    ),
                ),
            ),
        )
        val vm = PullViewModel(deckRepository, collectionRepository, savedStateHandle)
        vm.state.test {
            var s = awaitItem()
            while (s == null || s.cards.isEmpty()) s = awaitItem()
            val sol = s.cards.first { it.name.equals("Sol Ring", ignoreCase = true) }
            assertEquals(2, sol.ownedQty)
            assertTrue(sol.isOwned)
            cancelAndIgnoreRemainingEvents()
        }
    }
```
(Declare `private val collectionRepository = mockk<CollectionRepository>(relaxed = true)` in the test class and, in `setUp`/default stubbing, `coEvery { collectionRepository.observeOwnedByName() } returns flowOf(emptyMap())` so existing tests keep passing.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.pull.PullViewModelTest"`
Expected: FAIL — constructor arity mismatch / `collectionRepository` unresolved.

- [ ] **Step 3: Update PullViewModel (modify)**

Add import + constructor param and fold ownership into the combine. Add:
```kotlin
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.domain.model.CardName
```
Wait — `CardName` is in `com.deckpuller.domain`. Use:
```kotlin
import com.deckpuller.domain.CardName
```
Change the constructor:
```kotlin
class PullViewModel @Inject constructor(
    private val repository: DeckRepository,
    private val collectionRepository: CollectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
```
Add `ownedCards`/`ownedTotalCards` to `PullUiState`:
```kotlin
    val ownedCards: Int = 0,
    val ownedTotalCards: Int = 0,
```
Replace the `state` definition to combine in ownership (4 sources):
```kotlin
    val state: StateFlow<PullUiState?> =
        combine(
            repository.observeDeck(deckId),
            query,
            filters,
            collectionRepository.observeOwnedByName(),
        ) { deck, q, f, owned ->
            deck?.let {
                val enriched = it.cards.map { card ->
                    val info = owned[CardName.normalize(card.name)]
                    card.copy(
                        ownedQty = info?.totalQty ?: 0,
                        ownedPrintings = info?.printings ?: emptyList(),
                    )
                }
                val byFilter = if (f.isEmpty()) enriched
                    else enriched.filter { card -> subtitleOf(card) in f }
                val filtered = if (q.isBlank()) byFilter
                    else byFilter.filter { card -> card.name.contains(q, ignoreCase = true) }
                PullUiState(
                    deckName = it.name,
                    cards = filtered.sortedBy { card -> card.name.lowercase() },
                    pulled = it.cards.sumOf { card -> card.pulledQty },
                    total = it.cards.sumOf { card -> card.requiredQty },
                    searchQuery = q,
                    subtitles = enriched.map(::subtitleOf)
                        .filter { s -> s.isNotBlank() && s != "Unknown" }
                        .distinct()
                        .sorted(),
                    activeFilters = f,
                    commander = enriched.firstOrNull { card ->
                        card.category.contains("Commander", ignoreCase = true)
                    },
                    ownedCards = enriched.count { card -> card.ownedQty >= card.requiredQty },
                    ownedTotalCards = enriched.size,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```
(`combine` with 4 flows is supported directly by the kotlinx.coroutines overload.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.pull.PullViewModelTest"`
Expected: PASS (all tests, including the new one).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/pull/PullViewModel.kt \
  app/src/test/java/com/deckpuller/ui/pull/PullViewModelTest.kt
git commit -m "feat: enrich pull cards with collection ownership

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 15: Owned/missing indicator in CardRow

**Files:**
- Modify: `app/src/main/java/com/deckpuller/ui/pull/CardRow.kt`
- Test: `app/src/test/java/com/deckpuller/ui/pull/CardRowTest.kt`

- [ ] **Step 1: Write the failing test (append)**

Add a Compose UI test asserting the owned/missing badge text appears. Match the existing `CardRowTest` setup (it uses `createComposeRule()` and renders `CardRow`). Add:
```kotlin
    @Test
    fun `shows owned badge when fully owned`() {
        val card = sampleCard().copy(requiredQty = 1, ownedQty = 2)
        composeRule.setContent { CardRow(card = card, onIncrement = {}, onDecrement = {}) }
        composeRule.onNodeWithText("Owned", substring = true).assertExists()
    }

    @Test
    fun `shows missing badge when not owned`() {
        val card = sampleCard().copy(requiredQty = 1, ownedQty = 0)
        composeRule.setContent { CardRow(card = card, onIncrement = {}, onDecrement = {}) }
        composeRule.onNodeWithText("Missing", substring = true).assertExists()
    }
```
(Reuse the existing test's helper for building a sample `DeckCard`; if none exists, add `private fun sampleCard() = DeckCard(id=1, scryfallId="s", name="Sol Ring", typeLine="Artifact", imageUrl=null, requiredQty=1, pulledQty=0)`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.pull.CardRowTest"`
Expected: FAIL — no "Owned"/"Missing" node.

- [ ] **Step 3: Add the indicator (modify CardRow)**

Inside `CardRow`'s layout, where the card's subtitle/name column is rendered, add a small badge driven by ownership. Only show when the deck has any ownership data context — show "Missing" only when a collection is imported. Since the row itself doesn't know whether a collection exists, gate on `card.ownedQty > 0 || <collection-present>`. Simplest correct rule: always render based on `card.isOwned` when ownership info is meaningful. Use:
```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
```
Add a composable used within the row's text column:
```kotlin
@Composable
private fun OwnershipBadge(card: DeckCard) {
    // Hidden entirely when there's no ownership signal (no collection imported -> ownedQty 0
    // for every card; we only badge "Owned" positively, and "Missing" when the card has a
    // known shortfall against an imported collection).
    val owned = card.ownedQty
    if (owned <= 0 && card.requiredQty <= 0) return
    val (label, bg) = if (card.isOwned) {
        "Owned${if (owned > card.requiredQty) " ($owned)" else ""}" to Color(0xFF2E7D32)
    } else {
        "Missing" to Color(0xFFB26A00)
    }
    Text(
        text = label,
        color = Color.White,
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
    val detail = card.ownedPrintings.takeIf { it.isNotEmpty() }?.joinToString(" · ") {
        "${it.quantity}× ${it.setCode}${if (it.finish != "normal") " (${it.finish})" else ""}"
    }
    if (detail != null) {
        Text("have: $detail", modifier = Modifier.padding(top = 2.dp))
    }
}
```
Then call `OwnershipBadge(card)` inside the row's text `Column`.

> **Ownership-gating note:** because we want "Missing" to appear only when a collection is imported, pass a `collectionPresent: Boolean` down if the simple rule above produces "Missing" badges with no collection. The cleanest version: add `collectionPresent: Boolean = false` to `CardRow` and to `PullScreen`'s row call, sourced from `state.ownedTotalCards > 0 && <importedAt != null>`. If you add the param, render the badge only when `collectionPresent`. Implement this gated version; update the two new tests to pass `collectionPresent = true`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.pull.CardRowTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/pull/CardRow.kt \
  app/src/test/java/com/deckpuller/ui/pull/CardRowTest.kt
git commit -m "feat: show owned/missing badge on pull card rows

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phase D — Shopping list + store buy links

### Task 16: Scryfall prices DTO + repository method

**Files:**
- Modify: `app/src/main/java/com/deckpuller/data/remote/dto/ScryfallDto.kt`
- Modify: `app/src/main/java/com/deckpuller/data/repository/DeckRepository.kt`
- Modify: `app/src/main/java/com/deckpuller/data/repository/DefaultDeckRepository.kt`
- Test: `app/src/test/java/com/deckpuller/data/remote/DtoParsingTest.kt`

- [ ] **Step 1: Write the failing DTO test (append to DtoParsingTest)**

```kotlin
    @Test
    fun `parses scryfall prices`() {
        val payload = """
        {
          "data": [
            { "id": "p1", "name": "Sol Ring",
              "prices": { "usd": "1.49", "usd_foil": "5.00" } }
          ],
          "not_found": []
        }
        """.trimIndent()
        val card = json.decodeFromString<ScryfallCollectionResponse>(payload).data.single()
        assertEquals(1.49, card.prices?.usd?.toDouble()!!, 0.001)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.remote.DtoParsingTest"`
Expected: FAIL — `prices` unresolved.

- [ ] **Step 3: Add the prices field (modify ScryfallDto)**

Add to `ScryfallCardDto`:
```kotlin
    val prices: ScryfallPrices? = null,
```
And the new type:
```kotlin
@Serializable
data class ScryfallPrices(
    val usd: String? = null,
    @SerialName("usd_foil") val usdFoil: String? = null,
)
```

- [ ] **Step 4: Add `prices()` to DeckRepository (modify interface)**

```kotlin
    /** Cheapest USD price per scryfallId (front of the printing). Missing/failed -> null. */
    suspend fun prices(scryfallIds: List<String>): Map<String, Double?>
```

- [ ] **Step 5: Implement in DefaultDeckRepository (modify)**

Add, reusing the existing batched/throttled fetch:
```kotlin
    override suspend fun prices(scryfallIds: List<String>): Map<String, Double?> =
        runCatching {
            fetchScryfall(scryfallIds).mapValues { (_, dto) -> dto.prices?.usd?.toDoubleOrNull() }
        }.getOrDefault(emptyMap())
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.remote.DtoParsingTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/remote/dto/ScryfallDto.kt \
  app/src/main/java/com/deckpuller/data/repository/DeckRepository.kt \
  app/src/main/java/com/deckpuller/data/repository/DefaultDeckRepository.kt \
  app/src/test/java/com/deckpuller/data/remote/DtoParsingTest.kt
git commit -m "feat: expose Scryfall USD prices via repository

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 17: StoreCartLinks (pure buy-link builder)

**Files:**
- Create: `app/src/main/java/com/deckpuller/domain/StoreCartLinks.kt`
- Test: `app/src/test/java/com/deckpuller/domain/StoreCartLinksTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.deckpuller.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCartLinksTest {

    private val items = listOf(
        StoreCartLinks.BuyItem(name = "Sol Ring", quantity = 1),
        StoreCartLinks.BuyItem(name = "Rhystic Study", quantity = 2),
    )

    @Test
    fun `clipboard text is one line per card with quantity`() {
        assertEquals("1 Sol Ring\n2 Rhystic Study", StoreCartLinks.clipboardText(items))
    }

    @Test
    fun `tcgplayer url encodes the list with double-pipe separator`() {
        val url = StoreCartLinks.tcgPlayerUrl(items)
        assertTrue(url.startsWith("https://www.tcgplayer.com/massentry?productline=Magic&c="))
        // "1 Sol Ring||2 Rhystic Study" url-encoded
        assertTrue(url.contains("1%20Sol%20Ring%7C%7C2%20Rhystic%20Study"))
    }

    @Test
    fun `card kingdom builder url points at the builder with the list`() {
        val url = StoreCartLinks.cardKingdomUrl(items)
        assertTrue(url.startsWith("https://www.cardkingdom.com/builder"))
    }

    @Test
    fun `tcgplayer falls back to plain massentry when list exceeds url budget`() {
        val huge = (1..500).map { StoreCartLinks.BuyItem("Card Number $it With A Long Name", 1) }
        val url = StoreCartLinks.tcgPlayerUrl(huge)
        assertEquals("https://www.tcgplayer.com/massentry?productline=Magic", url)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.domain.StoreCartLinksTest"`
Expected: FAIL — `StoreCartLinks` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.deckpuller.domain

import java.net.URLEncoder

/** Builds bulk-add web links and clipboard text for buying a list of cards. */
object StoreCartLinks {

    data class BuyItem(val name: String, val quantity: Int)

    /** Conservative URL length budget; beyond this we drop the pre-filled list. */
    private const val MAX_URL_LENGTH = 6000

    fun clipboardText(items: List<BuyItem>): String =
        items.joinToString("\n") { "${it.quantity} ${it.name}" }

    fun tcgPlayerUrl(items: List<BuyItem>): String {
        val base = "https://www.tcgplayer.com/massentry?productline=Magic"
        val list = items.joinToString("||") { "${it.quantity} ${it.name}" }
        val url = base + "&c=" + encode(list)
        return if (url.length <= MAX_URL_LENGTH) url else base
    }

    fun cardKingdomUrl(items: List<BuyItem>): String {
        // Card Kingdom's pre-fill mechanism is unconfirmed (GET vs POST). We open the
        // builder; the screen also copies the list to the clipboard as a guaranteed fallback.
        val base = "https://www.cardkingdom.com/builder"
        val list = items.joinToString("||") { "${it.quantity} ${it.name}" }
        val url = base + "?c=" + encode(list)
        return if (url.length <= MAX_URL_LENGTH) url else base
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.domain.StoreCartLinksTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/domain/StoreCartLinks.kt \
  app/src/test/java/com/deckpuller/domain/StoreCartLinksTest.kt
git commit -m "feat: build TCGplayer/Card Kingdom buy links and clipboard text

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 18: ShoppingListViewModel

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/shopping/ShoppingListViewModel.kt`
- Test: `app/src/test/java/com/deckpuller/ui/shopping/ShoppingListViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.deckpuller.ui.shopping

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckCard
import com.deckpuller.domain.model.OwnedInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModelTest {

    private val deckRepo = mockk<DeckRepository>(relaxed = true)
    private val collectionRepo = mockk<CollectionRepository>(relaxed = true)

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun card(name: String, required: Int, scryfallId: String) = DeckCard(
        id = name.hashCode().toLong(), scryfallId = scryfallId, name = name,
        typeLine = "x", imageUrl = null, requiredQty = required, pulledQty = 0,
    )

    @Test
    fun `lists only missing cards with need quantity and prices`() = runTest {
        coEvery { deckRepo.observeDeck(1L) } returns flowOf(
            Deck(
                name = "D",
                cards = listOf(card("Sol Ring", 1, "s-sol"), card("Rhystic Study", 1, "s-rhy")),
            ),
        )
        coEvery { collectionRepo.observeOwnedByName() } returns flowOf(
            mapOf("sol ring" to OwnedInfo(totalQty = 1, printings = emptyList())),
        )
        coEvery { deckRepo.prices(listOf("s-rhy")) } returns mapOf("s-rhy" to 30.0)

        val vm = ShoppingListViewModel(deckRepo, collectionRepo, SavedStateHandle(mapOf("deckId" to 1L)))
        vm.state.test {
            var s = awaitItem()
            while (s == null || s.items.isEmpty()) s = awaitItem()
            assertEquals(1, s.items.size)
            assertEquals("Rhystic Study", s.items.single().name)
            assertEquals(1, s.items.single().need)
            assertEquals(30.0, s.items.single().unitPrice!!, 0.001)
            assertEquals(30.0, s.totalPrice, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.shopping.ShoppingListViewModelTest"`
Expected: FAIL — `ShoppingListViewModel` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.deckpuller.ui.shopping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.CollectionRepository
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.CardName
import com.deckpuller.domain.StoreCartLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ShoppingItem(
    val name: String,
    val scryfallId: String,
    val need: Int,
    val unitPrice: Double?,
) {
    val lineTotal: Double get() = (unitPrice ?: 0.0) * need
}

data class ShoppingUiState(
    val deckName: String = "",
    val items: List<ShoppingItem> = emptyList(),
    val totalPrice: Double = 0.0,
) {
    fun buyItems(): List<StoreCartLinks.BuyItem> =
        items.map { StoreCartLinks.BuyItem(it.name, it.need) }
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val collectionRepository: CollectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle["deckId"])
    private val prices = MutableStateFlow<Map<String, Double?>>(emptyMap())

    val state: StateFlow<ShoppingUiState?> =
        combine(
            deckRepository.observeDeck(deckId),
            collectionRepository.observeOwnedByName(),
            prices,
        ) { deck, owned, priceMap ->
            deck?.let {
                val missing = it.cards.mapNotNull { card ->
                    val ownedQty = owned[CardName.normalize(card.name)]?.totalQty ?: 0
                    val need = card.requiredQty - ownedQty
                    if (need <= 0) null
                    else ShoppingItem(
                        name = card.name,
                        scryfallId = card.scryfallId,
                        need = need,
                        unitPrice = priceMap[card.scryfallId],
                    )
                }.sortedBy { item -> item.name.lowercase() }
                ShoppingUiState(
                    deckName = it.name,
                    items = missing,
                    totalPrice = missing.sumOf { item -> item.lineTotal },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Fetch Scryfall prices for the current missing set; call from the screen on first load. */
    suspend fun loadPrices() {
        val deck = deckRepository.observeDeck(deckId)
        // Collect a single snapshot of the deck to know which scryfallIds to price.
        kotlinx.coroutines.flow.first(deck)?.let { d ->
            prices.value = deckRepository.prices(d.cards.map { it.scryfallId })
        }
    }
}
```
> Note: `kotlinx.coroutines.flow.first` is used as a function here; if simpler, import `kotlinx.coroutines.flow.first` and call `deck.first()`. Either form is acceptable — keep it compiling.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.shopping.ShoppingListViewModelTest"`
Expected: PASS.
(The test seeds `prices` indirectly via `loadPrices`? No — the test stubs `deckRepo.prices`. Adjust `loadPrices` call: the test should call `vm.loadPrices()` before asserting, OR the ViewModel should call it in `init`. Implement `init { viewModelScope.launch { loadPrices() } }` so prices load automatically; then the test's `prices` stub is consumed. Add the `init` block and the `launch` import.)

- [ ] **Step 5: Add automatic price load (modify)**

Add to the class:
```kotlin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
```
```kotlin
    init {
        viewModelScope.launch { loadPrices() }
    }
```
And simplify `loadPrices`:
```kotlin
    private suspend fun loadPrices() {
        val deck = deckRepository.observeDeck(deckId).first() ?: return
        prices.value = deckRepository.prices(deck.cards.map { it.scryfallId })
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.shopping.ShoppingListViewModelTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/shopping/ShoppingListViewModel.kt \
  app/src/test/java/com/deckpuller/ui/shopping/ShoppingListViewModelTest.kt
git commit -m "feat: add ShoppingListViewModel (missing cards + Scryfall prices)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 19: ShoppingListScreen + buy actions

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/shopping/ShoppingListScreen.kt`

- [ ] **Step 1: Create the screen**

```kotlin
package com.deckpuller.ui.shopping

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckpuller.domain.StoreCartLinks

@Composable
fun ShoppingListRoute(onBack: () -> Unit) {
    val viewModel: ShoppingListViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShoppingListScreen(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(state: ShoppingUiState?, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val items = state?.buyItems().orEmpty()

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Missing cards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (state == null || state.items.isEmpty()) {
                Text("Nothing to buy — you own everything in this deck (or no collection imported).")
                return@Column
            }

            Text(
                "${state.items.size} cards · ~$${"%.2f".format(state.totalPrice)} (Scryfall)",
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { open(StoreCartLinks.tcgPlayerUrl(items)) }) { Text("TCGplayer") }
                Button(onClick = {
                    // Always copy as the guaranteed fallback, then open the builder.
                    clipboard.setText(AnnotatedString(StoreCartLinks.clipboardText(items)))
                    open(StoreCartLinks.cardKingdomUrl(items))
                }) { Text("Card Kingdom") }
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(StoreCartLinks.clipboardText(items)))
                }) { Text("Copy") }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.scryfallId + it.name }) { item ->
                    ListItem(
                        headlineContent = { Text("${item.need}× ${item.name}") },
                        trailingContent = {
                            Text(item.unitPrice?.let { "$${"%.2f".format(it * item.need)}" } ?: "—")
                        },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/shopping/ShoppingListScreen.kt
git commit -m "feat: add ShoppingListScreen with TCGplayer/Card Kingdom/Copy actions

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 20: Wire shopping list into navigation + pull screen

**Files:**
- Modify: `app/src/main/java/com/deckpuller/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/deckpuller/ui/pull/PullScreen.kt`

- [ ] **Step 1: Add the route (modify AppRoot)**

Add import + constant:
```kotlin
import com.deckpuller.ui.shopping.ShoppingListRoute
```
```kotlin
private const val SHOPPING = "shopping"
```
Add the composable with the `deckId` arg:
```kotlin
        composable(
            route = "$SHOPPING/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
        ) {
            ShoppingListRoute(onBack = { navController.popBackStack() })
        }
```
Pass a navigation lambda into `PullRoute`:
```kotlin
        composable(
            route = "$PULL/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong("deckId") ?: 0L
            PullRoute(
                onBack = { navController.popBackStack() },
                onShoppingList = { navController.navigate("$SHOPPING/$deckId") },
            )
        }
```

- [ ] **Step 2: Add `onShoppingList` to PullRoute/PullScreen + app-bar action (modify)**

In `PullScreen.kt`, add `onShoppingList: () -> Unit` to both `PullRoute` and `PullScreen` signatures, thread it through, and add a top-app-bar action:
```kotlin
import androidx.compose.material.icons.filled.ShoppingCart
```
```kotlin
                    IconButton(onClick = onShoppingList) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Buy missing cards")
                    }
```
(Place it in the existing pull-screen `TopAppBar` actions. Update any `@Preview`/test invocations of `PullScreen` to pass `onShoppingList = {}`.)

- [ ] **Step 3: Verify it compiles + run pull tests**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.pull.*"`
Expected: BUILD SUCCESSFUL; pull tests PASS (fix any preview/test call sites that need `onShoppingList = {}`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/AppRoot.kt \
  app/src/main/java/com/deckpuller/ui/pull/PullScreen.kt \
  app/src/test/java/com/deckpuller/ui/pull/PullScreenTest.kt
git commit -m "feat: open shopping list from the pull screen

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phase E — Final verification

### Task 21: Full test + build pass

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Assemble a debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual smoke test (device/emulator)**

Verify, in order:
1. Open the app → Collection icon in deck-list top bar → Collection screen shows empty state.
2. Tap **Import ManaBox CSV**, pick `ManaBox_Collection_1.csv` → snackbar "Imported N cards"; list populates; "N cards · imported <time>" shows.
3. (Optional) From a file manager, Share the CSV → DeckPuller → Toast confirms import.
4. Open a deck → pull screen shows green **Owned**/amber **Missing** badges and "have: …" detail.
5. Tap the cart icon → shopping list lists only missing cards with prices and a total.
6. Tap **TCGplayer** → browser opens Mass Entry pre-filled. Tap **Card Kingdom** → builder opens + list copied. Tap **Copy** → list on clipboard.

- [ ] **Step 4: Final commit (if any test/preview fixups were needed)**

```bash
git add -A
git commit -m "test: fixups for collection + buy integration

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- ManaBox CSV import (share + picker) → Tasks 2, 6, 7, 10, 12. ✅
- Name-based matching with printing detail → Tasks 1, 5, 14, 15. ✅
- Single collection, replace-on-import + freshness → Tasks 3, 4, 7. ✅
- Owned/missing on pull screen → Tasks 13, 14, 15. ✅
- Collection browser → Tasks 9, 10, 11. ✅
- Missing-cards shopping list + Scryfall prices → Tasks 16, 18, 19. ✅
- TCGplayer / Card Kingdom / Copy buy actions (with URL-length + POST fallbacks) → Tasks 17, 19. ✅
- Navigation + DI → Tasks 8, 11, 20. ✅
- Migration 3→4 → Tasks 3, 8. ✅
- Test matrix → Tasks 1, 2, 3, 7, 9, 14, 15, 16, 17, 18. ✅

**Out-of-scope (correctly absent):** auto-fill pulled-from-owned, merge sync, multiple snapshots, affiliate tags, exact-printing buy lists, CSV-sourced prices.

**Type consistency check:** `CardName.normalize` (Task 1) used identically in Tasks 7, 14, 18. `OwnedInfo`/`OwnedPrinting` (Task 5) used in Tasks 7, 14, 15, 18. `CollectionRepository.observeOwnedByName(): Flow<Map<String, OwnedInfo>>` consistent across Tasks 7, 14, 18. `StoreCartLinks.BuyItem` consistent across Tasks 17, 18, 19. `ShoppingItem.need`/`unitPrice` consistent in Tasks 18, 19.
