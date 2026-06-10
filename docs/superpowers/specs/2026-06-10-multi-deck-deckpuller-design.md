# Multi-deck DeckPuller — Design

**Date:** 2026-06-10
**Status:** Approved
**Branch:** `feature/multi-deck`

## Summary

Extend DeckPuller from a single-deck tool into a multi-deck app with saved
per-deck progress, on-screen controls (search, refresh, reset, add-deck), and
the ability to browse and import a user's public Archidekt decks by username.

## Goals

- Store and switch between multiple imported decks.
- Persist pull progress per deck (already persisted; stop deleting decks on
  completion so progress survives).
- Pull-screen controls: **search**, **refresh**, **reset**, and **back to the
  add-deck screen**.
- Import a user's **public** Archidekt decks by username, in addition to
  pasting a deck URL.

## Non-goals

- Archidekt account login / OAuth. Archidekt offers no official third-party
  auth, so **private decks are out of scope**. Only public decks (by username)
  are reachable.
- Editing decks, syncing pull state back to Archidekt, or offline deck search.

## Background / current state

- One deck at a time: `DeckEntity` has a fixed `CURRENT_DECK_ID = 1`, the DAO
  reads `SELECT * FROM decks LIMIT 1`, and `replaceDeck` clears all rows on
  every import.
- Pull progress (`CardEntity.pulledQty`) **is** persisted in Room, but
  completing a deck calls `clearDeck()` (via the celebration overlay), which
  deletes it — making it feel like progress was lost.
- No navigation framework: `AppRoot` switches on a `hasDeck: Boolean?`. The
  system back button exits the app.
- Archidekt access is an unofficial public API. Confirmed working query for
  listing a user's public decks:
  `api/decks/cards/?owner={username}&ownerexact=true&orderBy=-createdAt&pageSize=50`.

## Architecture

### Navigation
Introduce **Jetpack Navigation-Compose**. A `NavHost` with destinations:

- `deckList` — home. Lists saved decks (name + `pulled/total`). Top-bar **＋**
  action → `addDeck`. Per-deck overflow → **Delete**. Tap a deck → `pull/{id}`.
- `pull/{deckId}` — the pull screen, wrapped in a Material3 `TopAppBar` (which
  also resolves the status-bar inset cleanly). Back arrow → `deckList`.
- `addDeck` — paste an Archidekt URL, or enter/confirm a username and browse
  your public decks. This is the **"back to deck entry URL"** destination.

On launch: show `deckList`. (If zero decks, the list shows an empty state with
a prominent "Add a deck" affordance.)

### Data model (Room)
- `DeckEntity`: remove fixed id, `@PrimaryKey(autoGenerate = true) id`. Add
  `archidektId: String` (for refresh) and `sourceUrl: String`.
- `DeckDao`: add `observeDecks(): Flow<List<DeckWithCards>>`,
  `observeDeck(id): Flow<DeckWithCards?>`, `deleteDeck(id)`,
  `resetProgress(id)` → `UPDATE cards SET pulledQty = 0 WHERE deckId = :id`,
  and an upsert path for refresh.
- Schema change handled by **destructive migration** (bump DB version,
  `fallbackToDestructiveMigration`). Acceptable for this pre-release app; the
  one existing local deck is wiped once and can be re-added via username.

### Username storage
Store the Archidekt username in **DataStore Preferences** so it is entered
once and reused on the add-deck screen.

### Repository
`DeckRepository` gains:
- `importDeck(url): Long` — returns the new deck id; persists `archidektId`
  and `sourceUrl`.
- `refreshDeck(id)` — re-fetch from Archidekt by stored `archidektId`; update
  names/images/required quantities; **preserve `pulledQty` by matching
  `scryfallId`**, clamped to the new `requiredQty`.
- `resetProgress(id)`, `deleteDeck(id)`.
- `observeDecks()`, `observeDeck(id)`.
- `searchDecks(username): List<DeckSummary>` — calls the owner query above.

### Remote
- `ArchidektApi.searchByOwner(username, ...)` + a `DeckSummary` DTO
  (id, name, card count).

### ViewModels
- `DeckListViewModel` — exposes the deck list with per-deck progress.
- `PullViewModel` — keyed by `deckId` from nav args; adds a `searchQuery`
  state that filters groups by card name; exposes `refresh()`, `reset()`.
- `ImportViewModel` (add-deck) — extends to username browsing + import by
  picked deck.

## The four controls (Pull screen top bar)

- **Search** — magnifier toggles an inline search field; filters the card list
  by name as you type.
- **Refresh** — re-syncs the deck from Archidekt, preserving pulled progress.
- **Reset** — sets all pulled counts to 0 for this deck; guarded by a
  "Reset progress?" confirmation dialog.
- **Back to deck entry** — navigates to the add-deck screen; the top-bar back
  arrow returns to the deck list.

## Data flow

1. Add deck: URL paste or username browse → `importDeck` → new deck id →
   navigate to `pull/{id}`.
2. Pull: `observeDeck(id)` → grouped UI; increment/decrement → `setPulled`.
3. Refresh: `refreshDeck(id)` re-fetches and merges, preserving progress.
4. Completion: celebration overlay shows; deck remains in the list at 100%.

## Error handling

- Import/refresh/search network failures surface a non-fatal message (reuse
  the existing `ImportUiState.Error` pattern); the user can retry.
- Username with no public decks → empty-state message, not an error.
- Refresh of a deck whose Archidekt source is gone → error toast/snackbar; the
  local deck and its progress are left intact.

## Testing

Robolectric/unit tests:
- Progress-preserving refresh: `scryfallId` match + clamp to new required qty.
- `resetProgress` zeroes all cards for one deck only.
- Multi-deck DAO: insert/observe/delete isolation between decks.
- Username-search DTO parsing.
- Pull-list search filtering by name.
- Existing `ArchidektUrlParser` / mapping tests remain green.

## Dependencies added

- `androidx.navigation:navigation-compose`
- `androidx.datastore:datastore-preferences`

## Risks / notes

- One-time destructive wipe of the existing local deck (accepted).
- Unofficial Archidekt API may change shape; isolate parsing in DTOs + a thin
  API interface so breakage is contained.
