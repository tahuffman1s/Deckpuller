# Multi-deck DeckPuller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn DeckPuller into a multi-deck app with saved per-deck progress, pull-screen controls (search/refresh/reset/add-deck), and import of a user's public Archidekt decks by username.

**Architecture:** Room moves from a single fixed-id deck to many auto-id decks (each storing its `archidektId` + `sourceUrl`). The repository gains refresh (progress-preserving), reset, delete, multi-deck observation, and username search. Jetpack Navigation-Compose replaces the boolean `AppRoot` toggle with `deckList` → `pull/{deckId}` / `addDeck` destinations, each in a Material3 `Scaffold`/`TopAppBar` that also handles window insets. The Archidekt username is stored in DataStore.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Navigation-Compose, Room, Hilt, Retrofit + kotlinx.serialization, Coil, DataStore Preferences, Robolectric/JUnit4 + Turbine.

**Verified facts (do not re-derive):**
- Deck-list endpoint: `GET https://archidekt.com/api/decks/v3/?owner={username}&ownerexact=true&orderBy=-updatedAt&pageSize=50`.
- Response shape: `{ "count": Int, "next": String?, "results": [ { "id": Long, "name": String, "size": Int, "featured": String, "private": Bool, "owner": { "username": String } } ] }`.
- Single deck import (`api/decks/{id}/`) and Scryfall collection fetch already work and are unchanged in behavior.

---

## File structure

**Modify:**
- `gradle/libs.versions.toml` — add navigation-compose, datastore-preferences libs.
- `app/build.gradle.kts` — add the two implementation deps.
- `app/src/main/java/com/deckpuller/data/local/entity/DeckEntity.kt` — auto-id, `archidektId`, `sourceUrl`; drop `CURRENT_DECK_ID`.
- `app/src/main/java/com/deckpuller/data/local/DeckDao.kt` — multi-deck queries.
- `app/src/main/java/com/deckpuller/data/local/AppDatabase.kt` — bump version to 2.
- `app/src/main/java/com/deckpuller/di/DataModule.kt` — destructive migration on the DB builder.
- `app/src/main/java/com/deckpuller/data/remote/ArchidektApi.kt` — regular interface + `searchByOwner`.
- `app/src/main/java/com/deckpuller/data/remote/dto/ArchidektDto.kt` — add deck-list DTOs.
- `app/src/main/java/com/deckpuller/data/repository/DeckRepository.kt` + `DefaultDeckRepository.kt` — new surface.
- `app/src/main/java/com/deckpuller/ui/AppRoot.kt` — NavHost.
- `app/src/main/java/com/deckpuller/MainActivity.kt` — drop the `safeDrawing` Box wrapper (Scaffolds handle insets now); keep `enableEdgeToEdge()`.
- `app/src/main/java/com/deckpuller/ui/pull/PullViewModel.kt` + `PullScreen.kt` — deckId arg, search, refresh, reset, top bar.
- `app/src/main/java/com/deckpuller/ui/importdeck/ImportViewModel.kt` — import returns id, username browse.
- Tests: `DeckDaoTest`, `DefaultDeckRepositoryTest`, `PullViewModelTest`, `ImportViewModelTest`, `ImportScreenTest`, `DtoParsingTest`.

**Create:**
- `app/src/main/java/com/deckpuller/domain/model/DeckSummary.kt`
- `app/src/main/java/com/deckpuller/data/prefs/UserPreferences.kt`
- `app/src/main/java/com/deckpuller/ui/decklist/DeckListViewModel.kt` + `DeckListScreen.kt`
- `app/src/main/java/com/deckpuller/ui/importdeck/AddDeckScreen.kt` (replaces `ImportScreen.kt`)
- Tests: `DeckListViewModelTest`, `DeckListScreenTest`, `AddDeckScreenTest`, `RefreshProgressTest` (folded into `DefaultDeckRepositoryTest`).

**Delete:**
- `app/src/main/java/com/deckpuller/ui/MainViewModel.kt` + `app/src/test/java/com/deckpuller/ui/MainViewModelTest.kt` (deck list is the new home; no `hasDeck` gate).
- `app/src/main/java/com/deckpuller/ui/importdeck/ImportScreen.kt` (renamed to `AddDeckScreen.kt`).

---

## Task 1: Add dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add versions and libraries to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
navigationCompose = "2.8.4"
datastore = "1.1.1"
```

Under `[libraries]` add:

```toml
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 2: Add the implementation dependencies**

In `app/build.gradle.kts`, in the `dependencies { }` block after `implementation(libs.hilt.navigation.compose)` add:

```kotlin
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 3: Verify it resolves**

Run: `./gradlew :app:help`
Expected: `BUILD SUCCESSFUL` (dependency coordinates resolve).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add navigation-compose and datastore-preferences"
```

---

## Task 2: Multi-deck Room entities + DAO

**Files:**
- Modify: `app/src/main/java/com/deckpuller/data/local/entity/DeckEntity.kt`
- Modify: `app/src/main/java/com/deckpuller/data/local/DeckDao.kt`
- Test: `app/src/test/java/com/deckpuller/data/local/DeckDaoTest.kt`

- [ ] **Step 1: Rewrite the failing DAO test for multi-deck**

Replace the entire contents of `DeckDaoTest.kt` with:

```kotlin
package com.deckpuller.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DeckDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.deckDao()
    }

    @After
    fun tearDown() = db.close()

    private fun deck(name: String, archidektId: String) =
        DeckEntity(name = name, archidektId = archidektId, sourceUrl = "url/$archidektId", importedAt = 1L)

    private fun card(scryfallId: String, required: Int, pulled: Int = 0) = CardEntity(
        deckId = 0, scryfallId = scryfallId, name = scryfallId,
        typeLine = "Creature", imageUrl = null, requiredQty = required, pulledQty = pulled,
    )

    @Test
    fun `insertDeckWithCards stores deck and returns its id`() = runTest {
        val id = dao.insertDeckWithCards(deck("A", "1"), listOf(card("forest", 4), card("sol", 1)))
        val stored = dao.observeDeck(id).first()!!
        assertEquals("A", stored.deck.name)
        assertEquals(2, stored.cards.size)
        assertEquals(id, stored.cards.first().deckId)
    }

    @Test
    fun `observeDecks lists multiple decks`() = runTest {
        dao.insertDeckWithCards(deck("A", "1"), listOf(card("x", 1)))
        dao.insertDeckWithCards(deck("B", "2"), listOf(card("y", 1)))
        assertEquals(2, dao.observeDecks().first().size)
    }

    @Test
    fun `deleteDeck removes one deck and cascades its cards`() = runTest {
        val a = dao.insertDeckWithCards(deck("A", "1"), listOf(card("x", 1)))
        val b = dao.insertDeckWithCards(deck("B", "2"), listOf(card("y", 1)))
        dao.deleteDeck(a)
        assertNull(dao.observeDeck(a).first())
        assertEquals(1, dao.observeDecks().first().size)
        assertEquals("B", dao.observeDeck(b).first()!!.deck.name)
    }

    @Test
    fun `resetProgress zeroes only the target deck`() = runTest {
        val a = dao.insertDeckWithCards(deck("A", "1"), listOf(card("x", 4, pulled = 3)))
        val b = dao.insertDeckWithCards(deck("B", "2"), listOf(card("y", 4, pulled = 2)))
        dao.resetProgress(a)
        assertEquals(0, dao.observeDeck(a).first()!!.cards.single().pulledQty)
        assertEquals(2, dao.observeDeck(b).first()!!.cards.single().pulledQty)
    }

    @Test
    fun `updatePulled changes a single card`() = runTest {
        val id = dao.insertDeckWithCards(deck("A", "1"), listOf(card("x", 4)))
        val cardId = dao.observeDeck(id).first()!!.cards.single().id
        dao.updatePulled(cardId, 3)
        assertEquals(3, dao.observeDeck(id).first()!!.cards.single().pulledQty)
    }
}
```

- [ ] **Step 2: Run to verify it fails to compile**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.local.DeckDaoTest"`
Expected: FAIL — unresolved references (`archidektId`, `insertDeckWithCards`, `observeDecks`, `resetProgress`, `observeDeck(id)`).

- [ ] **Step 3: Update DeckEntity**

Replace the entire contents of `DeckEntity.kt` with:

```kotlin
package com.deckpuller.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archidektId: String,
    val sourceUrl: String,
    val importedAt: Long,
)
```

- [ ] **Step 4: Rewrite the DAO**

Replace the entire contents of `DeckDao.kt` with:

```kotlin
package com.deckpuller.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import com.deckpuller.data.local.entity.DeckWithCards
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Transaction
    @Query("SELECT * FROM decks ORDER BY importedAt DESC")
    fun observeDecks(): Flow<List<DeckWithCards>>

    @Transaction
    @Query("SELECT * FROM decks WHERE id = :id")
    fun observeDeck(id: Long): Flow<DeckWithCards?>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun deckById(id: Long): DeckEntity?

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    suspend fun cardsForDeck(deckId: Long): List<CardEntity>

    @Insert
    suspend fun insertDeck(deck: DeckEntity): Long

    @Insert
    suspend fun insertCards(cards: List<CardEntity>)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsForDeck(deckId: Long)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: Long)

    @Query("UPDATE cards SET pulledQty = :pulled WHERE id = :cardId")
    suspend fun updatePulled(cardId: Long, pulled: Int)

    @Query("UPDATE cards SET pulledQty = 0 WHERE deckId = :deckId")
    suspend fun resetProgress(deckId: Long)

    @Query("UPDATE decks SET name = :name WHERE id = :id")
    suspend fun updateDeckName(id: Long, name: String)

    /** Insert a deck and its cards atomically; returns the new deck id. */
    @Transaction
    suspend fun insertDeckWithCards(deck: DeckEntity, cards: List<CardEntity>): Long {
        val deckId = insertDeck(deck)
        insertCards(cards.map { it.copy(deckId = deckId) })
        return deckId
    }

    /** Replace a deck's cards (used by refresh); name is updated too. */
    @Transaction
    suspend fun replaceCards(deckId: Long, name: String, cards: List<CardEntity>) {
        deleteCardsForDeck(deckId)
        insertCards(cards.map { it.copy(deckId = deckId) })
        updateDeckName(deckId, name)
    }
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.local.DeckDaoTest"`
Expected: PASS (5 tests). NOTE: other modules won't compile yet (repository still references removed symbols) — that's fixed in Task 3 & 5. If the test task fails to compile because of `DefaultDeckRepository`, proceed to Task 3 then re-run.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/local app/src/test/java/com/deckpuller/data/local/DeckDaoTest.kt
git commit -m "feat: multi-deck Room entities and DAO"
```

---

## Task 3: Bump DB version + destructive migration

**Files:**
- Modify: `app/src/main/java/com/deckpuller/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/deckpuller/di/DataModule.kt`

- [ ] **Step 1: Bump the schema version**

In `AppDatabase.kt`, change `version = 1` to `version = 2`.

- [ ] **Step 2: Allow destructive migration**

In `DataModule.kt`, change the `provideDatabase` body to:

```kotlin
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "deckpuller.db")
                .fallbackToDestructiveMigration()
                .build()
```

- [ ] **Step 3: Verify compile (data layer)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: may still FAIL on `DefaultDeckRepository` (fixed in Task 5). The `AppDatabase`/`DataModule` edits themselves must show no new errors. Proceed.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/local/AppDatabase.kt app/src/main/java/com/deckpuller/di/DataModule.kt
git commit -m "feat: bump Room to v2 with destructive migration"
```

---

## Task 4: Archidekt deck-list DTOs + search API method

**Files:**
- Modify: `app/src/main/java/com/deckpuller/data/remote/dto/ArchidektDto.kt`
- Modify: `app/src/main/java/com/deckpuller/data/remote/ArchidektApi.kt`
- Create: `app/src/main/java/com/deckpuller/domain/model/DeckSummary.kt`
- Test: `app/src/test/java/com/deckpuller/data/remote/DtoParsingTest.kt`

- [ ] **Step 1: Write the failing DTO parsing test**

Append this test to the existing `DtoParsingTest` class body (inside the class):

```kotlin
    @Test
    fun `parses archidekt deck list response`() {
        val raw = """
            {"count":2,"next":null,"results":[
              {"id":111,"name":"Goblins","size":100,"featured":"http://img/a.jpg","private":false,"owner":{"username":"me"}},
              {"id":222,"name":"Elves","size":99,"featured":"","private":false,"owner":{"username":"me"}}
            ]}
        """.trimIndent()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val dto = json.decodeFromString(
            com.deckpuller.data.remote.dto.ArchidektDeckListDto.serializer(), raw,
        )
        assertEquals(2, dto.results.size)
        assertEquals(111L, dto.results[0].id)
        assertEquals("Goblins", dto.results[0].name)
        assertEquals(100, dto.results[0].size)
        assertEquals("http://img/a.jpg", dto.results[0].featured)
    }
```

If `DtoParsingTest` already imports `org.junit.Assert.assertEquals`, do not re-import.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.remote.DtoParsingTest"`
Expected: FAIL — `ArchidektDeckListDto` unresolved.

- [ ] **Step 3: Add the deck-list DTOs**

Append to `ArchidektDto.kt`:

```kotlin
@Serializable
data class ArchidektDeckListDto(
    val results: List<ArchidektDeckSummaryDto> = emptyList(),
)

@Serializable
data class ArchidektDeckSummaryDto(
    val id: Long,
    val name: String,
    val size: Int = 0,
    val featured: String = "",
)
```

- [ ] **Step 4: Add the search method (convert to a regular interface)**

Replace the entire contents of `ArchidektApi.kt` with:

```kotlin
package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ArchidektDeckDto
import com.deckpuller.data.remote.dto.ArchidektDeckListDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ArchidektApi {
    @GET("decks/{id}/")
    suspend fun getDeck(@Path("id") deckId: String): ArchidektDeckDto

    @GET("decks/v3/")
    suspend fun searchByOwner(
        @Query("owner") owner: String,
        @Query("ownerexact") exact: Boolean = true,
        @Query("orderBy") orderBy: String = "-updatedAt",
        @Query("pageSize") pageSize: Int = 50,
    ): ArchidektDeckListDto
}
```

- [ ] **Step 5: Create the DeckSummary domain model**

Create `DeckSummary.kt`:

```kotlin
package com.deckpuller.domain.model

data class DeckSummary(
    val archidektId: String,
    val name: String,
    val cardCount: Int,
    val thumbnailUrl: String?,
)
```

- [ ] **Step 6: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.remote.DtoParsingTest"`
Expected: PASS (may still fail to compile due to repository — fixed next task; if so, do Task 5 then re-run).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/remote app/src/main/java/com/deckpuller/domain/model/DeckSummary.kt app/src/test/java/com/deckpuller/data/remote/DtoParsingTest.kt
git commit -m "feat: archidekt deck-list DTOs and searchByOwner API"
```

---

## Task 5: Repository — multi-deck, refresh, reset, delete, search

**Files:**
- Modify: `app/src/main/java/com/deckpuller/data/repository/DeckRepository.kt`
- Modify: `app/src/main/java/com/deckpuller/data/repository/DefaultDeckRepository.kt`
- Test: `app/src/test/java/com/deckpuller/data/repository/DefaultDeckRepositoryTest.kt`

- [ ] **Step 1: Rewrite the repository test**

Replace the entire contents of `DefaultDeckRepositoryTest.kt` with:

```kotlin
package com.deckpuller.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.image.ImagePrefetcher
import com.deckpuller.data.local.AppDatabase
import com.deckpuller.data.remote.ArchidektApi
import com.deckpuller.data.remote.ScryfallApi
import com.deckpuller.data.remote.dto.ArchidektCardDetailDto
import com.deckpuller.data.remote.dto.ArchidektCardDto
import com.deckpuller.data.remote.dto.ArchidektDeckDto
import com.deckpuller.data.remote.dto.ArchidektDeckListDto
import com.deckpuller.data.remote.dto.ArchidektDeckSummaryDto
import com.deckpuller.data.remote.dto.ArchidektOracleCardDto
import com.deckpuller.data.remote.dto.ScryfallCardDto
import com.deckpuller.data.remote.dto.ScryfallCollectionResponse
import com.deckpuller.data.remote.dto.ScryfallImageUris
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultDeckRepositoryTest {

    private lateinit var db: AppDatabase
    private val prefetched = mutableListOf<String>()
    private val fakePrefetcher = ImagePrefetcher { prefetched.addAll(it) }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun archidektCard(uid: String, name: String, qty: Int) = ArchidektCardDto(
        quantity = qty,
        card = ArchidektCardDetailDto(uid = uid, oracleCard = ArchidektOracleCardDto(name)),
    )

    private fun scryfallCard(id: String, name: String, type: String) = ScryfallCardDto(
        id = id, name = name, typeLine = type,
        imageUris = ScryfallImageUris(small = "$id-s.jpg", normal = "$id-n.jpg"),
    )

    /** Fake API returning a fixed deck for getDeck and a fixed list for searchByOwner. */
    private class FakeArchidektApi(
        val deck: (String) -> ArchidektDeckDto = { error("getDeck not stubbed") },
        val list: ArchidektDeckListDto = ArchidektDeckListDto(),
    ) : ArchidektApi {
        override suspend fun getDeck(deckId: String) = deck(deckId)
        override suspend fun searchByOwner(owner: String, exact: Boolean, orderBy: String, pageSize: Int) = list
    }

    private fun repo(archidekt: ArchidektApi, scryfall: ScryfallApi) =
        DefaultDeckRepository(archidekt, scryfall, db.deckDao(), fakePrefetcher)

    @Test
    fun `importDeck throws on bad url`() = runTest {
        val r = repo(FakeArchidektApi(), { fail("unused"); error("") })
        try {
            r.importDeck("https://example.com/not-a-deck")
            fail("expected InvalidDeckUrlException")
        } catch (e: InvalidDeckUrlException) { /* expected */ }
    }

    @Test
    fun `importDeck stores deck with archidektId and returns its id`() = runTest {
        val archidekt = FakeArchidektApi(deck = {
            ArchidektDeckDto("Test Deck", listOf(archidektCard("uid-1", "Forest", 4)))
        })
        val r = repo(archidekt, { ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land"))) })

        val id = r.importDeck("https://archidekt.com/decks/999/test")

        val deck = r.observeDeck(id).first()!!
        assertEquals("Test Deck", deck.name)
        assertEquals(4, deck.cards.single().requiredQty)
        assertTrue(prefetched.contains("uid-1-n.jpg"))
    }

    @Test
    fun `refreshDeck preserves pulled progress matched by scryfallId and clamps to new required`() = runTest {
        // initial import: Forest x4, pulled 3 after import
        val archidekt = FakeArchidektApi(deck = {
            ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 4), archidektCard("uid-2", "Sol Ring", 1)))
        })
        val scryfall = ScryfallApi {
            ScryfallCollectionResponse(it.identifiers.map { ident ->
                when (ident.id) {
                    "uid-1" -> scryfallCard("uid-1", "Forest", "Land")
                    else -> scryfallCard("uid-2", "Sol Ring", "Artifact")
                }
            })
        }
        val r = repo(archidekt, scryfall)
        val id = r.importDeck("https://archidekt.com/decks/1")
        val forest = r.observeDeck(id).first()!!.cards.first { it.name == "Forest" }
        r.setPulled(forest.id, 3)

        // refresh: Forest now only x2 (should clamp 3 -> 2), Sol Ring removed, new card added
        val archidekt2 = FakeArchidektApi(deck = {
            ArchidektDeckDto("Deck Renamed", listOf(archidektCard("uid-1", "Forest", 2), archidektCard("uid-3", "Llanowar Elves", 1)))
        })
        val r2 = repo(archidekt2, ScryfallApi {
            ScryfallCollectionResponse(it.identifiers.map { ident ->
                when (ident.id) {
                    "uid-1" -> scryfallCard("uid-1", "Forest", "Land")
                    else -> scryfallCard("uid-3", "Llanowar Elves", "Creature")
                }
            })
        })
        r2.refreshDeck(id)

        val deck = r2.observeDeck(id).first()!!
        assertEquals("Deck Renamed", deck.name)
        assertEquals(2, deck.cards.size)
        val refreshedForest = deck.cards.first { it.name == "Forest" }
        assertEquals(2, refreshedForest.requiredQty)
        assertEquals(2, refreshedForest.pulledQty) // clamped from 3 to new required 2
        assertEquals(0, deck.cards.first { it.name == "Llanowar Elves" }.pulledQty)
    }

    @Test
    fun `resetProgress zeroes the deck`() = runTest {
        val archidekt = FakeArchidektApi(deck = {
            ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 4)))
        })
        val r = repo(archidekt, { ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land"))) })
        val id = r.importDeck("https://archidekt.com/decks/1")
        val cardId = r.observeDeck(id).first()!!.cards.single().id
        r.setPulled(cardId, 4)

        r.resetProgress(id)

        assertEquals(0, r.observeDeck(id).first()!!.cards.single().pulledQty)
    }

    @Test
    fun `deleteDeck removes the deck`() = runTest {
        val archidekt = FakeArchidektApi(deck = {
            ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 1)))
        })
        val r = repo(archidekt, { ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land"))) })
        val id = r.importDeck("https://archidekt.com/decks/1")

        r.deleteDeck(id)

        assertNull(r.observeDeck(id).first())
    }

    @Test
    fun `searchDecks maps owner results to summaries`() = runTest {
        val archidekt = FakeArchidektApi(
            list = ArchidektDeckListDto(
                listOf(
                    ArchidektDeckSummaryDto(id = 111, name = "Goblins", size = 100, featured = "http://img/a.jpg"),
                    ArchidektDeckSummaryDto(id = 222, name = "Elves", size = 99, featured = ""),
                ),
            ),
        )
        val r = repo(archidekt, { ScryfallCollectionResponse() })

        val summaries = r.searchDecks("me")

        assertEquals(2, summaries.size)
        assertEquals("111", summaries[0].archidektId)
        assertEquals("Goblins", summaries[0].name)
        assertEquals(100, summaries[0].cardCount)
        assertEquals("http://img/a.jpg", summaries[0].thumbnailUrl)
        assertNull(summaries[1].thumbnailUrl) // blank featured -> null
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.repository.DefaultDeckRepositoryTest"`
Expected: FAIL to compile — `observeDeck(id)`, `refreshDeck`, `resetProgress`, `deleteDeck`, `searchDecks`, `importDeck` return type unresolved.

- [ ] **Step 3: Update the repository interface**

Replace the entire contents of `DeckRepository.kt` with:

```kotlin
package com.deckpuller.data.repository

import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckSummary
import kotlinx.coroutines.flow.Flow

interface DeckRepository {
    fun observeDecks(): Flow<List<DeckWithCards>>
    fun observeDeck(id: Long): Flow<Deck?>

    /** @return new deck id. @throws com.deckpuller.data.InvalidDeckUrlException for an unparseable URL. */
    suspend fun importDeck(url: String): Long

    /** @return new deck id. Imports directly from an Archidekt numeric id (used by username browse). */
    suspend fun importDeckById(archidektId: String, sourceUrl: String): Long

    /** Re-fetch the deck from Archidekt, preserving pulled progress by scryfallId. */
    suspend fun refreshDeck(deckId: Long)

    suspend fun resetProgress(deckId: Long)
    suspend fun deleteDeck(deckId: Long)
    suspend fun setPulled(cardId: Long, pulled: Int)

    /** List a user's public Archidekt decks by exact username. */
    suspend fun searchDecks(username: String): List<DeckSummary>
}
```

- [ ] **Step 4: Rewrite the repository implementation**

Replace the entire contents of `DefaultDeckRepository.kt` with:

```kotlin
package com.deckpuller.data.repository

import com.deckpuller.data.ArchidektUrlParser
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.image.ImagePrefetcher
import com.deckpuller.data.local.DeckDao
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.data.remote.ArchidektApi
import com.deckpuller.data.remote.ScryfallApi
import com.deckpuller.data.remote.dto.ArchidektCardDto
import com.deckpuller.data.remote.dto.ScryfallCardDto
import com.deckpuller.data.remote.dto.ScryfallCollectionRequest
import com.deckpuller.data.remote.dto.ScryfallIdentifier
import com.deckpuller.data.toDomain
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val SCRYFALL_BATCH = 75
private const val SCRYFALL_THROTTLE_MS = 100L

class DefaultDeckRepository @Inject constructor(
    private val archidektApi: ArchidektApi,
    private val scryfallApi: ScryfallApi,
    private val dao: DeckDao,
    private val imagePrefetcher: ImagePrefetcher,
) : DeckRepository {

    override fun observeDecks(): Flow<List<DeckWithCards>> = dao.observeDecks()

    override fun observeDeck(id: Long): Flow<Deck?> =
        dao.observeDeck(id).map { it?.toDomain() }

    override suspend fun importDeck(url: String): Long {
        val archidektId = ArchidektUrlParser.parseDeckId(url) ?: throw InvalidDeckUrlException()
        return importDeckById(archidektId, url)
    }

    override suspend fun importDeckById(archidektId: String, sourceUrl: String): Long {
        val deckDto = archidektApi.getDeck(archidektId)
        val scryfallById = fetchScryfall(deckDto.cards.map { it.card.uid })
        val cards = deckDto.cards.map { entry ->
            buildCard(deckId = 0, entry = entry, scryfall = scryfallById[entry.card.uid], pulled = 0)
        }
        imagePrefetcher.prefetch(cards.mapNotNull { it.imageUrl })
        return dao.insertDeckWithCards(
            DeckEntity(
                name = deckDto.name,
                archidektId = archidektId,
                sourceUrl = sourceUrl,
                importedAt = System.currentTimeMillis(),
            ),
            cards,
        )
    }

    override suspend fun refreshDeck(deckId: Long) {
        val deck = dao.deckById(deckId) ?: return
        val deckDto = archidektApi.getDeck(deck.archidektId)
        val scryfallById = fetchScryfall(deckDto.cards.map { it.card.uid })
        val previousPulled = dao.cardsForDeck(deckId).associate { it.scryfallId to it.pulledQty }
        val cards = deckDto.cards.map { entry ->
            val prior = previousPulled[entry.card.uid] ?: 0
            buildCard(
                deckId = deckId,
                entry = entry,
                scryfall = scryfallById[entry.card.uid],
                pulled = prior.coerceAtMost(entry.quantity),
            )
        }
        imagePrefetcher.prefetch(cards.mapNotNull { it.imageUrl })
        dao.replaceCards(deckId, deckDto.name, cards)
    }

    override suspend fun resetProgress(deckId: Long) = dao.resetProgress(deckId)

    override suspend fun deleteDeck(deckId: Long) = dao.deleteDeck(deckId)

    override suspend fun setPulled(cardId: Long, pulled: Int) = dao.updatePulled(cardId, pulled)

    override suspend fun searchDecks(username: String): List<DeckSummary> =
        archidektApi.searchByOwner(owner = username).results.map { dto ->
            DeckSummary(
                archidektId = dto.id.toString(),
                name = dto.name,
                cardCount = dto.size,
                thumbnailUrl = dto.featured.ifBlank { null },
            )
        }

    private suspend fun fetchScryfall(uids: List<String>): Map<String, ScryfallCardDto> =
        uids.distinct()
            .chunked(SCRYFALL_BATCH)
            .flatMapIndexed { index, chunk ->
                if (index > 0) delay(SCRYFALL_THROTTLE_MS)
                val request = ScryfallCollectionRequest(chunk.map { ScryfallIdentifier(it) })
                scryfallApi.getCollection(request).data
            }
            .associateBy { it.id }

    private fun buildCard(
        deckId: Long,
        entry: ArchidektCardDto,
        scryfall: ScryfallCardDto?,
        pulled: Int,
    ) = CardEntity(
        deckId = deckId,
        scryfallId = entry.card.uid,
        name = scryfall?.name ?: entry.card.oracleCard.name,
        typeLine = scryfall?.bestTypeLine() ?: "Unknown",
        imageUrl = scryfall?.bestImageUrl(),
        requiredQty = entry.quantity,
        pulledQty = pulled,
    )
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.data.repository.DefaultDeckRepositoryTest"`
Expected: PASS (6 tests). (UI ViewModels won't compile yet — fixed in Tasks 7–10.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/repository app/src/test/java/com/deckpuller/data/repository/DefaultDeckRepositoryTest.kt
git commit -m "feat: repository multi-deck, refresh, reset, delete, search"
```

---

## Task 6: UserPreferences (DataStore) for the Archidekt username

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/prefs/UserPreferences.kt`

(No unit test: this is a thin DataStore wrapper; Robolectric DataStore tests are flaky and add little. It is exercised through the AddDeck flow.)

- [ ] **Step 1: Create UserPreferences**

Create `UserPreferences.kt`:

```kotlin
package com.deckpuller.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_prefs")
private val ARCHIDEKT_USERNAME = stringPreferencesKey("archidekt_username")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val username: Flow<String?> = context.dataStore.data.map { it[ARCHIDEKT_USERNAME] }

    suspend fun setUsername(value: String) {
        context.dataStore.edit { it[ARCHIDEKT_USERNAME] = value }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: no errors from this file (errors in `AppRoot`/ViewModels remain until later tasks).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/prefs/UserPreferences.kt
git commit -m "feat: DataStore-backed Archidekt username preference"
```

---

## Task 7: Remove MainViewModel (deck list becomes home)

**Files:**
- Delete: `app/src/main/java/com/deckpuller/ui/MainViewModel.kt`
- Delete: `app/src/test/java/com/deckpuller/ui/MainViewModelTest.kt`

- [ ] **Step 1: Delete the files**

```bash
git rm app/src/main/java/com/deckpuller/ui/MainViewModel.kt app/src/test/java/com/deckpuller/ui/MainViewModelTest.kt
```

- [ ] **Step 2: Commit**

```bash
git commit -m "refactor: drop MainViewModel; deck list is the new home"
```

(`AppRoot` still references it and won't compile until Task 11 — acceptable mid-plan; do not run the full build here.)

---

## Task 8: PullViewModel — deckId arg, search, refresh, reset

**Files:**
- Modify: `app/src/main/java/com/deckpuller/ui/pull/PullViewModel.kt`
- Test: `app/src/test/java/com/deckpuller/ui/pull/PullViewModelTest.kt`

- [ ] **Step 1: Rewrite the failing test**

Replace the entire contents of `PullViewModelTest.kt` with:

```kotlin
package com.deckpuller.ui.pull

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckCard
import com.deckpuller.domain.model.DeckSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PullViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun card(name: String, required: Int, pulled: Int) =
        DeckCard(id = name.hashCode().toLong(), scryfallId = name, name = name,
            typeLine = "Creature", imageUrl = null, requiredQty = required, pulledQty = pulled)

    private class FakeRepo(deck: Deck) : DeckRepository {
        val flow = MutableStateFlow<Deck?>(deck)
        var resetCalled = false
        var refreshCalled = false
        override fun observeDecks(): Flow<List<DeckWithCards>> = flowOf(emptyList())
        override fun observeDeck(id: Long): Flow<Deck?> = flow
        override suspend fun importDeck(url: String): Long = 0
        override suspend fun importDeckById(archidektId: String, sourceUrl: String): Long = 0
        override suspend fun refreshDeck(deckId: Long) { refreshCalled = true }
        override suspend fun resetProgress(deckId: Long) { resetCalled = true }
        override suspend fun deleteDeck(deckId: Long) {}
        override suspend fun setPulled(cardId: Long, pulled: Int) {}
        override suspend fun searchDecks(username: String): List<DeckSummary> = emptyList()
    }

    private fun vm(repo: DeckRepository) =
        PullViewModel(repo, SavedStateHandle(mapOf("deckId" to 7L)))

    @Test
    fun `state exposes deck totals from the full deck`() = runTest {
        val deck = Deck("My Deck", listOf(card("Forest", 4, 1), card("Sol Ring", 1, 0)))
        vm(FakeRepo(deck)).state.test {
            val s = awaitItem()!!
            assertEquals("My Deck", s.deckName)
            assertEquals(1, s.pulled)
            assertEquals(5, s.total)
        }
    }

    @Test
    fun `search filters groups but totals stay full`() = runTest {
        val deck = Deck("D", listOf(card("Forest", 4, 1), card("Sol Ring", 1, 0)))
        val model = vm(FakeRepo(deck))
        model.onSearchChange("sol")
        model.state.test {
            val s = awaitItem()!!
            assertEquals(5, s.total) // unchanged
            val names = s.groups.flatMap { it.cards }.map { it.name }
            assertEquals(listOf("Sol Ring"), names)
            assertEquals("sol", s.searchQuery)
        }
    }

    @Test
    fun `reset and refresh delegate to the repository for this deck`() = runTest {
        val repo = FakeRepo(Deck("D", listOf(card("Forest", 1, 0))))
        val model = vm(repo)
        model.reset()
        model.refresh()
        assertEquals(true, repo.resetCalled)
        assertEquals(true, repo.refreshCalled)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.pull.PullViewModelTest"`
Expected: FAIL to compile — `onSearchChange`, `reset`, `refresh`, new constructor signature, `searchQuery`.

- [ ] **Step 3: Rewrite PullViewModel**

Replace the entire contents of `PullViewModel.kt` with:

```kotlin
package com.deckpuller.ui.pull

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.DeckGrouping
import com.deckpuller.domain.model.CardGroup
import com.deckpuller.domain.model.DeckCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PullUiState(
    val deckName: String,
    val groups: List<CardGroup>,
    val pulled: Int,
    val total: Int,
    val searchQuery: String,
) {
    val isComplete: Boolean get() = total > 0 && pulled == total
}

@HiltViewModel
class PullViewModel @Inject constructor(
    private val repository: DeckRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: Long = checkNotNull(savedStateHandle["deckId"])
    private val query = MutableStateFlow("")
    val isRefreshing = MutableStateFlow(false)

    val state: StateFlow<PullUiState?> =
        combine(repository.observeDeck(deckId), query) { deck, q ->
            deck?.let {
                val filtered = if (q.isBlank()) it.cards
                    else it.cards.filter { card -> card.name.contains(q, ignoreCase = true) }
                PullUiState(
                    deckName = it.name,
                    groups = DeckGrouping.group(filtered),
                    pulled = it.cards.sumOf { card -> card.pulledQty },
                    total = it.cards.sumOf { card -> card.requiredQty },
                    searchQuery = q,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onSearchChange(value: String) { query.value = value }

    fun increment(card: DeckCard) {
        if (card.pulledQty >= card.requiredQty) return
        viewModelScope.launch { repository.setPulled(card.id, card.pulledQty + 1) }
    }

    fun decrement(card: DeckCard) {
        if (card.pulledQty <= 0) return
        viewModelScope.launch { repository.setPulled(card.id, card.pulledQty - 1) }
    }

    fun reset() {
        viewModelScope.launch { repository.resetProgress(deckId) }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                repository.refreshDeck(deckId)
            } finally {
                isRefreshing.value = false
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.pull.PullViewModelTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/pull/PullViewModel.kt app/src/test/java/com/deckpuller/ui/pull/PullViewModelTest.kt
git commit -m "feat: PullViewModel keyed by deckId with search, reset, refresh"
```

---

## Task 9: DeckListViewModel

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/decklist/DeckListViewModel.kt`
- Test: `app/src/test/java/com/deckpuller/ui/decklist/DeckListViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Create `DeckListViewModelTest.kt`:

```kotlin
package com.deckpuller.ui.decklist

import app.cash.turbine.test
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DeckListViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun deckWithCards(id: Long, name: String, vararg pulledOfRequired: Pair<Int, Int>) =
        DeckWithCards(
            deck = DeckEntity(id = id, name = name, archidektId = "$id", sourceUrl = "u", importedAt = id),
            cards = pulledOfRequired.mapIndexed { i, (pulled, required) ->
                CardEntity(id = id * 100 + i, deckId = id, scryfallId = "s$i", name = "c$i",
                    typeLine = "Creature", imageUrl = null, requiredQty = required, pulledQty = pulled)
            },
        )

    private class FakeRepo(decks: List<DeckWithCards>) : DeckRepository {
        val flow = MutableStateFlow(decks)
        var deleted: Long? = null
        override fun observeDecks(): Flow<List<DeckWithCards>> = flow
        override fun observeDeck(id: Long): Flow<Deck?> = flowOf(null)
        override suspend fun importDeck(url: String): Long = 0
        override suspend fun importDeckById(archidektId: String, sourceUrl: String): Long = 0
        override suspend fun refreshDeck(deckId: Long) {}
        override suspend fun resetProgress(deckId: Long) {}
        override suspend fun deleteDeck(deckId: Long) { deleted = deckId }
        override suspend fun setPulled(cardId: Long, pulled: Int) {}
        override suspend fun searchDecks(username: String): List<DeckSummary> = emptyList()
    }

    @Test
    fun `maps decks to list items with progress totals`() = runTest {
        val repo = FakeRepo(listOf(deckWithCards(1, "Goblins", 1 to 4, 0 to 1)))
        DeckListViewModel(repo).items.test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(1L, items[0].id)
            assertEquals("Goblins", items[0].name)
            assertEquals(1, items[0].pulled)
            assertEquals(5, items[0].total)
        }
    }

    @Test
    fun `delete delegates to repository`() = runTest {
        val repo = FakeRepo(emptyList())
        DeckListViewModel(repo).delete(42L)
        assertEquals(42L, repo.deleted)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.decklist.DeckListViewModelTest"`
Expected: FAIL — `DeckListViewModel` / `DeckListItem` unresolved.

- [ ] **Step 3: Create DeckListViewModel**

Create `DeckListViewModel.kt`:

```kotlin
package com.deckpuller.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.DeckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckListItem(
    val id: Long,
    val name: String,
    val pulled: Int,
    val total: Int,
)

@HiltViewModel
class DeckListViewModel @Inject constructor(
    private val repository: DeckRepository,
) : ViewModel() {

    val items: StateFlow<List<DeckListItem>> =
        repository.observeDecks()
            .map { decks ->
                decks.map { dwc ->
                    DeckListItem(
                        id = dwc.deck.id,
                        name = dwc.deck.name,
                        pulled = dwc.cards.sumOf { it.pulledQty },
                        total = dwc.cards.sumOf { it.requiredQty },
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(deckId: Long) {
        viewModelScope.launch { repository.deleteDeck(deckId) }
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.decklist.DeckListViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/decklist/DeckListViewModel.kt app/src/test/java/com/deckpuller/ui/decklist/DeckListViewModelTest.kt
git commit -m "feat: DeckListViewModel with per-deck progress"
```

---

## Task 10: ImportViewModel — import returns id + username browse

**Files:**
- Modify: `app/src/main/java/com/deckpuller/ui/importdeck/ImportViewModel.kt`
- Test: `app/src/test/java/com/deckpuller/ui/importdeck/ImportViewModelTest.kt`

- [ ] **Step 1: Rewrite the failing test**

Replace the entire contents of `ImportViewModelTest.kt` with:

```kotlin
package com.deckpuller.ui.importdeck

import app.cash.turbine.test
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.data.prefs.UserPreferences
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckSummary
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo(
        val importResult: () -> Long = { 9L },
        val summaries: List<DeckSummary> = emptyList(),
    ) : DeckRepository {
        override fun observeDecks(): Flow<List<DeckWithCards>> = flowOf(emptyList())
        override fun observeDeck(id: Long): Flow<Deck?> = flowOf(null)
        override suspend fun importDeck(url: String): Long = importResult()
        override suspend fun importDeckById(archidektId: String, sourceUrl: String): Long = importResult()
        override suspend fun refreshDeck(deckId: Long) {}
        override suspend fun resetProgress(deckId: Long) {}
        override suspend fun deleteDeck(deckId: Long) {}
        override suspend fun setPulled(cardId: Long, pulled: Int) {}
        override suspend fun searchDecks(username: String): List<DeckSummary> = summaries
    }

    private fun prefs(name: String? = null): UserPreferences = mockk(relaxed = true) {
        coEvery { username } returns flowOf(name)
    }

    @Test
    fun `successful import emits Imported with the new deck id`() = runTest {
        val vm = ImportViewModel(FakeRepo(importResult = { 42L }), prefs())
        vm.import("https://archidekt.com/decks/1")
        vm.state.test {
            assertEquals(ImportUiState.Imported(42L), awaitItem())
        }
    }

    @Test
    fun `invalid url emits Error`() = runTest {
        val repo = object : DeckRepository by FakeRepo() {
            override suspend fun importDeck(url: String): Long = throw InvalidDeckUrlException()
        }
        val vm = ImportViewModel(repo, prefs())
        vm.import("nope")
        vm.state.test {
            assertTrue(awaitItem() is ImportUiState.Error)
        }
    }

    @Test
    fun `findMyDecks populates results from the repository`() = runTest {
        val vm = ImportViewModel(
            FakeRepo(summaries = listOf(DeckSummary("111", "Goblins", 100, null))),
            prefs(),
        )
        vm.findMyDecks("me")
        vm.results.test {
            val r = awaitItem()
            assertEquals(1, r.size)
            assertEquals("Goblins", r[0].name)
        }
    }
}
```

This test uses MockK for `UserPreferences`. Add the test dependency in `app/build.gradle.kts` under the `testImplementation` group: `testImplementation("io.mockk:mockk:1.13.13")`. (MockK is not yet a project dep.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.importdeck.ImportViewModelTest"`
Expected: FAIL — `ImportUiState.Imported`, `findMyDecks`, `results`, new constructor param.

- [ ] **Step 3: Rewrite ImportViewModel**

Replace the entire contents of `ImportViewModel.kt` with:

```kotlin
package com.deckpuller.ui.importdeck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.prefs.UserPreferences
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.DeckSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Error(val message: String) : ImportUiState
    data class Imported(val deckId: Long) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: DeckRepository,
    userPreferences: UserPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private val _results = MutableStateFlow<List<DeckSummary>>(emptyList())
    val results: StateFlow<List<DeckSummary>> = _results.asStateFlow()

    val savedUsername: StateFlow<String?> =
        userPreferences.username.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val prefs = userPreferences

    fun import(url: String) = run {
        _state.value = ImportUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                ImportUiState.Imported(repository.importDeck(url))
            } catch (e: InvalidDeckUrlException) {
                ImportUiState.Error("That doesn't look like an Archidekt deck URL.")
            } catch (e: Exception) {
                ImportUiState.Error("Couldn't import the deck. Check your connection and try again.")
            }
        }
    }

    fun importSummary(summary: DeckSummary) {
        _state.value = ImportUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                ImportUiState.Imported(
                    repository.importDeckById(
                        summary.archidektId,
                        "https://archidekt.com/decks/${summary.archidektId}",
                    ),
                )
            } catch (e: Exception) {
                ImportUiState.Error("Couldn't import that deck. Try again.")
            }
        }
    }

    fun findMyDecks(username: String) {
        viewModelScope.launch {
            prefs.setUsername(username)
            _state.value = ImportUiState.Loading
            try {
                _results.value = repository.searchDecks(username)
                _state.value = ImportUiState.Idle
            } catch (e: Exception) {
                _state.value = ImportUiState.Error("Couldn't reach Archidekt. Check the username and your connection.")
            }
        }
    }

    fun dismissError() { _state.value = ImportUiState.Idle }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.importdeck.ImportViewModelTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/deckpuller/ui/importdeck/ImportViewModel.kt app/src/test/java/com/deckpuller/ui/importdeck/ImportViewModelTest.kt
git commit -m "feat: ImportViewModel returns new deck id and browses decks by username"
```

---

## Task 11: Navigation scaffolding (AppRoot NavHost)

**Files:**
- Modify: `app/src/main/java/com/deckpuller/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/deckpuller/MainActivity.kt`

(Screens referenced here are created in Tasks 12–14. Write `AppRoot` to reference them now; the build will only go green once those exist. Implement Tasks 12, 13, 14 before the build/run check in Step 3.)

- [ ] **Step 1: Simplify MainActivity (Scaffolds now own insets)**

Replace the entire contents of `MainActivity.kt` with:

```kotlin
package com.deckpuller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.deckpuller.ui.AppRoot
import com.deckpuller.ui.theme.DeckPullerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeckPullerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}
```

- [ ] **Step 2: Rewrite AppRoot as a NavHost**

Replace the entire contents of `AppRoot.kt` with:

```kotlin
package com.deckpuller.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.deckpuller.ui.decklist.DeckListScreen
import com.deckpuller.ui.decklist.DeckListViewModel
import com.deckpuller.ui.importdeck.AddDeckScreen
import com.deckpuller.ui.importdeck.ImportUiState
import com.deckpuller.ui.importdeck.ImportViewModel
import com.deckpuller.ui.pull.PullRoute

private const val DECK_LIST = "deckList"
private const val ADD_DECK = "addDeck"
private const val PULL = "pull"

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = DECK_LIST) {
        composable(DECK_LIST) {
            val viewModel: DeckListViewModel = hiltViewModel()
            val items by viewModel.items.collectAsStateWithLifecycle()
            DeckListScreen(
                decks = items,
                onDeckClick = { id -> navController.navigate("$PULL/$id") },
                onAddDeck = { navController.navigate(ADD_DECK) },
                onDeleteDeck = viewModel::delete,
            )
        }

        composable(ADD_DECK) {
            val viewModel: ImportViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val results by viewModel.results.collectAsStateWithLifecycle()
            val savedUsername by viewModel.savedUsername.collectAsStateWithLifecycle()

            LaunchedEffect(state) {
                val s = state
                if (s is ImportUiState.Imported) {
                    navController.navigate("$PULL/${s.deckId}") {
                        popUpTo(DECK_LIST)
                    }
                    viewModel.dismissError() // reset to Idle after navigating
                }
            }

            AddDeckScreen(
                state = state,
                results = results,
                savedUsername = savedUsername,
                onImportUrl = viewModel::import,
                onFindMyDecks = viewModel::findMyDecks,
                onPickDeck = viewModel::importSummary,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "$PULL/{deckId}",
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
        ) {
            PullRoute(
                onBack = { navController.popBackStack() },
                onAddDeck = { navController.navigate(ADD_DECK) },
            )
        }
    }
}
```

- [ ] **Step 3: Build + install after Tasks 12–14 exist**

Run (only after Tasks 12, 13, 14 are complete): `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/AppRoot.kt app/src/main/java/com/deckpuller/MainActivity.kt
git commit -m "feat: navigation-compose NavHost (deckList, addDeck, pull)"
```

---

## Task 12: DeckListScreen UI

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/decklist/DeckListScreen.kt`
- Test: `app/src/test/java/com/deckpuller/ui/decklist/DeckListScreenTest.kt`

- [ ] **Step 1: Write the failing compose test**

Create `DeckListScreenTest.kt`:

```kotlin
package com.deckpuller.ui.decklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckListScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `shows deck name and progress and routes clicks`() {
        var clicked: Long? = null
        rule.setContent {
            DeckListScreen(
                decks = listOf(DeckListItem(id = 5, name = "Goblins", pulled = 2, total = 10)),
                onDeckClick = { clicked = it },
                onAddDeck = {},
                onDeleteDeck = {},
            )
        }
        rule.onNodeWithText("Goblins").assertIsDisplayed()
        rule.onNodeWithText("2 / 10").assertIsDisplayed()
        rule.onNodeWithText("Goblins").performClick()
        assertEquals(5L, clicked)
    }

    @Test
    fun `empty state offers adding a deck`() {
        var added = false
        rule.setContent {
            DeckListScreen(decks = emptyList(), onDeckClick = {}, onAddDeck = { added = true }, onDeleteDeck = {})
        }
        rule.onNodeWithContentDescription("Add deck").performClick()
        assertEquals(true, added)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.decklist.DeckListScreenTest"`
Expected: FAIL — `DeckListScreen` unresolved.

- [ ] **Step 3: Create DeckListScreen**

Create `DeckListScreen.kt`:

```kotlin
package com.deckpuller.ui.decklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    decks: List<DeckListItem>,
    onDeckClick: (Long) -> Unit,
    onAddDeck: () -> Unit,
    onDeleteDeck: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("My Decks") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDeck) {
                Icon(Icons.Filled.Add, contentDescription = "Add deck")
            }
        },
    ) { padding ->
        if (decks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No decks yet. Tap + to add one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(decks, key = { it.id }) { deck ->
                    DeckRow(deck = deck, onClick = { onDeckClick(deck.id) }, onDelete = { onDeleteDeck(deck.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DeckRow(deck: DeckListItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                deck.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text("${deck.pulled} / ${deck.total}", style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${deck.name}")
            }
        }
        LinearProgressIndicator(
            progress = { if (deck.total == 0) 0f else deck.pulled.toFloat() / deck.total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

NOTE: Material icons (`Icons.Filled.*`) come from `androidx.compose.material:material-icons-core`, bundled with the Compose BOM and already transitively available via `material3`. If the build reports the icons unresolved, add `implementation("androidx.compose.material:material-icons-extended")` to `app/build.gradle.kts`. The basic `Add`/`Delete`/`Search`/`Refresh`/`ArrowBack`/`Close` icons used in this plan are in the core set.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.decklist.DeckListScreenTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/decklist/DeckListScreen.kt app/src/test/java/com/deckpuller/ui/decklist/DeckListScreenTest.kt
git commit -m "feat: DeckListScreen with progress and delete"
```

---

## Task 13: AddDeckScreen UI (URL + username browse)

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/importdeck/AddDeckScreen.kt`
- Delete: `app/src/main/java/com/deckpuller/ui/importdeck/ImportScreen.kt`
- Replace test: `app/src/test/java/com/deckpuller/ui/importdeck/ImportScreenTest.kt` → `AddDeckScreenTest.kt`

- [ ] **Step 1: Remove the old screen + test, write the failing new test**

```bash
git rm app/src/main/java/com/deckpuller/ui/importdeck/ImportScreen.kt app/src/test/java/com/deckpuller/ui/importdeck/ImportScreenTest.kt
```

Create `app/src/test/java/com/deckpuller/ui/importdeck/AddDeckScreenTest.kt`:

```kotlin
package com.deckpuller.ui.importdeck

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.deckpuller.domain.model.DeckSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddDeckScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `import button forwards the typed url`() {
        var url: String? = null
        rule.setContent {
            AddDeckScreen(
                state = ImportUiState.Idle, results = emptyList(), savedUsername = null,
                onImportUrl = { url = it }, onFindMyDecks = {}, onPickDeck = {}, onBack = {},
            )
        }
        rule.onNodeWithText("Archidekt deck URL").performTextInput("https://archidekt.com/decks/1")
        rule.onNodeWithText("Import").performClick()
        assertEquals("https://archidekt.com/decks/1", url)
    }

    @Test
    fun `tapping a browsed deck result imports it`() {
        var picked: DeckSummary? = null
        val summary = DeckSummary("111", "Goblins", 100, null)
        rule.setContent {
            AddDeckScreen(
                state = ImportUiState.Idle, results = listOf(summary), savedUsername = "me",
                onImportUrl = {}, onFindMyDecks = {}, onPickDeck = { picked = it }, onBack = {},
            )
        }
        rule.onNodeWithText("Goblins").assertIsDisplayed()
        rule.onNodeWithText("Goblins").performClick()
        assertEquals(summary, picked)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.importdeck.AddDeckScreenTest"`
Expected: FAIL — `AddDeckScreen` unresolved.

- [ ] **Step 3: Create AddDeckScreen**

Create `AddDeckScreen.kt`:

```kotlin
package com.deckpuller.ui.importdeck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deckpuller.domain.model.DeckSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeckScreen(
    state: ImportUiState,
    results: List<DeckSummary>,
    savedUsername: String?,
    onImportUrl: (String) -> Unit,
    onFindMyDecks: (String) -> Unit,
    onPickDeck: (DeckSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(savedUsername) {
        if (username.isBlank() && !savedUsername.isNullOrBlank()) username = savedUsername
    }
    val isLoading = state is ImportUiState.Loading

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Add a deck") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Archidekt deck URL") },
                singleLine = true,
                isError = state is ImportUiState.Error,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onImportUrl(url) },
                enabled = !isLoading && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import") }

            HorizontalDivider()
            Text("Or browse your Archidekt decks", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Archidekt username") },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { onFindMyDecks(username) },
                enabled = !isLoading && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Find my decks") }

            if (state is ImportUiState.Error) {
                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(results, key = { it.archidektId }) { summary ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickDeck(summary) }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(summary.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("${summary.cardCount} cards", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.importdeck.AddDeckScreenTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/importdeck/AddDeckScreen.kt app/src/test/java/com/deckpuller/ui/importdeck/AddDeckScreenTest.kt
git commit -m "feat: AddDeckScreen with URL import and username deck browsing"
```

---

## Task 14: PullScreen — top bar with search, refresh, reset, back; keep tap-to-enlarge; celebration no longer deletes

**Files:**
- Modify: `app/src/main/java/com/deckpuller/ui/pull/PullScreen.kt`
- Create: `PullRoute` inside `PullScreen.kt` (composable that wires the ViewModel + celebration).
- Test: `app/src/test/java/com/deckpuller/ui/pull/PullScreenTest.kt`

- [ ] **Step 1: Write the failing compose test**

Create `PullScreenTest.kt`:

```kotlin
package com.deckpuller.ui.pull

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.deckpuller.domain.DeckGrouping
import com.deckpuller.domain.model.DeckCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PullScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun card(name: String) = DeckCard(
        id = name.hashCode().toLong(), scryfallId = name, name = name,
        typeLine = "Creature", imageUrl = null, requiredQty = 1, pulledQty = 0,
    )

    private fun state(query: String = "", cards: List<DeckCard>) = PullUiState(
        deckName = "My Deck",
        groups = DeckGrouping.group(if (query.isBlank()) cards else cards.filter { it.name.contains(query, true) }),
        pulled = 0, total = cards.size, searchQuery = query,
    )

    @Test
    fun `reset action shows a confirmation dialog and confirms`() {
        var reset = false
        rule.setContent {
            PullScreen(
                state = state(cards = listOf(card("Forest"))),
                isRefreshing = false,
                onIncrement = {}, onDecrement = {}, onSearchChange = {},
                onRefresh = {}, onReset = { reset = true }, onBack = {}, onAddDeck = {},
                onCelebrationFinished = {},
            )
        }
        rule.onNodeWithContentDescription("Reset progress").performClick()
        rule.onNodeWithText("Reset").performClick() // confirm button in dialog
        assertEquals(true, reset)
    }

    @Test
    fun `typing in search forwards the query`() {
        var typed: String? = null
        rule.setContent {
            PullScreen(
                state = state(cards = listOf(card("Forest"), card("Mountain"))),
                isRefreshing = false,
                onIncrement = {}, onDecrement = {}, onSearchChange = { typed = it },
                onRefresh = {}, onReset = {}, onBack = {}, onAddDeck = {},
                onCelebrationFinished = {},
            )
        }
        rule.onNodeWithContentDescription("Search").performClick()
        rule.onNodeWithContentDescription("Search field").performTextInput("mount")
        assertEquals("mount", typed)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.pull.PullScreenTest"`
Expected: FAIL — `PullScreen` signature mismatch (new params).

- [ ] **Step 3: Rewrite PullScreen + add PullRoute**

Replace the entire contents of `PullScreen.kt` with:

```kotlin
package com.deckpuller.ui.pull

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.deckpuller.domain.model.DeckCard

@Composable
fun PullRoute(
    onBack: () -> Unit,
    onAddDeck: () -> Unit,
    viewModel: PullViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var celebrationDismissed by remember { mutableStateOf(false) }

    state?.let { pull ->
        PullScreen(
            state = pull,
            isRefreshing = isRefreshing,
            onIncrement = viewModel::increment,
            onDecrement = viewModel::decrement,
            onSearchChange = viewModel::onSearchChange,
            onRefresh = viewModel::refresh,
            onReset = viewModel::reset,
            onBack = onBack,
            onAddDeck = onAddDeck,
            onCelebrationFinished = { celebrationDismissed = true },
            showCelebration = pull.isComplete && !celebrationDismissed,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PullScreen(
    state: PullUiState,
    isRefreshing: Boolean,
    onIncrement: (DeckCard) -> Unit,
    onDecrement: (DeckCard) -> Unit,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onAddDeck: () -> Unit,
    onCelebrationFinished: () -> Unit,
    showCelebration: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var searching by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var zoomedCard by remember { mutableStateOf<DeckCard?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to decks")
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            placeholder = { Text("Search cards") },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search field" },
                        )
                    } else {
                        Text(state.deckName)
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = "Reset progress")
                        }
                        IconButton(onClick = onAddDeck) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Add another deck")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                PullHeader(pulled = state.pulled, total = state.total, isRefreshing = isRefreshing)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.groups.forEach { group ->
                        stickyHeader(key = "header-${group.type}") {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${group.type} (${group.cards.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }
                        items(group.cards, key = { it.id }) { card ->
                            CardRow(
                                card = card,
                                onIncrement = onIncrement,
                                onDecrement = onDecrement,
                                onImageClick = { zoomedCard = it },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            if (showCelebration) {
                CelebrationOverlay(onFinished = onCelebrationFinished)
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset progress?") },
            text = { Text("This sets every card's pulled count back to zero for this deck.") },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; onReset() }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }

    zoomedCard?.let { card ->
        CardImageDialog(card = card, onDismiss = { zoomedCard = null })
    }
}

@Composable
private fun PullHeader(pulled: Int, total: Int, isRefreshing: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("$pulled / $total pulled", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else pulled.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (isRefreshing) {
            Text("Refreshing…", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun CardImageDialog(card: DeckCard, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}
```

NOTE on icons: `RestartAlt` lives in `material-icons-extended`. If unresolved at build time, add `implementation("androidx.compose.material:material-icons-extended")` to `app/build.gradle.kts` (a known, version-aligned Compose artifact). `Search`, `Refresh`, `Close`, `ArrowBack` are in the core set.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.deckpuller.ui.pull.PullScreenTest"`
Expected: PASS (2 tests). If `RestartAlt`/`Refresh` icons fail to resolve, add the `material-icons-extended` dependency and re-run.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/deckpuller/ui/pull/PullScreen.kt app/src/test/java/com/deckpuller/ui/pull/PullScreenTest.kt
git commit -m "feat: pull-screen top bar with search, refresh, reset; non-destructive celebration"
```

---

## Task 15: Full build, full test suite, device verification

**Files:** none (verification only).

- [ ] **Step 1: Run the entire unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass. Fix any compile gaps surfaced here (most likely missing `material-icons-extended` — add it to `app/build.gradle.kts` and re-run).

- [ ] **Step 2: Build and install on the connected phone**

Run: `./gradlew :app:installDebug`
Expected: `Installed on 1 device.`

- [ ] **Step 3: Manual verification checklist (on device)**

Launch the app and confirm:
- Deck list is the home screen; the previously-loaded deck was wiped by the migration (expected). Tap **+**.
- On Add-deck: paste an Archidekt URL → Import → lands on the pull screen for that deck.
- Back arrow returns to the deck list, which now shows the deck with `0 / N`.
- Add another deck via **username**: enter your Archidekt username → "Find my decks" → your public decks list → tap one → imports and opens.
- In a pull screen: increment some cards, go back, reopen — progress persisted.
- **Search**: tap the magnifier, type a card name, list filters; close clears it.
- **Refresh**: tap refresh; deck re-syncs and pulled counts are preserved.
- **Reset**: tap reset → confirm dialog → counts go to zero.
- Complete a deck → celebration shows → deck remains in the list at 100% (not deleted).
- Status bar / nav bar: no overlap; bars adapt to theme.

- [ ] **Step 4: Final commit (only if build files changed in Step 1)**

```bash
git add app/build.gradle.kts
git commit -m "build: add material-icons-extended for pull-screen actions"
```

---

## Self-review notes (addressed)

- **Spec coverage:** multiple decks (Tasks 2,3,5,9,12); saved progress + non-deletion on completion (Tasks 2,5,14); search/refresh/reset/back buttons (Tasks 8,14); back-to-add-deck (Tasks 11,14); username browse (Tasks 4,5,6,10,13). All covered.
- **Type consistency:** `DeckRepository` surface (`observeDecks`/`observeDeck(id)`/`importDeck:Long`/`importDeckById`/`refreshDeck`/`resetProgress`/`deleteDeck`/`setPulled`/`searchDecks`) is identical across the interface, impl, and every fake in tests. `ImportUiState.Imported(deckId)`, `DeckListItem`, `DeckSummary`, `PullUiState(... searchQuery)` are used consistently.
- **Known external risk:** the Archidekt deck-list JSON is unofficial; DTOs use `ignoreUnknownKeys` and only depend on `id/name/size/featured`, verified live against the real endpoint on 2026-06-10.
- **MockK** added as a test-only dependency in Task 10 for faking `UserPreferences` in `ImportViewModelTest`.
```
