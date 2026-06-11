# ManaBox Collection Import + Store Buy Integration — Design

**Date:** 2026-06-11
**Status:** Approved design, pending implementation plan

## Summary

Let DeckPuller ingest a user's **ManaBox collection CSV** so the pull/checklist
screen can show what the user already owns versus what they're missing, browse
the whole collection, and turn the missing cards into a one-tap purchase on
**TCGplayer** or **Card Kingdom** (or a universal copy-to-clipboard).

ManaBox has no public API — integration is file-based (CSV export) for the
collection, and the store integrations are public bulk-add web links. Nothing
here depends on an API that doesn't exist.

## Decisions (locked during brainstorming)

| Decision | Choice |
| --- | --- |
| Ownership matching | **By card name** (any printing counts), but surface which printings are held |
| Feature scope | Owned/missing on pull screen · collection browser · missing-cards shopping list |
| Collection model | **Single collection, replaced wholesale on each import**; "last imported" timestamp shown |
| Import entry | **Share target + in-app file picker** |
| Shopping-list prices | From **Scryfall** (not the CSV's purchase price) |
| Buy-list precision | **Card name + quantity only** (store picks cheapest printing) |
| Buy targets | **TCGplayer**, **Card Kingdom**, **Copy list to clipboard** |

## ManaBox CSV format (verified against `ManaBox_Collection_1.csv`, ~3,100 rows)

Header (column order is NOT guaranteed — map by name):

```
Binder Name,Binder Type,Name,Set code,Set name,Collector number,Foil,Rarity,
Quantity,ManaBox ID,Scryfall ID,Purchase price,Misprint,Altered,Condition,
Language,Purchase price currency,Added
```

Observed realities the parser must handle:

- **Quoted fields**: names with commas are quoted — `"Mazirek, Kraul Death Priest"`.
- **Double-faced / split cards** use ` // ` — `Pestilent Cauldron // Restorative Burst`,
  and may also be quoted when they contain a comma — `"Lluwen, Exchange Student // Pest Friend"`.
- **`Scryfall ID`** is present per row → clean price lookups and exact printing detail.
- **`Binder Name`** identifies where the card physically lives → shown in owned detail (useful for pulling).
- **Collector number** can contain letters (`127p`).
- **`Foil`** values: `normal` / `foil` / `etched`.
- Real files are large (thousands of rows) — import must stream/batch, not block the UI.

## Architecture

A **separate collection module** parallel to the existing deck stack, because the
collection is global and independent of any deck. Pure logic is split from I/O,
mirroring the existing `ArchidektUrlParser` (pure) vs repository (I/O) split.

```
CSV (Uri)
   │  CollectionImporter      (Android: Uri -> text via ContentResolver)
   ▼
ManaBoxCsvParser              (pure: text -> ParsedCollection + failed lines)
   ▼
CollectionRepository          (replace table in one txn; stamp DataStore timestamp)
   ▼
Room: collection_cards
   │
   ├── observeOwnedByName() ──► PullViewModel enrichment (owned/missing on rows)
   ├── observeAll() ─────────► CollectionViewModel (browser)
   └── (deck cards − owned) ─► ShoppingListViewModel ──► StoreCartLinks (buy URLs)
```

### New units and their responsibilities

| Unit | Package | Purpose | Depends on |
| --- | --- | --- | --- |
| `CardName` (`normalize()`) | `domain` | Canonical name key used on BOTH deck and collection sides | — |
| `ManaBoxCsvParser` | `data` | Pure CSV → `ParsedCollection` (rows + failed lines). No Android types. | `CardName` |
| `CollectionImporter` | `data` | Resolve `Uri` → text via `ContentResolver`; hand to parser | `@ApplicationContext`, parser |
| `CollectionCardEntity` | `data.local.entity` | One Room row per CSV printing line | — |
| `CollectionDao` | `data.local` | Replace-on-import, owned-by-name aggregation, browse query | — |
| `CollectionRepository` (+ `Default…`) | `data.repository` | Import orchestration, ownership flows, freshness timestamp | DAO, importer, prefs |
| `StoreCartLinks` | `domain` (pure) | Build TCGplayer / Card Kingdom bulk-add URLs + clipboard text from a buy list | — |
| `CollectionScreen` / `CollectionViewModel` | `ui.collection` | Browse + search + import button + empty state | repository |
| `ShoppingListScreen` / `ShoppingListViewModel` | `ui.shopping` | Missing cards, Scryfall prices, buy buttons | deck + collection repos |

## Data model (Room: bump version 3 → 4)

```kotlin
@Entity(
    tableName = "collection_cards",
    indices = [Index("nameKey")],
)
data class CollectionCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameKey: String,        // CardName.normalize(name) — the join key
    val name: String,           // display name as ManaBox gave it
    val setCode: String,
    val setName: String,
    val collectorNumber: String,
    val scryfallId: String?,    // present in ManaBox CSV; null-safe
    val finish: String,         // normal | foil | etched
    val condition: String,
    val language: String,
    val binderName: String,     // where the card physically lives
    val quantity: Int,
)
```

- **No new meta table.** "Last imported" timestamp + card count live in the
  existing `UserPreferences` DataStore (`collectionImportedAt: Long?`,
  `collectionCount: Int`).
- **Migration `MIGRATION_3_4`** creates `collection_cards`. Register it in
  `DataModule.provideDatabase` alongside `MIGRATION_2_3`. (Note: the builder
  currently also has `fallbackToDestructiveMigration()`; keep the explicit
  migration so a real upgrade path exists.)

### DAO queries

```kotlin
@Query("DELETE FROM collection_cards")
suspend fun clear()

@Insert suspend fun insertAll(rows: List<CollectionCardEntity>)

@Transaction
suspend fun replaceAll(rows: List<CollectionCardEntity>) { clear(); insertAll(rows) }

// Owned-by-name totals -> Map<nameKey, Int> in the repository
@Query("SELECT nameKey, SUM(quantity) AS qty FROM collection_cards GROUP BY nameKey")
fun observeOwnedTotals(): Flow<List<OwnedTotal>>

// Printing detail for one card (for the "have: 2x LTR, 1x C21 foil" line)
@Query("SELECT * FROM collection_cards WHERE nameKey = :nameKey ORDER BY setCode")
suspend fun printingsFor(nameKey: String): List<CollectionCardEntity>

// Browser
@Query("SELECT * FROM collection_cards ORDER BY name COLLATE NOCASE")
fun observeAll(): Flow<List<CollectionCardEntity>>
```

## Name normalization (`CardName.normalize`)

The single sharpest correctness risk. Used identically on the deck card name
(from Scryfall) and the CSV `Name`. Rules:

- Lowercase, trim, collapse internal whitespace.
- Normalize DFC/split separator: treat `A // B` consistently (store full
  normalized `a // b`; the deck side's Scryfall `name` is already `A // B`).
- Strip/normalize accents (NFKD fold) so `Lim-Dûl` matches `Lim-Dul`.
- Leave punctuation that's part of the name (apostrophes, commas) but compare
  case-insensitively.

Unit tests cover DFCs, split cards, accents, commas, apostrophes, and basics.

## Feature 1 — Owned/missing on the pull screen

- `PullViewModel.state` `combine(...)` gains a fourth source:
  `collectionRepository.observeOwnedByName()` → `Map<String, Int>` (nameKey → owned qty).
- Each `DeckCard` is enriched **at combine time** with transient ownership —
  `ownedQty` and (lazily, for the expanded detail) `ownedPrintings`. These are
  **not persisted**: add defaulted fields to the domain `DeckCard`
  (`ownedQty: Int = 0`), populated in the ViewModel, so `CardEntity`/Room and
  `Mappers.toDomain()` are untouched and existing tests stay green.
- `CardRow` gains a small ownership indicator following the existing subtitle
  style: green **Owned** (with count) vs amber **Missing**; an optional secondary
  line `have: 2× LTR · 1× C21 (foil) — Binder "EDH"` when detail is available.
- Optional deck-level header chip: "You own 71 / 100 cards." (include — cheap,
  computed from the same map).
- When **no collection is imported**, ownership UI is simply hidden (no
  regressions to current behavior).

## Feature 2 — Collection browser

- `ui.collection`: `CollectionScreen` + `CollectionViewModel`.
- Searchable, alphabetical list of the whole collection; reuse the existing
  `AlphabetRail` + scrub-bubble pattern from the pull screen.
- Each row: name · total qty · set(s) · finish. Tapping expands printing detail.
- **Empty state**: when nothing imported, show explanation + an Import button.
- Header shows "Last imported · <relative time> · N cards" from DataStore.
- Reached from the deck-list top bar (new icon) — see Navigation.

## Feature 3 — Missing-cards shopping list + buy

- `ui.shopping`: `ShoppingListScreen` + `ShoppingListViewModel`, deck-scoped,
  reached from a pull-screen app-bar action.
- Compute: for each deck card, `need = requiredQty − ownedQty` (clamped ≥ 0);
  the list is cards with `need > 0`.
- **Prices from Scryfall**: extend `ScryfallCardDto` with
  `prices { usd, usd_foil }` (the `cards/collection` endpoint already returns it).
  Add `DeckRepository.prices(scryfallIds): Map<String, Double?>` reusing the
  existing batched + throttled `fetchScryfall`. Show per-card `usd × need` and a
  grand total. Missing prices degrade gracefully via `runCatching` (as
  `colorIdentity` already does).
- **Buy actions** (`StoreCartLinks`, pure + unit-tested URL builders):
  - **TCGplayer** — open
    `https://www.tcgplayer.com/massentry?productline=Magic&c=<list>` in a Custom
    Tab / browser intent, where `<list>` is URL-encoded `N Card Name` joined by
    `||`. Lands on the pre-filled Mass Entry review page.
  - **Card Kingdom** — open the Deck Builder pre-filled. **Caveat:** the pre-fill
    mechanism (GET vs POST) is unconfirmed. Plan: attempt the deep-link; if it is
    POST-only or breaks, fall back to **open `cardkingdom.com/builder` + copy the
    list to clipboard** so the action always works. This fallback is decided up
    front, not deferred.
  - **Copy list to clipboard** — universal `N Card Name` text for any store/site.
- **Expectation set in UI copy:** both stores land the user on a bulk-add/review
  page (not a silently-filled final cart) — by design.
- **URL length**: a large missing list can exceed practical URL limits. If the
  TCGplayer URL would be too long, fall back to clipboard + open Mass Entry, and
  `log`/toast that the list was copied instead. No silent truncation.

## Import flow (entry points)

1. **Share target** — `AndroidManifest` adds an `intent-filter` on `MainActivity`
   for `ACTION_SEND` and `ACTION_VIEW` with mime `text/csv`,
   `text/comma-separated-values`, and `*/*` (CSV mime is inconsistent across
   launchers; guard by extension/content when `*/*`). `MainActivity` routes the
   incoming `Uri` to the import path.
2. **In-app picker** — a button on the Collection screen launches
   `ActivityResultContracts.OpenDocument` (`text/*`).

Both paths call `CollectionImporter` → parser → `CollectionRepository.import()`,
which `replaceAll` in one transaction and stamps the DataStore timestamp/count.
Result surfaced to the user: **"Imported 812 cards · 3 skipped"** (mirrors
ManaBox's own partial-import behavior), with the skipped lines available.

## Navigation & DI

- `AppRoot`: add routes `COLLECTION = "collection"` and
  `SHOPPING = "shopping/{deckId}"`. Entry points: Collection from the deck-list
  top bar; Shopping list from the pull-screen app bar.
- `DataModule`: provide `CollectionDao` (`db.collectionDao()`), bind
  `CollectionRepository`, provide `CollectionImporter` (needs
  `@ApplicationContext`). Register `MIGRATION_3_4`.
- `UserPreferences`: add `collectionImportedAt` / `collectionCount` keys + flows.

## Error handling

| Case | Behavior |
| --- | --- |
| Missing required column (`Name` or `Quantity`) | Abort import; clear message naming the missing column |
| Malformed / partial rows | Import valid rows, collect failures, show "N imported · M skipped" with details |
| Empty / non-CSV file | Friendly error; collection unchanged |
| Scryfall price fetch fails | Show shopping list without prices (graceful) |
| Card name doesn't match collection | Treated as not-owned (acceptable; normalization minimizes this) |
| TCGplayer URL too long | Copy list to clipboard + open Mass Entry; notify user |
| Card Kingdom pre-fill unavailable | Open builder + copy list to clipboard |

## Testing (matches existing Robolectric + Turbine + MockK setup)

- `CardNameNormalizeTest` — DFC `//`, split, accents, commas, apostrophes, basics.
- `ManaBoxCsvParserTest` — real sample rows from `ManaBox_Collection_1.csv`:
  quoted comma names, DFC names, missing/extra/reordered columns, foil/etched,
  letter collector numbers, partial-failure reporting.
- `CollectionDaoTest` — replace-on-import wipes prior data; owned-by-name SUM
  aggregation across printings.
- `CollectionRepositoryTest` — import replaces + stamps timestamp; ownership map.
- `StoreCartLinksTest` — TCGplayer URL encoding/joining; clipboard text;
  long-list fallback threshold.
- `PullViewModelTest` — owned/missing enrichment given a collection; hidden when
  no collection.
- `ShoppingListViewModelTest` — `need` computation, price totals, null prices.

## Out of scope (YAGNI for v1)

- Owned-as-pulled auto-fill (explicitly declined — ownership stays separate from
  pull progress).
- Merge/incremental collection sync (replace-on-import only).
- Multiple named collection snapshots.
- TCGplayer affiliate kickback tags (trivial to add later; noted, not built).
- Exact-printing buy lists / per-screen precision toggle (name+qty only).
- CSV-sourced prices (Scryfall is the price source).

## Key risks

1. **Name normalization** correctness across DFCs / split / accented / basic-land
   names — mitigated by a single shared normalizer with heavy unit tests.
2. **Card Kingdom pre-fill mechanism** (GET vs POST) unconfirmed — mitigated by a
   decided clipboard + open-builder fallback.
3. **Share-target mime handling** — CSV mime types are inconsistent; guard with
   `*/*` + extension/content sniffing.
