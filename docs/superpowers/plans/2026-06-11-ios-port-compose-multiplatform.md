# DeckPuller iOS Port (Compose Multiplatform) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

---

## EXECUTION LOG (branch `ios-port-kmp`, started 2026-06-11)

Resolved real versions (knowledge cutoff was behind): **Kotlin 2.2.20 · CMP 1.9.3 · KSP 2.2.20-2.0.4 · AGP 8.11.2 · Gradle 8.13 · compileSdk/targetSdk 36 · Koin 4.2.1 · Ktor 3.5.0 · Robolectric 4.16.1**. Baseline = **99 tests**, green after every step.

**Key deviations from the written plan (all sound, end-state identical):**
1. **Hilt→Koin (Task 2.1) was pulled BEFORE the module restructure.** Reason: Hilt's Gradle plugin + KSP don't work in a `kotlin-multiplatform` module, so "park Hilt in androidMain" (plan Task 1.4) is unworkable. Koin is pure-runtime and moves cleanly.
2. **Stack modernized (AGP 8.7.2→8.11.2, Gradle→8.13, SDK 35→36, Robolectric 4.14→4.16.1).** Forced by Koin 4.2.1 requiring androidx.activity 1.12.4 (needs compileSdk 36); chose to modernize rather than carry a fragile `force()` downgrade. KSP pinned to KSP1 (`ksp.useKSP2=false` in gradle.properties) — KSP2 trips on Room/Hilt processors under Kotlin 2.2.
3. **Phase 1 put ALL code in `androidMain` (not the commonMain split of plan Task 1.3).** Reason: a grep proved ZERO files are commonMain-ready pre-swap (every file imports an android-only lib). `commonMain` promotion now happens per-file DURING the Phase 2 library swaps. `:shared` namespace is `com.deckpuller.shared` (R-collision avoidance); `:androidApp` stays `com.deckpuller`.

**DONE (committed, all green):**
- [x] Phase 0 — branch + baseline (99 tests)
- [x] Task 1.1 — Kotlin/CMP/KSP bump + KMP/CMP plugins in catalog
- [x] Task 2.1 — Hilt→Koin (`di/AppModule.kt`; `koinViewModel()`; startKoin guarded for Robolectric) + stack modernization
- [x] Phase 1 (Tasks 1.2–1.5) — split `:app` → `:shared` (KMP android-library, android target only) + `:androidApp` launcher
- [x] Task 2.2 — Retrofit/OkHttp → Ktor 3.5.0 (APIs now concrete classes over HttpClient; tests use Ktor MockEngine). okhttp kept for UpdateManager's release download.
- [x] Task 2.3 — Room → Room KMP 2.8.4 (bundled driver, `@ConstructedBy` + KSP-generated constructor, migrations on `SQLiteConnection`). **DB layer (AppDatabase/DAOs/entities) is now in `commonMain`** — first commonMain code. db name + migrations preserved. Required `ksp.useKSP2=true`, serialization→1.8.1, exportSchema=true. Tests use `AndroidSQLiteDriver` (bundled native lib won't load under Robolectric); prod uses `BundledSQLiteDriver`.
- [x] Task 2.4 — DataStore → datastore-preferences-core (KMP). **`UserPreferences` now in `commonMain`** (takes `DataStore<Preferences>`); Koin supplies the android path `<filesDir>/datastore/user_prefs.preferences_pb` (byte-identical to old default).
- [x] Task 2.5 — Coil 2 → Coil 3.5.0 (+ coil-network-ktor3 over the existing Ktor client; `SingletonImageLoader.setSafe`). All AsyncImage sites + `loadCardBitmap` ported. Forced `kotlin-stdlib`→2.2.20 (Coil pins 2.4.0, unreadable by the compiler).

- [x] Task 2.6 — konfetti → **ConfettiKit 0.8.0** (`io.github.vinceglb:confettikit`), a CMP port of Konfetti with the same Party/Position/Emitter API and iOS artifacts. `CelebrationOverlay`: `KonfettiView`→`ConfettiKit`, `Emitter(2, TimeUnit.SECONDS)`→`Emitter(2.seconds)`. (Chose the maintained KMP library over a hand-rolled confetti — less code, real iOS support.)
- [x] Task 2.7 — navigation-compose → **`org.jetbrains.androidx.navigation:navigation-compose` 2.9.2** (JetBrains KMP fork, iOS artifacts). Same `androidx.navigation.*` packages → coordinate swap only, no code changes.

**REMAINING on Linux (files still in `androidMain`):**
- [ ] **commonMain promotion + apply `compose-multiplatform` plugin** (drop the androidx compose-BOM in `:shared`, switch to `compose.*` deps, move platform-free UI/domain to `commonMain`; the data/prefs layer is already there). HIGHEST RISK — best done as one focused step, ideally alongside iOS-target setup. This subsumes plan Task 1.3's intent. NOTE: all third-party libs are now KMP/iOS-capable (Koin, Ktor, Room KMP, DataStore-MP, Coil 3, ConfettiKit, JetBrains nav), so this step is purely the Compose/source-set move + the `expect/actual` for the ~5 platform files (Phase 3).

**REMAINING needs macOS/CI (Phases 3–4):** unchanged — `expect`/`actual` (haptics, CoreMotion tilt, UIDocumentPicker, share, self-update no-op) then the CI-build + SideStore sideload flow.

---

**Goal:** Ship an iOS build of DeckPuller with the same GUI and feature set as the Android app (minus the in-app self-updater, which iOS forbids) by migrating the existing Kotlin/Jetpack-Compose code to Compose Multiplatform (KMP).

**Architecture:** Restructure the single Android `:app` module into a KMP `:shared` module (holding all UI, view models, domain, data) plus thin platform launchers (`:androidApp`, `iosApp`). ~49 of 61 source files are pure Compose/Kotlin and move to `commonMain` essentially untouched. The ~12 files touching Android framework APIs are split: library-backed ones (Room, DataStore, Coil, networking, DI) swap to KMP-capable libraries; truly platform-specific ones (haptics, device-tilt sensor, file import, share sheet) become `expect`/`actual` declarations with Android + iOS implementations. The Android build must stay green at every step so the live app never regresses.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (JetBrains), Koin (DI, replaces Hilt), Ktor Client (replaces Retrofit/OkHttp), Room KMP (replaces Room-Android), DataStore Multiplatform, Coil 3 (replaces Coil 2), CoreMotion + CoreHaptics (iOS sensor/haptics), `UIDocumentPickerViewController` + `UIActivityViewController` (iOS file import / share).

---

## CRITICAL CONSTRAINTS — read before starting

1. **No Mac and no paid Apple Developer account — we build in CI and sideload for free.** You cannot compile/link an iOS binary on Linux, but a **GitHub Actions `macos` runner** does it for us (free minutes on a public repo). The resulting `.ipa` is installed on a personal iPhone with **SideStore**, which re-signs it using a **free Apple ID** — no $99 Developer Program. The cost of "free" is real and baked into Phase 4: apps re-sign every **7 days** (SideStore automates this), max **3 sideloaded apps** per device, install only on **your own** device(s), and **no way to distribute to other people**. The moment you need to hand the app to someone else, ship push notifications, or list on the App Store, the $99/yr account becomes unavoidable — out of scope here. Phases 0–2 run on this Linux box; Phase 3's iOS compile-checks and Phase 4's build run on the CI macOS runner; final feel-testing happens on a physical iPhone via SideStore.

2. **The self-updater cannot be ported.** `data/update/UpdateManager.kt`, `ui/update/UpdateGate.kt`, and `ui/update/UpdateViewModel.kt` download a GitHub-Releases APK and invoke the Android package installer. iOS has no equivalent and forbids it. These stay in `androidMain`; on iOS the update gate is a no-op (`expect fun isSelfUpdateSupported(): Boolean` → `false`). This is the single accepted feature-parity gap. Confirm with the product owner before proceeding (already flagged).

3. **Keep Android green.** After every task that touches shared code, `./gradlew :androidApp:assembleDebug` (or `:shared:testDebugUnitTest`) must still pass. The existing test suite (`app/src/test`) is the regression net — it moves to `:shared/src/commonTest` + `:shared/src/androidUnitTest` and must keep passing.

4. **Verify library versions at execution time.** Concrete versions are given below (current as of 2026-06), but KMP libraries move fast. Each library-introduction task includes a step to confirm the latest stable before pinning. Pinning a real version is required — do not leave version refs blank.

5. **This is a migration, not greenfield.** Where a task is a mechanical library swap, the "test" is the existing suite still passing, not a new unit test. Where a task adds genuinely new behavior (e.g. an iOS `actual`), it gets its own verification. iOS UI/feel (gyro, haptics) can only be verified by hand on a device — those tasks say so explicitly.

---

## Target module / file structure

```
deckpuller/
├── settings.gradle.kts            # add :shared, :androidApp; (iosApp is an Xcode project)
├── gradle/libs.versions.toml      # add KMP/Compose/Koin/Ktor/Coil3 entries
├── shared/
│   ├── build.gradle.kts           # kotlin-multiplatform + compose-multiplatform plugins
│   └── src/
│       ├── commonMain/kotlin/com/deckpuller/
│       │   ├── ui/…                # ALL screens move here unchanged (pull, collection,
│       │   │                       #   shopping, decklist, importdeck, settings, theme, common)
│       │   ├── domain/…            # moves here unchanged
│       │   └── data/
│       │       ├── remote/…        # ScryfallApi/ArchidektApi → Ktor (commonMain)
│       │       ├── local/…         # Room KMP entities/DAOs/AppDatabase (commonMain)
│       │       ├── repository/…    # moves here unchanged
│       │       └── prefs/…         # DataStore MP (commonMain)
│       ├── commonMain/kotlin/com/deckpuller/platform/   # expect declarations
│       │   ├── Haptics.kt          # expect class/interface
│       │   ├── DeviceTilt.kt       # expect composable hook
│       │   ├── FilePicker.kt       # expect file-pick + read
│       │   ├── ShareSheet.kt       # expect share
│       │   └── SelfUpdate.kt       # expect fun isSelfUpdateSupported()
│       ├── androidMain/kotlin/com/deckpuller/
│       │   ├── platform/…          # actual impls = today's android.* code
│       │   ├── data/update/…       # UpdateManager stays Android-only
│       │   └── di/AndroidModule.kt # Koin android module (Context, DataStore, Room driver)
│       ├── iosMain/kotlin/com/deckpuller/
│       │   ├── platform/…          # actual impls = CoreMotion/CoreHaptics/UIKit
│       │   └── di/IosModule.kt
│       ├── commonTest/…            # portable unit tests (domain, repos w/ fakes)
│       └── androidUnitTest/…       # Robolectric/Android-only tests
├── androidApp/
│   ├── build.gradle.kts           # com.android.application; depends on :shared
│   └── src/main/…                 # MainActivity, DeckPullerApp, manifest, res, signing
└── iosApp/                        # Xcode project (created on Mac in Phase 4)
    └── iosApp/ContentView.swift   # hosts ComposeUIViewController { App() }
```

**Package move note:** files keep their `com.deckpuller.*` package; only their source-set directory changes. Imports of `androidx.compose.*`, `kotlinx.*`, `coil3.*`, `io.ktor.*` resolve identically in `commonMain`. Imports of `android.*` are only legal in `androidMain`.

---

## Phase 0 — Branch & baseline

### Task 0.1: Create the work branch and capture the green baseline

**Files:**
- Modify: none (git + verification only)

- [ ] **Step 1: Branch off main**

```bash
git checkout -b ios-port-kmp
```

- [ ] **Step 2: Record the current Android test + build baseline**

Run:
```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. Save the test count from the output (the same tests must pass after the module move). If this fails on a clean checkout, STOP and fix the baseline before porting anything.

- [ ] **Step 3: Commit the baseline marker**

```bash
git commit --allow-empty -m "chore: baseline before KMP/iOS port"
```

---

## Phase 1 — Restructure into a KMP module (Android stays the only target)

Goal of this phase: end with `:shared` (KMP, but only the `android()` target enabled) + `:androidApp`, all existing code moved, all existing tests passing. **No library swaps and no iOS target yet** — purely structural, so a regression here is obviously structural.

### Task 1.1: Add KMP + Compose-Multiplatform plugins to the version catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Confirm latest stable versions**

Run:
```bash
# Check current stable Kotlin, Compose Multiplatform, AGP compatibility matrix
echo "Verify at: https://github.com/JetBrains/compose-multiplatform/releases"
echo "Verify Kotlin/AGP/Compose compat at: https://kotlinlang.org/docs/multiplatform-compatibility-guide.html"
```
Pin the newest mutually-compatible trio. The values below are the 2026-06 known-good set; bump only if a newer trio is confirmed compatible.

- [ ] **Step 2: Add versions + plugins**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
kotlin = "2.1.21"                  # bump from 2.0.21; CMP 1.7+ needs Kotlin 2.1.x
composeMultiplatform = "1.7.3"
ksp = "2.1.21-1.0.31"              # must match the new kotlin version
```
Under `[plugins]` add:
```toml
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
```
Keep the existing `android-application`, `kotlin-compose`, `kotlin-serialization`, `ksp` entries.

- [ ] **Step 3: Sync and verify the catalog parses**

Run:
```bash
./gradlew help
```
Expected: `BUILD SUCCESSFUL` (no "unresolved version catalog" errors).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: add KMP and Compose Multiplatform plugins to catalog"
```

### Task 1.2: Create the `:shared` module with only the Android target

**Files:**
- Create: `shared/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, change `include(":app")` to:
```kotlin
include(":shared")
include(":androidApp")
```
(`:app` is renamed to `:androidApp` in Task 1.5; declaring both now is fine because the dirs are created before the next sync.)

- [ ] **Step 2: Write `shared/build.gradle.kts` (Android target only, for now)**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }
    // iOS targets are added in Phase 4 on a Mac:
    // iosArm64(); iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

android {
    namespace = "com.deckpuller"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

- [ ] **Step 3: Add the needed catalog entries this file references**

In `libs.versions.toml`, add under `[versions]`: `coroutines` already exists. Add libraries if missing:
```toml
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
```
And under `[plugins]`:
```toml
android-library = { id = "com.android.library", version.ref = "agp" }
```

- [ ] **Step 4: Create empty source-set dirs so Gradle recognizes the module**

```bash
mkdir -p shared/src/commonMain/kotlin/com/deckpuller \
         shared/src/androidMain/kotlin/com/deckpuller \
         shared/src/commonTest/kotlin/com/deckpuller \
         shared/src/androidUnitTest/kotlin/com/deckpuller
```

- [ ] **Step 5: Do NOT build yet** (no code moved; `:androidApp` doesn't exist). Commit the scaffold.

```bash
git add settings.gradle.kts shared/ gradle/libs.versions.toml
git commit -m "build: scaffold :shared KMP module (android target only)"
```

### Task 1.3: Move portable code (UI, domain, repositories, DTOs) into `commonMain`

These files have **no `android.*` imports** and no Hilt/Retrofit/Room/Coil/DataStore dependency in their import list (those are handled in Phase 2). Move them verbatim.

**Files:**
- Move: every `.kt` under `app/src/main/java/com/deckpuller/` that does NOT appear in the "platform/library files" list in Task 1.4 — i.e. all of `ui/**` except the platform bits, all of `domain/**`, `data/repository/**`, `data/remote/dto/**`.
- Move tests: `app/src/test/java/com/deckpuller/{domain,data/repository}/**` → `shared/src/commonTest/kotlin/com/deckpuller/…` (only those with no Android/Robolectric dependency); Robolectric-dependent tests → `shared/src/androidUnitTest/…`.

- [ ] **Step 1: Identify the exact portable set**

Run:
```bash
cd app/src/main/java/com/deckpuller
# Portable = no android.* import AND not a Hilt/Retrofit/Room/Coil/DataStore file
grep -rL "import android\." . | sort > /tmp/no_android.txt
grep -rL -E "import (dagger|retrofit2|androidx\.room|coil|androidx\.datastore)" . | sort > /tmp/no_libs.txt
comm -12 /tmp/no_android.txt /tmp/no_libs.txt
cd -
```
This prints the files safe to move with zero edits in this task. (View-model files using `@HiltViewModel` will appear because they import `androidx.lifecycle` not `dagger.hilt.android` directly — handle their annotations in Task 2.1; move their non-DI bodies now is fine since the annotation lines are edited in Phase 2. If a VM imports `dagger.*` it is excluded here and moved in Task 2.1.)

- [ ] **Step 2: Move the files preserving package paths**

For each file `PATH` from Step 1 (example shown for the pull screen — repeat for all):
```bash
git mv app/src/main/java/com/deckpuller/ui/pull/PullScreen.kt \
       shared/src/commonMain/kotlin/com/deckpuller/ui/pull/PullScreen.kt
```
Do this for every portable file. Use `git mv` so history follows.

- [ ] **Step 3: Move the portable tests**

```bash
# domain + repository tests that don't need Robolectric:
git mv app/src/test/java/com/deckpuller/domain/<Test>.kt \
       shared/src/commonTest/kotlin/com/deckpuller/domain/<Test>.kt
```
For any test annotated `@RunWith(RobolectricTestRunner::class)` or importing `androidx.test`, move to `shared/src/androidUnitTest/...` instead.

- [ ] **Step 4: Replace any `androidx.compose.ui.res` resource usage**

Run:
```bash
grep -rn "painterResource\|R.drawable\|stringResource" shared/src/commonMain || echo "none"
```
There are 2 `painterResource`/`R.drawable` usages. Replace each Android `R.drawable.x` with a Compose-Multiplatform resource: move the drawable into `shared/src/commonMain/composeResources/drawable/` and reference it via the generated `Res.drawable.x` + `painterResource(Res.drawable.x)` from `org.jetbrains.compose.resources`. Show the edit for each occurrence (find them, then):
```kotlin
// before:  painterResource(R.drawable.foo)
// after:
import deckpuller.shared.generated.resources.Res
import deckpuller.shared.generated.resources.foo
import org.jetbrains.compose.resources.painterResource
painterResource(Res.drawable.foo)
```

- [ ] **Step 5: Commit (won't build until Phase 1 completes — that's expected)**

```bash
git add -A
git commit -m "refactor: move portable UI/domain/repo code into shared/commonMain"
```

### Task 1.4: Keep library- and platform-coupled files in `androidMain` (temporarily) and move the rest

The 12 platform/library files stay in Android source for now and are migrated one-by-one in Phase 2/3. Move them into `:shared`'s `androidMain` so the module compiles for Android while Phase 2 converts them.

**Files (move `app/.../X` → `shared/src/androidMain/kotlin/com/deckpuller/X`):**
- `data/CollectionImporter.kt`
- `data/image/ImagePrefetcher.kt` (+ `CoilImagePrefetcher` if separate)
- `data/local/**` (AppDatabase, DeckDao, CollectionDao, entities)
- `data/prefs/UserPreferences.kt`
- `data/remote/ScryfallApi.kt`, `data/remote/ArchidektApi.kt`
- `data/update/UpdateManager.kt`
- `di/DataModule.kt`
- `ui/pull/PullFeedback.kt`, `ui/pull/CardBitmap.kt`
- `ui/common/CardImageDialog.kt`
- `ui/collection/CollectionViewModel.kt`, `ui/shopping/ShoppingListScreen.kt`, and any VM importing `dagger.*`

- [ ] **Step 1: Move them with git mv** (one command per file, as in Task 1.3 Step 2).

- [ ] **Step 2: Add the Android-only deps these files need to `:shared`'s `androidMain`**

In `shared/build.gradle.kts` `androidMain.dependencies { … }`, temporarily add the existing Android libs so the module compiles unchanged (these get removed as Phase 2 swaps each one):
```kotlin
implementation(libs.hilt.android)
implementation(libs.room.runtime); implementation(libs.room.ktx)
implementation(libs.retrofit); implementation(libs.retrofit.serialization)
implementation(libs.okhttp); implementation(libs.okhttp.logging)
implementation(libs.coil.compose)
implementation(libs.androidx.datastore.preferences)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.androidx.navigation.compose)
implementation(libs.konfetti.compose)
```
Add `ksp(libs.room.compiler)` and `ksp(libs.hilt.compiler)` under the android KSP config, and the hilt plugin to the module plugins block (temporarily).

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: park platform/library files in shared/androidMain pre-swap"
```

### Task 1.5: Convert `:app` into the thin `:androidApp` launcher

**Files:**
- Modify/rename: `app/` → `androidApp/`
- Modify: `androidApp/build.gradle.kts` (depend on `:shared`, drop libs now owned by `:shared`)
- Keep in `androidApp`: `MainActivity.kt`, `DeckPullerApp.kt`, `AndroidManifest.xml`, `res/`, signing config.

- [ ] **Step 1: Rename the module dir**

```bash
git mv app androidApp
```

- [ ] **Step 2: Rewrite `androidApp/build.gradle.kts`**

Keep `com.android.application` + signing config (from the original, unchanged), and replace the dependency list with:
```kotlin
dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Hilt entry point stays here until Task 2.1 swaps DI to Koin:
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
```
Preserve the entire `signingConfigs`/`buildTypes` block verbatim from the original `app/build.gradle.kts`.

- [ ] **Step 3: Point `settings.gradle.kts` at the new path** (already `include(":androidApp")` from Task 1.2; ensure no stray `:app`).

- [ ] **Step 4: Build the whole thing for Android**

Run:
```bash
./gradlew :androidApp:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. Fix import/source-set errors until green. This proves the structural move is complete and the live Android app is intact.

- [ ] **Step 5: Run the migrated test suite**

Run:
```bash
./gradlew :shared:testDebugUnitTest
```
Expected: same test count as the Phase 0 baseline, all passing.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: :app -> thin :androidApp launcher on :shared module"
```

---

## Phase 2 — Swap each Android-only library for its KMP equivalent

After each task: `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest` stays green. Each swap moves the file from `androidMain` to `commonMain` once it no longer imports `android.*`.

### Task 2.1: Replace Hilt with Koin

**Files:**
- Create: `shared/src/commonMain/kotlin/com/deckpuller/di/SharedModule.kt`
- Create: `shared/src/androidMain/kotlin/com/deckpuller/di/AndroidModule.kt`
- Delete: `shared/src/androidMain/.../di/DataModule.kt` (Hilt)
- Modify: every `@HiltViewModel` VM (6), every `@Inject`/`@Singleton`/`@Binds` site (repositories, UserPreferences, CollectionImporter, ImagePrefetcher, UpdateManager), `DeckPullerApp.kt`, `MainActivity.kt`.

- [ ] **Step 1: Add Koin to the catalog and module**

`libs.versions.toml`:
```toml
koin = "4.0.0"
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
```
`shared/build.gradle.kts`: add `koin-core`, `koin-compose`, `koin-compose-viewmodel` to `commonMain`; `koin-android` to `androidMain`. Confirm latest stable Koin first.

- [ ] **Step 2: Replace `@HiltViewModel class FooViewModel @Inject constructor(...)`**

For each VM, drop the Hilt annotations and keep the constructor:
```kotlin
// before
@HiltViewModel
class PullViewModel @Inject constructor(private val repo: DeckRepository) : ViewModel()
// after
class PullViewModel(private val repo: DeckRepository) : ViewModel()
```
Change the call sites from `hiltViewModel()` to Koin's `koinViewModel()`:
```kotlin
import org.koin.compose.viewmodel.koinViewModel
val vm: PullViewModel = koinViewModel()
```

- [ ] **Step 3: Write `SharedModule.kt` (commonMain) replacing `DataModule`'s `@Provides`/`@Binds`**

```kotlin
package com.deckpuller.di
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import kotlinx.serialization.json.Json
// repositories, apis, daos, prefs, viewmodels:
val sharedModule = module {
    single { Json { ignoreUnknownKeys = true } }
    single<DeckRepository> { DefaultDeckRepository(get(), get(), get()) }
    single<CollectionRepository> { DefaultCollectionRepository(get(), get()) }
    single<ImagePrefetcher> { get<ImagePrefetcherFactory>().create() } // see Task 2.5
    single { ScryfallApi(get()) }    // Ktor-backed, Task 2.2
    single { ArchidektApi(get()) }
    viewModel { PullViewModel(get()) }
    viewModel { CollectionViewModel(get(), get(), get()) }
    viewModel { DeckListViewModel(get()) }
    viewModel { ImportViewModel(get()) }
    viewModel { ShoppingListViewModel(get()) }
    // UpdateViewModel is registered in AndroidModule only.
}
```
(Adjust `get()` arity to each constructor's real parameters — read each constructor and match.)

- [ ] **Step 4: Write `AndroidModule.kt` (androidMain) for Context-bound singletons**

```kotlin
package com.deckpuller.di
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
val androidModule = module {
    single { provideHttpClient() }                 // Ktor OkHttp engine (Task 2.2)
    single { createDatabase(androidContext()) }    // Room KMP builder (Task 2.3)
    single { get<AppDatabase>().deckDao() }
    single { get<AppDatabase>().collectionDao() }
    single { UserPreferences(createDataStore(androidContext())) } // Task 2.4
    single { CollectionImporter(androidContext()) }               // until Task 3.3
    single { UpdateManager(get(), androidContext()) }
    viewModel { UpdateViewModel(get()) }
}
```

- [ ] **Step 5: Start Koin from the Android `Application`**

In `androidApp/.../DeckPullerApp.kt`, drop `@HiltAndroidApp` and start Koin:
```kotlin
class DeckPullerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DeckPullerApp)
            modules(sharedModule, androidModule)
        }
    }
}
```
Drop `@AndroidEntryPoint` from `MainActivity.kt`. Remove Hilt plugin/deps from `shared` and `androidApp` Gradle files and the `hilt.navigation.compose` import.

- [ ] **Step 6: Build + test green**

```bash
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, all tests pass. (Tests that used Hilt test components switch to plain constructor injection / Koin test — update them in this task.)

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "refactor: replace Hilt with Koin (KMP DI)"
```

### Task 2.2: Replace Retrofit/OkHttp with Ktor Client

**Files:**
- Modify: `data/remote/ScryfallApi.kt`, `data/remote/ArchidektApi.kt` (→ `commonMain`, Ktor)
- Create: `shared/src/commonMain/.../data/remote/HttpClientFactory.kt` (`expect`)
- Create: `androidMain`/`iosMain` `actual` HttpClient engine providers
- Modify: `SharedModule`/`AndroidModule` provider for the client

- [ ] **Step 1: Add Ktor to catalog + module**

```toml
ktor = "3.0.3"
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
```
`commonMain`: core + content-negotiation + json. `androidMain`: okhttp engine. `iosMain` (added Phase 4): darwin engine. Confirm latest Ktor 3.x first.

- [ ] **Step 2: `expect`/`actual` HTTP engine, shared config in common**

`commonMain/.../HttpClientFactory.kt`:
```kotlin
package com.deckpuller.data.remote
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun httpEngine(): io.ktor.client.engine.HttpClientEngine

fun provideHttpClient(): HttpClient = HttpClient(httpEngine()) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    defaultRequest {
        headers.append(HttpHeaders.UserAgent, "DeckPuller/1.0")
        headers.append(HttpHeaders.Accept, "application/json")
    }
}
```
`androidMain`: `actual fun httpEngine() = io.ktor.client.engine.okhttp.OkHttp.create()`.
`iosMain` (Phase 4): `actual fun httpEngine() = io.ktor.client.engine.darwin.Darwin.create()`.

- [ ] **Step 3: Rewrite the two APIs as Ktor calls (commonMain)**

`ScryfallApi.kt`:
```kotlin
package com.deckpuller.data.remote
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.deckpuller.data.remote.dto.ScryfallCollectionRequest
import com.deckpuller.data.remote.dto.ScryfallCollectionResponse

class ScryfallApi(private val client: HttpClient) {
    suspend fun getCollection(request: ScryfallCollectionRequest): ScryfallCollectionResponse =
        client.post("https://api.scryfall.com/cards/collection") {
            contentType(ContentType.Application.Json); setBody(request)
        }.body()
}
```
`ArchidektApi.kt`:
```kotlin
package com.deckpuller.data.remote
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import com.deckpuller.data.remote.dto.ArchidektDeckDto
import com.deckpuller.data.remote.dto.ArchidektDeckListDto

class ArchidektApi(private val client: HttpClient) {
    suspend fun getDeck(deckId: String): ArchidektDeckDto =
        client.get("https://archidekt.com/api/decks/$deckId/").body()

    suspend fun searchByOwner(
        username: String, orderBy: String = "-updatedAt", pageSize: Int = 50,
    ): ArchidektDeckListDto =
        client.get("https://archidekt.com/api/decks/v3/") {
            parameter("ownerUsername", username); parameter("orderBy", orderBy)
            parameter("pageSize", pageSize)
        }.body()
}
```

- [ ] **Step 4: Update Koin providers** — `single { provideHttpClient() }` (move from AndroidModule note to commonMain `sharedModule` since engine is `expect`), `single { ScryfallApi(get()) }`, `single { ArchidektApi(get()) }`. Remove the OkHttp/Retrofit deps from `:shared` androidMain. Verify repositories call the new method signatures (they already match: `getDeck`, `searchByOwner`, `getCollection`).

- [ ] **Step 5: Build + test**

```bash
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest
```
Expected: green. Any repository test that mocked the Retrofit interface now constructs the class with a fake `HttpClient` (Ktor `MockEngine`) — update those tests here.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor: replace Retrofit/OkHttp with Ktor client"
```

### Task 2.3: Migrate Room to Room KMP

**Files:**
- Modify: `data/local/AppDatabase.kt`, `DeckDao.kt`, `CollectionDao.kt`, 3 entities (→ `commonMain`)
- Create: `expect`/`actual` `getDatabaseBuilder()` for the platform DB file path
- Modify: KSP config to run for KMP

- [ ] **Step 1: Confirm Room KMP support & versions**

Room KMP requires Room 2.7.0+ and the bundled SQLite driver. Confirm the current stable:
```bash
echo "Verify Room KMP at: https://developer.android.com/kotlin/multiplatform/room"
```
Catalog:
```toml
room = "2.7.0"
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version = "2.5.0" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
```
Add the Room Gradle plugin: `room = { id = "androidx.room", version.ref = "room" }`.

- [ ] **Step 2: Enable Room + KSP for KMP in `shared/build.gradle.kts`**

```kotlin
plugins { alias(libs.plugins.ksp); alias(libs.plugins.room) }
room { schemaDirectory("$projectDir/schemas") }
dependencies {
    add("kspAndroid", libs.room.compiler)
    // add("kspIosArm64", libs.room.compiler); add("kspIosSimulatorArm64", libs.room.compiler) // Phase 4
}
kotlin { sourceSets {
    commonMain.dependencies { implementation(libs.room.runtime); implementation(libs.sqlite.bundled) }
}}
```

- [ ] **Step 3: Make `AppDatabase` KMP-compatible**

Move it to `commonMain`. Add the `RoomDatabaseConstructor` expect-object Room KMP requires, and keep entities/migrations. The two `Migration` objects use `SupportSQLiteDatabase.execSQL` which is available in KMP Room — keep them. Add:
```kotlin
// Room KMP requires this generated-constructor hook:
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseCtor : RoomDatabaseConstructor<AppDatabase>
```
and annotate the class `@ConstructedBy(AppDatabaseCtor::class)`.

- [ ] **Step 4: `expect`/`actual` builder**

`commonMain`:
```kotlin
expect fun appDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>
fun buildDatabase(): AppDatabase = appDatabaseBuilder()
    .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())
    .build()
```
`androidMain`:
```kotlin
actual fun appDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val ctx = /* injected Context */
    val dbFile = ctx.getDatabasePath("deckpuller.db")
    return Room.databaseBuilder<AppDatabase>(ctx, dbFile.absolutePath)
}
```
(Wire the Context via Koin: register `appDatabaseBuilder` behind a factory that takes `androidContext()`; simplest is to pass Context into a small android-only holder.)
`iosMain` (Phase 4):
```kotlin
actual fun appDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dir = NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, ...).path
    return Room.databaseBuilder<AppDatabase>("$dir/deckpuller.db")
}
```

- [ ] **Step 5: Update Koin DB registration** to `single { buildDatabase() }` and DAOs from it.

- [ ] **Step 6: Build + test (Android)**

```bash
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest
```
Expected: green. Room migration tests (`room-testing`) move to `androidUnitTest`. Verify the v2→3→4 migrations still apply (existing migration test must pass — this guards real users' on-device data).

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "refactor: migrate Room to Room KMP (bundled SQLite driver)"
```

### Task 2.4: Migrate DataStore to multiplatform

**Files:**
- Modify: `data/prefs/UserPreferences.kt` (→ `commonMain`, take `DataStore<Preferences>` as a ctor arg)
- Create: `expect`/`actual` `createDataStore()`

- [ ] **Step 1: Swap the dependency**

`datastore-preferences-core` is the KMP artifact:
```toml
datastore = "1.1.1"
datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }
```
`commonMain`: `implementation(libs.datastore.preferences.core)`.

- [ ] **Step 2: Make `UserPreferences` platform-free**

```kotlin
package com.deckpuller.data.prefs
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferences(private val dataStore: DataStore<Preferences>) {
    private val USERNAME = stringPreferencesKey("archidekt_username")
    private val IMPORTED_AT = longPreferencesKey("collection_imported_at")
    private val COUNT = intPreferencesKey("collection_count")
    val username: Flow<String?> = dataStore.data.map { it[USERNAME] }
    suspend fun setUsername(v: String) { dataStore.edit { it[USERNAME] = v } }
    val collectionImportedAt: Flow<Long?> = dataStore.data.map { it[IMPORTED_AT] }
    val collectionCount: Flow<Int> = dataStore.data.map { it[COUNT] ?: 0 }
    suspend fun setCollectionImported(ts: Long, count: Int) =
        dataStore.edit { it[IMPORTED_AT] = ts; it[COUNT] = count }
}
```
(Body is identical to today's logic — only the source of the `DataStore` changes. **Keep the file name `user_prefs` so existing users' settings survive.**)

- [ ] **Step 3: `expect`/`actual` factory**

`commonMain`: `expect fun createDataStore(): DataStore<Preferences>` — or pass a platform path. Android `actual` uses `PreferenceDataStoreFactory.createWithPath { context.filesDir.resolve("user_prefs.preferences_pb").absolutePath.toPath() }`. iOS `actual` (Phase 4) uses the NSDocument dir path. Register `single { UserPreferences(createDataStore()) }` in Koin (Context via androidContext on Android).

- [ ] **Step 4: Build + test, then commit**

```bash
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest
git add -A && git commit -m "refactor: migrate DataStore to multiplatform"
```

### Task 2.5: Migrate Coil 2 → Coil 3 (KMP) and the bitmap helper

**Files:**
- Modify: `data/image/ImagePrefetcher.kt`, `ui/pull/CardBitmap.kt`, every `AsyncImage` call site (e.g. `CardImageDialog`, collection/shopping thumbnails)

- [ ] **Step 1: Swap deps**

```toml
coil = "3.0.4"
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-ktor = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }
```
`commonMain`: both. Confirm latest Coil 3.x. Coil 3 uses Ktor for network (reuses Task 2.2's client).

- [ ] **Step 2: Update imports** `coil.compose.AsyncImage` → `coil3.compose.AsyncImage` across all call sites:
```bash
grep -rln "import coil\." shared/src/commonMain | xargs sed -i 's/import coil\./import coil3./g'
```
Verify each by eye (some symbols moved packages, e.g. `coil3.request.ImageRequest`, `coil3.size.Scale`).

- [ ] **Step 3: Rewrite `CardBitmap.loadCardBitmap`** to Coil 3's `ImageLoader.execute` returning a `coil3.Image`; convert to Compose `ImageBitmap` via `image.toBitmap().asImageBitmap()` (Coil 3 exposes a multiplatform `Image`). Keep `allowHardware(false)` semantics — on KMP this becomes the default software path; document that the card-burst overlay still gets a readable bitmap.

- [ ] **Step 4: Configure a singleton `ImageLoader`** with the Ktor network fetcher via `setSingletonImageLoaderFactory { … }` at the Compose root (replaces Android `ImageLoader(context)`), and make `ImagePrefetcher` use it. Move `ImagePrefetcher` to `commonMain`.

- [ ] **Step 5: Build + test, commit**

```bash
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest
git add -A && git commit -m "refactor: migrate Coil 2 -> Coil 3 (KMP image loading)"
```

### Task 2.6: Replace konfetti (Android-only) with a common particle overlay

**Files:**
- Modify: the screen(s) using `konfetti.compose` (the deck-completion celebration)
- Create: `shared/src/commonMain/.../ui/common/ConfettiOverlay.kt`

- [ ] **Step 1: Find usages**

```bash
grep -rln "konfetti\|nl.dionsegijn" shared/src
```

- [ ] **Step 2: Implement a small Compose-canvas confetti** in `commonMain` (no library): a `@Composable ConfettiOverlay(trigger: Boolean)` driving N particles with `Animatable` position/rotation over a `Canvas`. Keep the same colors/burst feel as the konfetti config it replaces (read the old config first and match `colors`, particle count, spread).

- [ ] **Step 3: Swap call sites** to `ConfettiOverlay(...)`, remove the konfetti dependency from Gradle.

- [ ] **Step 4: Build + test, commit**

```bash
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest
git add -A && git commit -m "refactor: replace konfetti with a multiplatform confetti overlay"
```

### Task 2.7: Move navigation to a KMP-safe approach

**Files:**
- Modify: `ui/AppRoot.kt` and any `androidx.navigation.compose` usage

- [ ] **Step 1: Check whether the pinned Compose-Multiplatform version bundles `org.jetbrains.androidx.navigation:navigation-compose`** (the KMP fork). If yes, swap the dependency coordinate and keep the nav graph as-is:
```toml
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version = "2.8.0-alpha10" }
```
Confirm the current KMP-nav version compatible with the pinned CMP. Update imports `androidx.navigation` (these are source-compatible with the JetBrains fork).

- [ ] **Step 2: Build + test, commit**

```bash
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest
git add -A && git commit -m "refactor: use multiplatform navigation-compose"
```

### Task 2.8: End-of-phase gate — `commonMain` is android-free except the parked platform files

- [ ] **Step 1: Confirm only the genuine platform files remain in `androidMain`**

```bash
grep -rln "import android\." shared/src/commonMain && echo "LEAK — fix before Phase 3" || echo "commonMain is android-free ✔"
ls shared/src/androidMain/kotlin/com/deckpuller/  # expect: platform/, di/AndroidModule, data/update/
```
Remaining `android.*` users should now be only: `PullFeedback`, `CardImageDialog` (sensor part), `CollectionImporter`, `ShoppingListScreen` (share), `UpdateManager`. These are Phase 3.

- [ ] **Step 2: Full Android regression**

```bash
./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest
```
Expected: green; test count == baseline. **Install the debug APK on a real Android device and smoke-test every screen** — this is the last point where Android-only changes are easy to bisect.

- [ ] **Step 3: Commit the gate**

```bash
git commit --allow-empty -m "chore: Phase 2 complete — libraries are KMP, commonMain android-free"
```

---

## Phase 3 — `expect`/`actual` platform behavior (iOS compiles run in CI, not locally)

> Add the iOS targets to `shared/build.gradle.kts` now (uncomment `iosArm64()`, `iosSimulatorArm64()`, and `iosMain`/`iosTest` source sets, plus the iOS Ktor/Room/KSP entries noted in Phase 2 tasks). Each task below provides the `commonMain` `expect`, the `androidMain` `actual` (lifted from today's code), and the `iosMain` `actual`.
>
> **No Mac locally:** the `./gradlew :shared:compileKotlinIos*` commands in each task's verify step **cannot run on this Linux box** — they need Kotlin/Native's Apple toolchain. Set up the CI macOS job from **Task 4.0 first**, then each Phase 3 task is "verified" when you push the branch and that CI job goes green. You can still author all the `iosMain` code here on Linux; you just lean on CI to compile it. The `:androidApp:assembleDebug` half of each verify step runs locally as normal and keeps Android safe.

### Task 3.1: Haptics + click sound (`PullFeedback` + `CardImageDialog` tick)

**Files:**
- Create: `commonMain/.../platform/Haptics.kt` (expect)
- Create: `androidMain/.../platform/Haptics.android.kt`, `iosMain/.../platform/Haptics.ios.kt`
- Modify: `ui/pull/PullScreen.kt` / `PullViewModel` call sites; `CardImageDialog` half-turn tick

- [ ] **Step 1: Define the common interface (maps to today's `PullFeedback` API)**

```kotlin
package com.deckpuller.platform
interface Haptics {
    fun pullClick()        // light tick on each pull (today: VibrationEffect.EFFECT_CLICK / CLOCK_TICK)
    fun cardComplete()     // heftier double-tap on completing a card
    fun faceFlipTick()     // crisp tick crossing a 90°/270° card-flip boundary
}
@Composable expect fun rememberHaptics(): Haptics
```

- [ ] **Step 2: Android `actual`** — wrap today's `PullFeedback` logic verbatim (Vibrator/VibrationEffect API 29+ with `View.performHapticFeedback` fallback, respecting `Settings.System.HAPTIC_FEEDBACK_ENABLED`). `rememberHaptics()` reads `LocalView.current`.

- [ ] **Step 3: iOS `actual`** — implement with `UIImpactFeedbackGenerator`/`UINotificationFeedbackGenerator`:
```kotlin
// iosMain
import platform.UIKit.*
class IosHaptics : Haptics {
    private val light = UIImpactFeedbackGenerator(UIImpactFeedbackStyleLight)
    private val rigid = UIImpactFeedbackGenerator(UIImpactFeedbackStyleRigid)
    private val notify = UINotificationFeedbackGenerator()
    override fun pullClick() { light.impactOccurred() }
    override fun cardComplete() { notify.notificationOccurred(UINotificationFeedbackTypeSuccess) }
    override fun faceFlipTick() { rigid.impactOccurred() }
}
@Composable actual fun rememberHaptics(): Haptics = remember { IosHaptics() }
```
(System click sound on iOS: optional `AudioServicesPlaySystemSound(1104u)` for the pull tick if you want parity with the Android touch sound.)

- [ ] **Step 4: Swap call sites** from `PullFeedback`/`view.performHapticFeedback(CLOCK_TICK)` to `haptics.faceFlipTick()` etc. `PullFeedback.kt` (androidMain) becomes the Android actual's internals.

- [ ] **Step 5: Verify Android still green; iOS compiles**

```bash
./gradlew :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64
```
Expected: both succeed. **Haptic *feel* is verified by hand on a physical iPhone in Phase 4** (the simulator has no haptics).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(ios): expect/actual haptics (UIKit feedback generators)"
```

### Task 3.2: Device-tilt sensor for the 3D card viewer (`CardImageDialog`)

**Files:**
- Create: `commonMain/.../platform/DeviceTilt.kt` (expect)
- Create: `androidMain` + `iosMain` actuals
- Modify: `ui/common/CardImageDialog.kt` — replace the `DisposableEffect` sensor block with `val tilt = rememberDeviceTilt()`

- [ ] **Step 1: Define the common hook returning smoothed (tiltX, tiltY) in degrees**

```kotlin
package com.deckpuller.platform
import androidx.compose.runtime.Composable
data class Tilt(val xDeg: Float, val yDeg: Float)   // pitch lean, roll lean
// Returns a Tilt that recenters toward the current hold (matches today's DEVICE_TILT_RECENTER),
// clamped to ±maxDegrees, low-passed. Emits (0,0) on devices without the sensor.
@Composable expect fun rememberDeviceTilt(maxDegrees: Float, gain: Float, recenter: Float, smoothing: Float): Tilt
```

- [ ] **Step 2: Android `actual`** — port today's `SensorManager` + `TYPE_GAME_ROTATION_VECTOR` (fallback `TYPE_ROTATION_VECTOR`) listener into a `DisposableEffect`, exposing the smoothed values as Compose state. The math (basePitch/baseRoll recenter, gain, clamp, low-pass) is copied verbatim from the current `CardImageDialog` — keep the constants identical so Android feel is unchanged.

- [ ] **Step 3: iOS `actual`** — `CMMotionManager` device-motion (`attitude.pitch`/`attitude.roll`, drift-free, gyro+accel fused, the CoreMotion analog of game-rotation-vector):
```kotlin
// iosMain
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
@Composable actual fun rememberDeviceTilt(maxDegrees: Float, gain: Float, recenter: Float, smoothing: Float): Tilt {
    var tilt by remember { mutableStateOf(Tilt(0f, 0f)) }
    DisposableEffect(Unit) {
        val mgr = CMMotionManager()
        var basePitch = Float.NaN; var baseRoll = Float.NaN
        var tx = 0f; var ty = 0f
        if (mgr.deviceMotionAvailable) {
            mgr.deviceMotionUpdateInterval = 1.0 / 60.0
            mgr.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { motion, _ ->
                val m = motion ?: return@startDeviceMotionUpdatesToQueue
                val pitchDeg = (m.attitude.pitch * 180.0 / PI).toFloat()
                val rollDeg = (m.attitude.roll * 180.0 / PI).toFloat()
                if (basePitch.isNaN()) { basePitch = pitchDeg; baseRoll = rollDeg }
                else { basePitch += (pitchDeg-basePitch)*recenter; baseRoll += (rollDeg-baseRoll)*recenter }
                val targetX = ((pitchDeg-basePitch)*gain).coerceIn(-maxDegrees, maxDegrees)
                val targetY = ((rollDeg-baseRoll)*gain).coerceIn(-maxDegrees, maxDegrees)
                tx = tx*smoothing + targetX*(1f-smoothing); ty = ty*smoothing + targetY*(1f-smoothing)
                tilt = Tilt(tx, ty)
            }
        }
        onDispose { mgr.stopDeviceMotionUpdates() }
    }
    return tilt
}
```
(Sign conventions for pitch/roll between Android `getOrientation` and CoreMotion `attitude` differ — be ready to flip a sign so the lean direction matches Android; verify on device in Phase 4.)

- [ ] **Step 3.5: Rewire `CardImageDialog`** — delete its `DisposableEffect` sensor block and `Context`/`SensorManager` imports; replace with:
```kotlin
val tilt = rememberDeviceTilt(MAX_DEVICE_TILT_DEGREES, DEVICE_TILT_GAIN, DEVICE_TILT_RECENTER, DEVICE_TILT_SMOOTHING)
// use tilt.xDeg / tilt.yDeg where tiltX/tiltY were used
```
`CardImageDialog.kt` now has no `android.*` imports → it moves to `commonMain`.

- [ ] **Step 4: Verify compiles both targets, commit**

```bash
./gradlew :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64
git add -A && git commit -m "feat(ios): expect/actual device tilt (CoreMotion); CardImageDialog -> commonMain"
```

### Task 3.3: CSV import file picking (`CollectionImporter`)

**Files:**
- Create: `commonMain/.../platform/FilePicker.kt` (expect)
- Create: `androidMain` + `iosMain` actuals
- Modify: `ui/settings/SettingsScreen.kt` (today's `rememberLauncher`/`GetContent` call site), `CollectionViewModel`, drop `CollectionImporter`'s `Uri` dependency

- [ ] **Step 1: Define a common "pick a CSV and give me its text" hook**

```kotlin
package com.deckpuller.platform
import androidx.compose.runtime.Composable
// Launches the platform document picker filtered to CSV/text; calls onText with file contents (or null if cancelled).
@Composable expect fun rememberCsvPicker(onText: (String?) -> Unit): () -> Unit
```

- [ ] **Step 2: Android `actual`** — wrap today's `ActivityResultContracts.GetContent()` (or `OpenDocument`) launcher; on result, read via the existing `CollectionImporter.readText(uri)` (kept in androidMain) and hand back the string.

- [ ] **Step 3: iOS `actual`** — present `UIDocumentPickerViewController(forOpeningContentTypes = [UTType.commaSeparatedText, UTType.plainText])`, read the picked URL's contents with `NSString.stringWithContentsOfURL`, return the string. Present from the current key window's root VC.

- [ ] **Step 4: Rewire SettingsScreen** to `val pick = rememberCsvPicker { text -> text?.let { viewModel.importCsv(it) } }` and call `pick()` from the import button. `CollectionViewModel.importCsv(text: String)` now takes the already-read text (parsing logic unchanged). `CollectionImporter`'s `Uri` API stays android-internal behind the actual.

- [ ] **Step 5: Compiles both targets, commit**

```bash
./gradlew :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64
git add -A && git commit -m "feat(ios): expect/actual CSV picker (UIDocumentPicker)"
```

### Task 3.4: Share sheet (`ShoppingListScreen`)

**Files:**
- Create: `commonMain/.../platform/ShareSheet.kt` (expect)
- Create: `androidMain` + `iosMain` actuals
- Modify: `ui/shopping/ShoppingListScreen.kt`

- [ ] **Step 1: Common hook**

```kotlin
package com.deckpuller.platform
@Composable expect fun rememberShare(): (text: String) -> Unit
```

- [ ] **Step 2: Android `actual`** — today's `Intent(ACTION_SEND)` with `type = "text/plain"` wrapped in a chooser.

- [ ] **Step 3: iOS `actual`** — `UIActivityViewController(activityItems = listOf(text))` presented from the root VC.

- [ ] **Step 4: Rewire ShoppingListScreen** to `val share = rememberShare(); ... share(listText)`. The screen loses its `android.content.Intent` import → moves to `commonMain`.

- [ ] **Step 5: Compiles both targets, commit**

```bash
./gradlew :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64
git add -A && git commit -m "feat(ios): expect/actual share sheet (UIActivityViewController)"
```

### Task 3.5: Stub the self-updater on iOS

**Files:**
- Create: `commonMain/.../platform/SelfUpdate.kt` (expect)
- Create: `androidMain` (`true`) + `iosMain` (`false`) actuals
- Modify: `ui/update/UpdateGate.kt`

- [ ] **Step 1: Common flag**

```kotlin
package com.deckpuller.platform
expect fun isSelfUpdateSupported(): Boolean
```
Android `actual` → `true`; iOS `actual` → `false`.

- [ ] **Step 2: Gate the UI** — in `UpdateGate.kt`, short-circuit when `!isSelfUpdateSupported()` (render children directly, never show the update prompt). `UpdateManager`/`UpdateViewModel` stay android-only (registered only in `androidModule`). The whole `ui/update` package stays in `androidMain` except the gate, which becomes a thin commonMain wrapper that no-ops on iOS.

- [ ] **Step 3: Compiles both targets, commit**

```bash
./gradlew :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64
git add -A && git commit -m "feat(ios): no-op self-update gate on platforms without APK install"
```

### Task 3.6: Define the shared root `App()` composable

**Files:**
- Create: `commonMain/.../App.kt`

- [ ] **Step 1: Extract the Compose entry point** that today lives implicitly in `MainActivity.setContent { … }` into a reusable composable both platforms call:
```kotlin
package com.deckpuller
@Composable
fun App() {
    KoinContext {                  // koin-compose: makes koinViewModel() resolve
        DeckPullerTheme {
            setSingletonImageLoaderFactory { ctx -> /* Coil3 + Ktor */ }
            AppRoot()              // existing nav root
        }
    }
}
```
Android `MainActivity.setContent { App() }`; iOS hosts `App()` via `ComposeUIViewController` (Phase 4).

- [ ] **Step 2: Build Android + iOS compile, commit**

```bash
./gradlew :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64
git add -A && git commit -m "refactor: shared App() composable entry point"
```

---

## Phase 4 — Build the `.ipa` in CI, sideload free with SideStore (no Mac, no $99)

This phase replaces the usual "open Xcode, sign, TestFlight" flow with: author the iOS app shell as **text files** on Linux → a **GitHub Actions macOS runner** compiles it into an **unsigned `.ipa`** artifact → **SideStore** on your iPhone re-signs it with your **free Apple ID** and installs it. The project is generated with **XcodeGen** (a `project.yml` → `.xcodeproj`) so you never need to touch Xcode's GUI.

> **Distribution ceiling (restating constraint #1):** this path installs only on *your* device(s), apps expire/re-sign every 7 days, max 3 sideloaded apps. It's for running your own build on your own phone. Anything beyond that = the $99 account, out of scope.

### Task 4.0: Stand up the CI macOS job FIRST (this is your "build machine")

Do this before the rest of Phase 3/4 verification, because it's how you compile anything for iOS.

**Files:**
- Create: `.github/workflows/ios.yml`
- Modify: `shared/build.gradle.kts` (framework config)

- [ ] **Step 1: Configure the shared framework output**

In `shared/build.gradle.kts`:
```kotlin
kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "Shared"; isStatic = true }
    }
}
```

- [ ] **Step 2: Add a macOS workflow that compiles the iOS framework on every push**

`.github/workflows/ios.yml`:
```yaml
name: iOS
on:
  push: { branches: [ ios-port-kmp, main ] }
  workflow_dispatch:
jobs:
  compile:
    runs-on: macos-14            # Apple-silicon runner, Xcode preinstalled
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - name: Compile shared for iOS (device + simulator)
        run: ./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64
```

- [ ] **Step 3: Push and confirm the runner goes green**

```bash
git add .github/workflows/ios.yml shared/build.gradle.kts
git commit -m "ci: macOS job compiles shared framework for iOS"
git push -u origin ios-port-kmp
```
Open the Actions tab → the `iOS / compile` job must succeed. **This green check is now the verification target for every Phase 3 `compileKotlinIos*` step.** If your repo is private, note the macOS-minute burn (10× Linux); make this job `workflow_dispatch`-only or push-batched to conserve minutes.

### Task 4.1: Author the iOS app shell as text (XcodeGen — no Xcode GUI)

**Files:**
- Create: `iosApp/project.yml` (XcodeGen spec), `iosApp/Sources/iOSApp.swift`, `iosApp/Sources/ContentView.swift`, `iosApp/Sources/Info.plist`
- Create: `shared/src/iosMain/kotlin/com/deckpuller/MainViewController.kt`, `shared/src/iosMain/kotlin/com/deckpuller/di/KoinIos.kt`

- [ ] **Step 1: Kotlin side — Compose host + Koin init in `iosMain`**

`MainViewController.kt`:
```kotlin
package com.deckpuller
import androidx.compose.ui.window.ComposeUIViewController
fun MainViewController() = ComposeUIViewController { App() }
```
`di/KoinIos.kt`:
```kotlin
package com.deckpuller.di
import org.koin.core.context.startKoin
fun doInitKoin() { startKoin { modules(sharedModule, iosModule) } }
```
(`iosModule` provides the iOS `actual` HttpClient engine, Room builder, and DataStore from Phase 2/3.)

- [ ] **Step 2: Swift side — SwiftUI hosting the Compose view**

`iosApp/Sources/iOSApp.swift`:
```swift
import SwiftUI
import Shared
@main
struct iOSApp: App {
    init() { KoinIosKt.doInitKoin() }
    var body: some Scene { WindowGroup { ContentView().ignoresSafeArea() } }
}
```
`iosApp/Sources/ContentView.swift`:
```swift
import SwiftUI
import Shared
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}
```

- [ ] **Step 3: The XcodeGen project spec**

`iosApp/project.yml`:
```yaml
name: DeckPuller
options:
  bundleIdPrefix: com
  deploymentTarget: { iOS: "15.0" }
targets:
  DeckPuller:
    type: application
    platform: iOS
    sources: [ Sources ]
    info:
      path: Sources/Info.plist
      properties:
        CFBundleDisplayName: DeckPuller
        NSMotionUsageDescription: "Used to tilt and shimmer cards as you move your phone."
        UILaunchScreen: {}
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.deckpuller
        CODE_SIGNING_ALLOWED: "NO"          # CI builds unsigned; SideStore signs later
        ENABLE_USER_SCRIPT_SANDBOXING: "NO" # let the gradle build-phase run
    preBuildScripts:
      - name: Build Kotlin framework
        script: |
          cd "$SRCROOT/.."
          ./gradlew :shared:embedAndSignAppleFrameworkForXcode
    dependencies:
      - framework: $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)/Shared.framework
        embed: true
```
(The exact framework path is what `embedAndSignAppleFrameworkForXcode` emits; if the link path differs on first build, read the gradle task's output dir and correct the `framework:` line. This is the one path most likely to need a tweak — verify against the CI build log.)

- [ ] **Step 4: Generate + build in CI (extend the workflow)**

Append a build job to `.github/workflows/ios.yml`:
```yaml
  ipa:
    runs-on: macos-14
    needs: compile
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - run: brew install xcodegen
      - run: cd iosApp && xcodegen generate
      - name: Build unsigned .app
        run: |
          xcodebuild -project iosApp/DeckPuller.xcodeproj -scheme DeckPuller \
            -configuration Release -sdk iphoneos -derivedDataPath build \
            CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
      - name: Package unsigned .ipa
        run: |
          mkdir -p Payload
          cp -R build/Build/Products/Release-iphoneos/DeckPuller.app Payload/
          zip -r DeckPuller-unsigned.ipa Payload
      - uses: actions/upload-artifact@v4
        with: { name: DeckPuller-unsigned-ipa, path: DeckPuller-unsigned.ipa }
```

- [ ] **Step 5: Push, confirm the `ipa` job uploads an artifact**

```bash
git add -A && git commit -m "feat(ios): XcodeGen shell + CI builds unsigned .ipa artifact"
git push
```
Expected: Actions → `iOS / ipa` green, with `DeckPuller-unsigned-ipa` downloadable from the run. **This `.ipa` is what you sideload.**

### Task 4.2: App icon + launch screen (text/asset files, committed)

**Files:**
- Create: `iosApp/Sources/Assets.xcassets/AppIcon.appiconset/` (PNGs from the existing Android icon art) + `Contents.json`
- Modify: `iosApp/project.yml` (already declares `UILaunchScreen: {}` for a plain launch screen)

- [ ] **Step 1: Export the app-icon PNGs** from the existing Android adaptive icon art at the iOS sizes (1024 marketing + the standard set) into the `.appiconset` with a valid `Contents.json`. Generate the set with any icon-set tool locally (no Mac needed — it's just resized PNGs + JSON).
- [ ] **Step 2: Point XcodeGen at the asset catalog** (`sources: [ Sources ]` already includes it; ensure `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon` in `settings.base`).
- [ ] **Step 3: Push; confirm the `ipa` job still builds.** Commit.
```bash
git add -A && git commit -m "feat(ios): app icon + launch screen"
git push
```

### Task 4.3: Install on your iPhone with SideStore (free Apple ID)

**Files:**
- Create: `docs/IOS_SIDELOAD.md`

This is the one-time, on-device part. SideStore re-signs the CI `.ipa` with a free Apple ID and auto-refreshes it before the 7-day expiry.

- [ ] **Step 1: Write `docs/IOS_SIDELOAD.md`** with the procedure:

```markdown
# Installing DeckPuller on iPhone via SideStore (free, no Developer account)

## What you need
- An iPhone (iOS 15+) and a **free Apple ID** (a throwaway one is wise — it gets
  tied to app signing).
- The latest **DeckPuller-unsigned.ipa** from GitHub Actions
  (Actions → latest `iOS` run → Artifacts → `DeckPuller-unsigned-ipa`).

## One-time SideStore setup
SideStore boots itself onto the phone using a **device pairing file** and a
background "refresh" trick (no always-on computer required afterward).
Follow the official installer — it changes often, so don't hand-copy steps:
  → https://sidestore.io/  (Get Started / Install guide)
The setup produces a working SideStore app on your home screen signed with your
free Apple ID.

## Installing DeckPuller
1. Download `DeckPuller-unsigned.ipa` to the iPhone (Files app, AirDrop, or a link).
2. Open **SideStore → My Apps → + → pick the .ipa**.
3. SideStore signs it with your Apple ID and installs it. First launch:
   Settings → General → VPN & Device Management → trust your Apple ID.

## Keeping it alive (the 7-day reality)
- Free-signed apps expire after 7 days. SideStore **auto-refreshes** them while
  it can reach the phone over its local refresh connection — open SideStore every
  few days if an app ever shows as expired and tap **Refresh All**.
- Free Apple IDs allow **3 sideloaded apps** at once. SideStore itself counts as
  one, so keep slots free.
- New build? Re-download the latest artifact and repeat "Installing" — it updates
  in place (same bundle id `com.deckpuller`), keeping your decks/collection data.

## What this does NOT do
- No sharing with other people (they'd sideload with their own Apple ID).
- No push notifications. No App Store listing. No in-app self-update (Android-only).
For any of those, enroll in the Apple Developer Program ($99/yr).
```

- [ ] **Step 2: Commit the doc**
```bash
git add docs/IOS_SIDELOAD.md && git commit -m "docs: free SideStore sideload guide"
git push
```

### Task 4.4: Device pass — verify feel and the sensor/haptic actuals

The simulator can't do haptics or CoreMotion, and you have no Mac to run it anyway — so a **physical iPhone running the sideloaded build is your only feel-test**. This is the real verification gate for Phase 3's iOS actuals.

- [ ] **Step 1: Install the latest CI `.ipa` via SideStore** (Task 4.3) and verify screen-by-screen against Android:
  - Pull loop: counter, pull haptic on each pull, deck-completion confetti + `cardComplete()` haptic.
  - 3D card viewer: drag-to-spin/flip, vertical tilt spring-back, **gyro lean direction matches Android** (if mirrored, flip the pitch/roll sign in the Task 3.2 iOS `actual` and rebuild), foil sheen tracks motion, half-turn `faceFlipTick()` fires.
  - Collection: CSV import via the Files picker (`UIDocumentPicker`); foil shimmer on thumbnails.
  - Shopping list: share sheet (`UIActivityViewController`) opens.
  - Settings: Archidekt username persists across relaunch (DataStore actual).
  - My Decks: import a deck (Ktor + Room round-trip), data survives a relaunch and a reinstall.
- [ ] **Step 2: For each delta, fix the relevant Phase 3 iOS `actual`, push, let CI rebuild the `.ipa`, re-sideload, re-check.** Commit fixes individually. The loop is "edit on Linux → push → CI builds ipa → SideStore installs → test on phone" — slower than a local Xcode run-loop, so batch changes per push.

### Task 4.5: Make the iOS CI job a first-class check

**Files:**
- Modify: `.github/workflows/ios.yml`

- [ ] **Step 1: Once the `compile` + `ipa` jobs are reliably green, mark `iOS / compile` a required check** on the branch (Settings → Branches) so an iOS-breaking change can't merge. Keep `ipa` artifact-only (not required) if macOS minutes are a concern on a private repo.
- [ ] **Step 2: Commit any workflow tweaks**
```bash
git add .github && git commit -m "ci: make iOS compile a required check"
git push
```

---

## Final verification checklist (run before merging the branch)

- [ ] `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest` green locally; test count == Phase 0 baseline.
- [ ] CI `iOS / compile` job green (`:shared:compileKotlinIosArm64` + `…SimulatorArm64`).
- [ ] CI `iOS / ipa` job green and `DeckPuller-unsigned-ipa` artifact downloads.
- [ ] Android release build installs and every screen behaves exactly as before the port (no regressions from the restructure).
- [ ] The sideloaded `.ipa` (SideStore, free Apple ID) passes the Task 4.4 device smoke test on a physical iPhone.
- [ ] `grep -rln "import android\." shared/src/commonMain` returns nothing.
- [ ] Self-update is absent on iOS and unchanged on Android.
- [ ] Existing Android users' data survives an in-place update (Room migrations v2→4 + DataStore `user_prefs` key names unchanged).

---

## Notes for the executor

- **Order matters within Phase 2** only loosely; Ktor (2.2) must land before Coil 3 (2.5, shares the client) and before iOS networking. DI (2.1) first makes the rest easier to wire.
- **The riskiest tasks are 3.1 and 3.2** (haptic/gyro *feel*), verifiable only on a physical iPhone — budget iteration there.
- **You have no Mac:** every iOS compile/build runs on the CI macOS runner (Task 4.0). Don't try `compileKotlinIos*` on Linux — it needs Kotlin/Native's Apple toolchain. The dev loop for iOS bits is "edit on Linux → push → CI builds the `.ipa` → SideStore installs it → test on phone," which is slower than a local Xcode run, so batch iOS changes per push.
- **The single fiddliest line** in the whole iOS shell is the framework path in `iosApp/project.yml` (Task 4.1 Step 3). If the first CI build can't find `Shared.framework`, read the `embedAndSignAppleFrameworkForXcode` output path from the build log and correct that one line.
- **SideStore's own install is a one-time bootstrap** (pairing file + refresh setup) that lives outside this repo — `docs/IOS_SIDELOAD.md` points at the official guide rather than pinning steps that drift.
- If a pinned library version turns out incompatible with the chosen Kotlin/CMP, bump the whole Kotlin+CMP+KSP trio together (they are version-locked) rather than one in isolation.
