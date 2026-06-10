# Build & Release Pipeline

DeckPuller ships with two GitHub Actions workflows:

| Workflow | File | Trigger | What it does |
|----------|------|---------|--------------|
| **CI** | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | push / PR to `main`, manual | Android Lint → unit tests → debug APK; uploads the APK and the test/lint reports as run artifacts. |
| **Release** | [`.github/workflows/release.yml`](../.github/workflows/release.yml) | push a `v*` tag, or manual dispatch | Builds a **signed** release APK **and** AAB, then publishes a GitHub Release with auto-generated notes and both files attached. |

Both run on `ubuntu-latest` with JDK 17 and cached Gradle.

---

## One-time setup: signing secrets

Release builds are signed with a keystore that **you** own. CI reads it from GitHub
Secrets; without them the release still builds but the artifacts are **unsigned**
(not installable / not publishable). Set it up once:

### 1. Generate an upload keystore

```bash
keytool -genkeypair -v \
  -keystore deckpuller-release.jks \
  -alias deckpuller \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep `deckpuller-release.jks` and the passwords **safe and private** — losing them
means you can't ship updates under the same identity. Do **not** commit it (the repo
`.gitignore` already excludes `*.jks` / `*.keystore`).

### 2. Base64-encode the keystore

```bash
base64 -w0 deckpuller-release.jks   # Linux
base64 deckpuller-release.jks       # macOS (no -w0)
```

Copy the output.

### 3. Add repository secrets

In GitHub → **Settings → Secrets and variables → Actions → New repository secret**,
add all four:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | the base64 string from step 2 |
| `KEYSTORE_PASSWORD` | the keystore (store) password |
| `KEY_ALIAS` | the key alias (e.g. `deckpuller`) |
| `KEY_PASSWORD` | the key password |

That's it — the next release run will produce signed artifacts.

---

## Cutting a release

### Option A — push a version tag (recommended)

```bash
git tag v1.0.0
git push origin v1.0.0
```

The Release workflow runs automatically and creates **DeckPuller v1.0.0** with
`DeckPuller-1.0.0.apk` and `DeckPuller-1.0.0.aab` attached.

### Option B — manual run

GitHub → **Actions → Release → Run workflow**, and enter the tag (e.g. `v1.0.0`).
The tag is created at the current `main` commit if it doesn't exist yet.

### Versioning

- `versionName` is derived from the tag (`v1.2.3` → `1.2.3`).
- `versionCode` is the workflow run number (monotonically increasing).

Both are passed into Gradle as `-PversionName=… -PversionCode=…`; locally they
default to `1.0` / `1`.

---

## Running the pipeline locally

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug          # what CI runs
./gradlew assembleRelease bundleRelease \                    # what Release runs
  -PversionName=1.0.0 -PversionCode=2
```

To test signing locally, export the same env vars the workflow uses
(`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) before the
release build; otherwise the artifacts are left unsigned.
