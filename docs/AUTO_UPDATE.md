# In-app auto-update

DeckPuller updates itself from its **GitHub Releases** — no app store required.

## How it works

On launch, the app asks GitHub for the latest release and compares it to the
installed version:

```
GET https://api.github.com/repos/tahuffman1s/Deckpuller/releases/latest
```

1. **Check** — `UpdateManager.checkForUpdate()` reads the latest release's
   `tag_name` (e.g. `v1.2.0`) and its attached `*.apk` asset. If the tag is a
   newer version than the installed one (see `VersionComparator`), it returns an
   `UpdateInfo`; otherwise `null`. Network errors and "up to date" are silent.
2. **Prompt** — `UpdateGate` (mounted in `AppRoot`, so it overlays any screen)
   shows an *"Update available"* dialog with the release notes.
3. **Download** — on **Update**, the APK is streamed to `cacheDir/updates/` with a
   live progress bar.
4. **Install** — the APK is handed to the system installer via a `FileProvider`
   content URI (`ACTION_VIEW`). The user confirms the install.

### Components

| Piece | File |
|-------|------|
| GitHub API + DTOs | `data/remote/GitHubApi.kt`, `data/remote/dto/GitHubDto.kt` |
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
- **Stable/full releases only.** Drafts and pre-releases are ignored; only the
  `releases/latest` (latest full release) with an `.apk` asset is offered.

## Releasing an update (what feeds the updater)

Cut a release with the pipeline — it builds and attaches the signed APK + AAB:

```bash
git tag v1.1.0 && git push origin v1.1.0
```

Once published, every installed (release-signed) app picks it up on next launch.
See `docs/RELEASING.md` for details.

## Testing notes

- `VersionComparatorTest` and `GitHubDtoTest` cover the comparison and parsing
  logic (pure JVM, no device needed).
- The download/install path is device-only (system installer UI) and can't be
  unit-tested; verify it manually with two real releases (`vX` then `vX+1`).
