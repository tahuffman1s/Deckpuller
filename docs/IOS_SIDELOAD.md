# Installing DeckPuller on iPhone via SideStore (free, no Developer account)

DeckPuller's iOS build is produced by CI (GitHub Actions macOS runner) as an **unsigned
`.ipa`**. You install it on your own iPhone with **SideStore**, which re-signs it using a
**free Apple ID** — no $99/yr Apple Developer Program.

## What you need
- An iPhone (iOS 15+) and a **free Apple ID** (a throwaway one is wise — it gets tied to app
  signing).
- The latest **DeckPuller-unsigned.ipa** from GitHub Actions:
  Actions → latest `iOS` run → **Artifacts** → `DeckPuller-unsigned-ipa`.

## One-time SideStore setup
SideStore boots itself onto the phone using a **device pairing file** and a background
"refresh" trick (no always-on computer required afterward). The installer changes often, so
follow the official guide rather than hand-copied steps:

  → https://sidestore.io/  (Get Started / Install)

The result is a working SideStore app on your home screen, signed with your free Apple ID.

## Installing DeckPuller
1. Download `DeckPuller-unsigned.ipa` onto the iPhone (Files app, AirDrop, or a link).
2. Open **SideStore → My Apps → + → pick the .ipa**.
3. SideStore signs it with your Apple ID and installs it. On first launch:
   Settings → General → VPN & Device Management → trust your Apple ID.

## Keeping it alive (the 7-day reality)
- Free-signed apps expire after **7 days**. SideStore **auto-refreshes** them while it can
  reach the phone over its local refresh connection — open SideStore every few days and tap
  **Refresh All** if an app ever shows as expired.
- Free Apple IDs allow **3 sideloaded apps** at once. SideStore itself counts as one, so keep
  slots free.
- **New build?** Re-download the latest artifact and repeat *Installing* — it updates in place
  (same bundle id `com.deckpuller`), keeping your decks and collection data.

## What this does NOT do
- No sharing with other people (they'd sideload with their own Apple ID).
- No push notifications. No App Store listing.
- **No in-app self-update** — that feature is Android-only (iOS forbids app-managed installs);
  on iOS you re-sideload the new `.ipa` instead.

For any of those, enroll in the Apple Developer Program ($99/yr).

## How the build works (for maintainers)
- `.github/workflows/ios.yml`: the `compile` job builds the Kotlin/Native framework for device
  + simulator; the `ipa` job generates the Xcode project with **XcodeGen** (`iosApp/project.yml`),
  builds an unsigned `.app` with `xcodebuild`, zips it into `DeckPuller-unsigned.ipa`, and
  uploads it as an artifact.
- The iOS app shell is pure text — `iosApp/Sources/*.swift` + `iosApp/project.yml` — so no Xcode
  GUI or Mac is needed to maintain it. The single fiddliest line is the `Shared.framework` path
  in `project.yml`; if a build can't find it, read the `embedAndSignAppleFrameworkForXcode`
  output path from the CI log and correct that one line.
