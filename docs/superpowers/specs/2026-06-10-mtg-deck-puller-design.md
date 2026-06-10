# MTG Deck Puller — Design

**Date:** 2026-06-10
**Status:** Approved (design phase)

## Purpose

An Android app to track which physical MTG cards you've pulled from your
collection while assembling a deck. You import a decklist from Archidekt, then
work through a checklist of cards — marking each off as you physically pull it —
until the whole deck is assembled, at which point the app celebrates and clears
the list.

The app keeps track of every card pulled and what remains, including decks that
require multiple copies of the same card.

## Scope

- **One deck at a time.** Importing a new deck replaces the current one.
- Android only. No iOS, no web.
- Single-user, local-only. No accounts, no sync, no backend of our own.

Explicitly out of scope (YAGNI): a library of multiple saved decks, deck
editing, collection management, price tracking, login/auth.

## Tech Stack

- **Language/UI:** Kotlin, Jetpack Compose (Material 3)
- **DI:** Hilt
- **Networking:** Retrofit + OkHttp + kotlinx.serialization
- **Persistence:** Room
- **Images:** Coil
- **Async:** Kotlin Coroutines / Flow
- **Celebration:** konfetti library
- **SDK:** `minSdk 26`, `targetSdk 35`
- **Structure:** single app module, package-by-feature

## User Flows

### Import
1. User pastes an Archidekt deck URL into the Import screen and taps Import.
2. App extracts the numeric deck ID from the URL.
3. App fetches the deck from Archidekt's public API.
4. App collects Scryfall IDs + required quantities for each card.
5. App batch-fetches card details (type line, image URLs) from Scryfall's
   `/cards/collection` endpoint (≤75 cards per request).
6. App builds card entries (name, type, image URL, required qty, pulled qty = 0).
7. App prefetches all card images into Coil's disk cache for offline use.
8. App persists the deck to Room (wiping any previous deck).
9. App navigates to the Pull screen.

### Pull
1. Pull screen shows an overall progress bar: `X / Y pulled`.
2. Cards are shown in a `LazyColumn` **grouped by card type** (Creature,
   Instant, Land, …) with sticky headers, sorted by name within each group.
   Type is derived from Scryfall's `type_line`.
3. Each row shows: cached thumbnail + card name + a counter `pulled/required`.
   - Tap the row (or a `+`) to increment the pulled count.
   - A `−` control decrements it.
   - Count is clamped to `0..required`. A card is "done" when
     `pulled == required`.
4. When **every** card reaches its required quantity, the app plays a confetti
   celebration animation, then clears the deck and returns to the Import screen.

## Architecture (MVVM, layered)

```
ui/
  import/    ImportScreen + ImportViewModel
  pull/      PullScreen + PullViewModel
  celebration/  Celebration composable (konfetti)
domain/
  model/     Deck, DeckCard (clean models)
data/
  remote/    ArchidektApi, ScryfallApi (Retrofit) + DTOs
  local/     Room: DeckEntity, CardEntity, DeckDao, AppDatabase
  repository/  DeckRepository
di/          Hilt modules
```

### Components

- **ArchidektApi** — `GET archidekt.com/api/decks/{id}/`. Returns deck name and
  cards, each with quantity and Scryfall ID (`uid`).
- **ScryfallApi** — `POST /cards/collection` with a list of Scryfall ID
  identifiers; returns `type_line` and `image_uris` per card. Batched ≤75.
- **DeckRepository** — orchestrates the import pipeline (Archidekt → Scryfall
  batch → image prefetch → persist), exposes the current deck as a `Flow`,
  updates pull counts, and clears the deck. Enforces one-deck-at-a-time by
  replacing on import.
- **Room schema:**
  - `DeckEntity(id, name, importedAt)`
  - `CardEntity(id, deckId FK, scryfallId, name, typeLine, imageUrl,
    requiredQty, pulledQty)`
- **ViewModels** expose UI state (`Loading | Success | Error` for import; deck +
  derived progress for pull) and handle increment/decrement and completion
  detection.

### Data Flow (import)

`URL → deckId (regex) → Archidekt deck → [scryfallId, qty] → Scryfall batch
→ DeckCard list → image prefetch → Room → Pull screen`

## Offline Image Strategy

On import, all card image URLs are enqueued through Coil's `ImageLoader` so the
images land in Coil's disk cache. The list then reads from cache, so pulling
works without a connection once a deck is imported. Image URLs are stored in
Room; Coil's disk cache is keyed by URL.

## Error Handling

- **Invalid / unparseable URL** → inline error on the Import field.
- **Archidekt / Scryfall network failure** → error message with a Retry action.
- **Card not found on Scryfall** → still listed using the name from Archidekt,
  with a placeholder image, grouped under an "Unknown" type header.
- **Image load failure** → placeholder image in the row.

## Testing

- **URL parsing** — unit tests for extracting the deck ID from various Archidekt
  URL shapes (and rejecting invalid input).
- **DTO parsing** — unit tests parsing sample Archidekt and Scryfall JSON
  payloads.
- **Repository** — tests with fake APIs + in-memory Room: import pipeline builds
  the correct card list, replace-on-import wipes the prior deck, missing-card
  fallback works.
- **ViewModels** — import state transitions (loading/success/error), counter
  clamping (0..required), and completion detection (all cards done → celebrate).

## Open Questions / Assumptions

- Archidekt's public deck API is unauthenticated and returns Scryfall IDs per
  card. (To be confirmed against a live payload during implementation; the
  raw-decklist fallback is a contingency if IDs aren't available, but is not in
  scope for v1.)
- Scryfall API usage stays within their rate-limit guidance (batched requests,
  cached images) — no special handling beyond batching expected for personal use.
