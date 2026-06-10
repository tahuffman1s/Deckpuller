<div align="center">

# 🎴 DeckPuller

### Pull your Magic: The Gathering decks from paper, one card at a time.

Import a deck from [Archidekt](https://archidekt.com), then tap your way through it as you physically pull each card from your collection — with live progress, card images, and everything saved per deck.

<br/>

<img src="docs/screenshots/demo.gif" width="280" alt="DeckPuller demo — open a deck, pull cards, and search the list" />

<br/><br/>

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](#)
[![Language](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](#)
[![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](#)
[![Min SDK](https://img.shields.io/badge/minSdk-26-blue)](#)
[![Tests](https://img.shields.io/badge/tests-48%20passing-brightgreen)](#)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

</div>

---

## ✨ What it does

Building a physical MTG deck from a list is tedious — you scan a decklist, hunt through binders, and lose your place. **DeckPuller** turns that into a tap-to-track checklist:

- Pull a deck straight from **Archidekt** (paste a URL, or browse your own decks by username).
- Cards are grouped by type and shown with their real art (via **Scryfall**).
- Tap a card to mark one pulled; the progress bar and per-card counters update instantly.
- Everything is saved locally, **per deck**, so you can stop and resume anytime.
- Finish a deck and you get a little confetti moment. 🎉

## 📸 Screenshots

<div align="center">
<table>
<tr>
<td align="center"><b>Your decks</b></td>
<td align="center"><b>Pulling a deck</b></td>
<td align="center"><b>Quick actions</b></td>
</tr>
<tr>
<td><img src="docs/screenshots/01-decks.png" width="240" alt="Deck list with per-deck progress" /></td>
<td><img src="docs/screenshots/02-pull.png" width="240" alt="Pull screen grouped by card type" /></td>
<td><img src="docs/screenshots/03-actions.png" width="240" alt="Speed-dial actions" /></td>
</tr>
</table>
</div>

## 🚀 Features

- **📚 Multiple decks** — keep as many decks as you like, each with its own saved pull progress.
- **🔗 Import from Archidekt** — paste a deck URL, or enter your Archidekt username and pick from your public decks (the username is remembered).
- **🖼️ Real card images** — fetched and cached from Scryfall; tap any card to see it full-size.
- **🗂️ Grouped & sorted** — cards are organized by type (Creatures, Instants, Lands, …) with sticky headers.
- **🔎 Search** — filter the current deck by card name; the keyboard pops up the moment you open it.
- **🔄 Refresh** — re-sync a deck from Archidekt **without losing your pulled progress**.
- **♻️ Reset** — zero-out a deck's progress (with a confirmation), ready for the next build.
- **💾 Offline-friendly progress** — pull counts live in a local database and survive restarts.
- **🎉 Completion celebration** — confetti when a deck hits 100%.
- **🌙 Modern Material 3 UI** — edge-to-edge, dark-theme-first, with a speed-dial action button.

## 🧭 How pulling works

1. **Add a deck** from the deck list (the `+` button) — by URL or by browsing your Archidekt username.
2. DeckPuller fetches the decklist from Archidekt and enriches each card with art/type data from Scryfall.
3. On the **pull screen**, tap a card row to increment its pulled count; use the `−` button to back one out.
4. The header shows overall `pulled / total` and a percentage; each card shows `pulled / required`.
5. Pull everything and enjoy the confetti — the deck stays in your list at 100% for next time.

The bottom-right **speed-dial** holds Search, Refresh, Reset, and Add-deck so the card list stays clean.

## 🏗️ Architecture & tech stack

DeckPuller is a single-module Android app following an MVVM + repository pattern with a clear data / domain / ui split.

| Layer | What's there |
|-------|--------------|
| **UI** | Jetpack Compose (Material 3), Navigation-Compose, screen-scoped `ViewModel`s exposing `StateFlow` |
| **Domain** | Plain Kotlin models (`Deck`, `DeckCard`, `DeckSummary`), card-type classification & grouping |
| **Data** | Repository over Retrofit APIs + a Room database; DataStore for preferences; Coil for images |

**Built with:**
- **Kotlin 2.0** · **Jetpack Compose** (Material 3) · **Navigation-Compose**
- **Hilt** for dependency injection
- **Room** for local persistence (decks + per-card progress)
- **Retrofit + kotlinx.serialization** for the Archidekt & Scryfall APIs
- **Coil** for card image loading/caching
- **DataStore (Preferences)** for the saved Archidekt username
- **Coroutines / Flow** throughout
- **konfetti** for the completion celebration
- **Robolectric · JUnit4 · Turbine · MockK** for tests

### Project layout

```
app/src/main/java/com/deckpuller/
├── data/
│   ├── remote/      # Archidekt + Scryfall APIs and DTOs
│   ├── local/       # Room database, DAO, entities
│   ├── repository/  # DeckRepository (import, refresh, reset, search…)
│   ├── prefs/       # DataStore-backed UserPreferences
│   └── image/       # Coil image prefetcher
├── domain/          # Models, card-type classifier, grouping
├── ui/
│   ├── decklist/    # "My Decks" home
│   ├── importdeck/  # Add-deck (URL + username browse)
│   ├── pull/        # Pull screen, card rows, celebration
│   └── theme/
└── di/              # Hilt modules
```

## 🛠️ Building & running

**Requirements:** Android Studio (Ladybug or newer), JDK 17, Android SDK 35.

```bash
# Clone
git clone git@github.com:tahuffman1s/Deckpuller.git
cd Deckpuller

# Build a debug APK
./gradlew :app:assembleDebug

# Install on a connected device / emulator
./gradlew :app:installDebug

# Run the unit test suite (Robolectric)
./gradlew :app:testDebugUnitTest
```

Or just open the project in Android Studio and hit **Run**.

## 🔌 Data sources

- **[Archidekt](https://archidekt.com)** — deck lists, via its public (unofficial) API.
- **[Scryfall](https://scryfall.com)** — card metadata and images.

> **Note on Archidekt accounts:** Archidekt has no official OAuth for third-party apps, so DeckPuller can only browse your **public** decks (by username) — there's no login and private decks aren't accessible. You can always import any deck directly by URL.

Please be kind to these free community APIs. DeckPuller is an unofficial fan project and isn't affiliated with Archidekt, Scryfall, or Wizards of the Coast.

## 📄 License

Released under the [MIT License](LICENSE).

<div align="center">
<sub>Magic: The Gathering is © Wizards of the Coast. DeckPuller is an unofficial, non-commercial fan tool.</sub>
</div>
