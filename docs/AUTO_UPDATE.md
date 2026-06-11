# In-app auto-update

DeckPuller updates itself from its **GitHub Releases** — no app store required.

## How it works

On launch, the app asks GitHub for the latest release and compares it to the
installed version:

```
GET https://github.com/tahuffman1s/Deckpuller/releases/latest   (302 → .../releases/tag/<tag>)
```

> **Why not `api.github.com`?** The REST API allows only **60 unauthenticated
> requests per hour per source IP**. On carrier-grade NAT many phones share one
> address, so that budget is regularly exhausted and the API returns **HTTP 403**.
> The `releases/latest` *web* endpoint isn't rate-limited — it just 302-redirects
> to the tagged release — so the updater reads the tag from the `Location` header
> instead.

1. **Check** — `UpdateManager.checkForUpdate()` issues a non-following request to
   `releases/latest`, reads the redirect target's tag (e.g. `v1.2.0`), and derives
   the APK URL from CI's stable asset name (`DeckPuller-<version>.apk`). If the tag
   is a newer version than the installed one (see `VersionComparator`), it returns
   an `UpdateInfo`; otherwise `null`. Network errors and "up to date" are silent.
2. **Prompt** — `UpdateGate` (mounted in `AppRoot`, so it overlays any screen)
   shows an *"Update available"* dialog with the release notes.
3. **Download** — on **Update**, the APK is streamed to `cacheDir/updates/` with a
   live progress bar.
4. **Install** — the APK is handed to the system installer via a `FileProvider`
   content URI (`ACTION_VIEW`). The user confirms the install.

### Components

| Piece | File |
|-------|------|
| Version compare (pure, unit-tested) | `domain/VersionComparator.kt` |
| Check / download / install | `data/update/UpdateManager.kt` |
| UI state machine | `ui/update/UpdateViewModel.kt` |
| Dialog overlay | `ui/update/UpdateGate.kt` |
| Permission + FileProvider | `AndroidManifest.xml`, `res/xml/file_paths.xml` |

## Requirements & caveats

- **Same signing key.** Android only installs an update over an app signed with
  the **same** certificate. All releases are signed by the CI keystore
  (`docs/RELEASING.md`), so release→release updates work. A **debug** build
  (e.g. one installed via `installDebug`) is signed with the debug key and
  **cannot** auto-update to a release build — you'd uninstall/reinstall once to
  switch to release-signed builds, after which updates flow normally.
- **"Install unknown apps" permission.** Sideloaded installs need the
  `REQUEST_INSTALL_PACKAGES` permission (declared) plus the user granting
  "install unknown apps" to DeckPuller. The app detects this and sends the user
  to the right Settings screen if needed.
- **Latest release only.** Whatever `releases/latest` points at (GitHub's own
  "latest" pointer, which already excludes drafts and pre-releases) is offered.

## Releasing an update (what feeds the updater)

Cut a release with the pipeline — it builds and attaches the signed APK + AAB:

```bash
git tag v1.1.0 && git push origin v1.1.0
```

Once published, every installed (release-signed) app picks it up on next launch.
See `docs/RELEASING.md` for details.

## Testing notes

- `VersionComparatorTest` covers the version comparison logic (pure JVM, no
  device needed).
- The download/install path is device-only (system installer UI) and can't be
  unit-tested; verify it manually with two real releases (`vX` then `vX+1`).
