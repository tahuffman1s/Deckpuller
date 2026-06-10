# MTG Deck Puller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app that imports an Archidekt decklist by URL and lets you check off each physical MTG card as you pull it (with multi-copy counters), celebrating and clearing the list when the deck is complete.

**Architecture:** Native Kotlin + Jetpack Compose (Material 3), MVVM with a layered data tier (Retrofit remote APIs → repository → Room). One deck at a time. The visible screen is driven reactively by whether a deck exists in Room (no navigation library). Card data comes from Archidekt's deck API plus a batched Scryfall `/cards/collection` lookup; images are prefetched into Coil's disk cache for offline use.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.01) + Material 3, Hilt 2.52 (KSP), Retrofit 2.11.0 + OkHttp 4.12.0 + kotlinx.serialization 1.7.3, Room 2.6.1 (KSP), Coil 2.7.0, konfetti-compose 2.0.4, Coroutines 1.9.0. Tests: JUnit4, Robolectric 4.14, coroutines-test, Turbine.

**Conventions used throughout:**
- Package root: `com.deckpuller`
- Run unit/Robolectric tests with: `./gradlew testDebugUnitTest`
- Build the app with: `./gradlew assembleDebug`
- All commits use Conventional Commit prefixes (`feat:`, `test:`, `chore:`).

---

## File Structure

```
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/java/com/deckpuller/
  DeckPullerApp.kt                 # Hilt @HiltAndroidApp Application
  MainActivity.kt                  # @AndroidEntryPoint, sets Compose content
  ui/
    AppRoot.kt                     # picks Import vs Pull based on deck presence
    MainViewModel.kt               # exposes hasDeck: StateFlow<Boolean?>
    theme/Theme.kt                 # Material3 theme
    import/ImportScreen.kt
    import/ImportViewModel.kt
    pull/PullScreen.kt
    pull/CardRow.kt
    pull/PullViewModel.kt
    pull/CelebrationOverlay.kt
  domain/
    model/Deck.kt                  # Deck, DeckCard
    model/CardGroup.kt
    CardTypeClassifier.kt
    DeckGrouping.kt
  data/
    remote/ArchidektApi.kt
    remote/ScryfallApi.kt
    remote/dto/ArchidektDto.kt
    remote/dto/ScryfallDto.kt
    local/AppDatabase.kt
    local/DeckDao.kt
    local/entity/DeckEntity.kt
    local/entity/CardEntity.kt
    local/entity/DeckWithCards.kt
    image/ImagePrefetcher.kt       # interface + CoilImagePrefetcher
    repository/DeckRepository.kt   # interface
    repository/DefaultDeckRepository.kt
    Mappers.kt                     # DTO/entity <-> domain
    InvalidDeckUrlException.kt
    ArchidektUrlParser.kt
  di/
    NetworkModule.kt
    DatabaseModule.kt
    RepositoryModule.kt
app/src/test/java/com/deckpuller/   # JVM + Robolectric tests mirror packages
```

---

### Task 1: Project scaffold & build configuration

Creates a buildable, empty Compose app. No business logic yet.

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/deckpuller/DeckPullerApp.kt`
- Create: `app/src/main/java/com/deckpuller/MainActivity.kt`
- Create: `app/src/main/java/com/deckpuller/ui/theme/Theme.kt`
- Create: `.gitignore`

- [ ] **Step 1: Create `.gitignore`**

```gitignore
*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
app/build
```

- [ ] **Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
hilt = "2.52"
room = "2.6.1"
retrofit = "2.11.0"
okhttp = "4.12.0"
serialization = "1.7.3"
coroutines = "1.9.0"
coil = "2.7.0"
konfetti = "2.0.4"
composeBom = "2024.10.01"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
coreKtx = "1.13.1"
junit = "4.13.2"
robolectric = "4.14"
androidxTestCore = "1.6.1"
turbine = "1.1.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
compose-material3 = { module = "androidx.compose.material3:material3" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version = "1.2.0" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-serialization = { module = "com.squareup.retrofit2:converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
konfetti-compose = { module = "nl.dionsegijn:konfetti-compose", version.ref = "konfetti" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DeckPuller"
include(":app")
```

- [ ] **Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

- [ ] **Step 5: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 6: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.deckpuller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.deckpuller"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.konfetti.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

- [ ] **Step 7: Create `app/proguard-rules.pro`** (empty placeholder is fine)

```proguard
# Keep kotlinx.serialization generated serializers (handled by plugin; no custom rules needed for debug).
```

- [ ] **Step 8: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".DeckPullerApp"
        android:allowBackup="true"
        android:label="DeckPuller"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 9: Create `app/src/main/java/com/deckpuller/DeckPullerApp.kt`**

```kotlin
package com.deckpuller

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DeckPullerApp : Application()
```

- [ ] **Step 10: Create `app/src/main/java/com/deckpuller/ui/theme/Theme.kt`**

```kotlin
package com.deckpuller.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun DeckPullerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}
```

- [ ] **Step 11: Create `app/src/main/java/com/deckpuller/MainActivity.kt`** (temporary placeholder UI; replaced in Task 15)

```kotlin
package com.deckpuller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.deckpuller.ui.theme.DeckPullerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeckPullerTheme {
                Surface {
                    Text("DeckPuller")
                }
            }
        }
    }
}
```

- [ ] **Step 12: Generate the Gradle wrapper**

Run: `gradle wrapper --gradle-version 8.10.2`
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.
(If `gradle` is not installed, open the project once in Android Studio to generate the wrapper.)

- [ ] **Step 13: Build to verify the scaffold compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 14: Commit**

```bash
git add -A
git commit -m "chore: scaffold Compose + Hilt Android project"
```

---

### Task 2: Archidekt URL parser

Pure function that extracts a numeric deck ID from an Archidekt URL.

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/ArchidektUrlParser.kt`
- Test: `app/src/test/java/com/deckpuller/data/ArchidektUrlParserTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.deckpuller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchidektUrlParserTest {

    @Test
    fun `extracts id from full url with deck name`() {
        val id = ArchidektUrlParser.parseDeckId("https://archidekt.com/decks/1234567/my-cool-deck")
        assertEquals("1234567", id)
    }

    @Test
    fun `extracts id from url without trailing name`() {
        val id = ArchidektUrlParser.parseDeckId("https://archidekt.com/decks/9988776")
        assertEquals("9988776", id)
    }

    @Test
    fun `extracts id ignoring query params and trailing slash`() {
        val id = ArchidektUrlParser.parseDeckId("archidekt.com/decks/42/?tab=view")
        assertEquals("42", id)
    }

    @Test
    fun `returns null for url with no deck id`() {
        assertNull(ArchidektUrlParser.parseDeckId("https://archidekt.com/search/decks"))
    }

    @Test
    fun `returns null for blank input`() {
        assertNull(ArchidektUrlParser.parseDeckId("   "))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.data.ArchidektUrlParserTest"`
Expected: FAIL — `ArchidektUrlParser` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.deckpuller.data

object ArchidektUrlParser {
    private val DECK_ID = Regex("""decks/(\d+)""")

    fun parseDeckId(input: String): String? =
        DECK_ID.find(input)?.groupValues?.get(1)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.data.ArchidektUrlParserTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/ArchidektUrlParser.kt app/src/test/java/com/deckpuller/data/ArchidektUrlParserTest.kt
git commit -m "feat: parse Archidekt deck id from url"
```

---

### Task 3: Card type classifier & deck grouping

Pure functions: derive a card's primary type from its Scryfall `type_line`, and group/sort a card list for display. Depends on the `DeckCard` domain type, which is created here (it has no dependencies).

**Files:**
- Create: `app/src/main/java/com/deckpuller/domain/model/Deck.kt`
- Create: `app/src/main/java/com/deckpuller/domain/model/CardGroup.kt`
- Create: `app/src/main/java/com/deckpuller/domain/CardTypeClassifier.kt`
- Create: `app/src/main/java/com/deckpuller/domain/DeckGrouping.kt`
- Test: `app/src/test/java/com/deckpuller/domain/CardTypeClassifierTest.kt`
- Test: `app/src/test/java/com/deckpuller/domain/DeckGroupingTest.kt`

- [ ] **Step 1: Create the domain models**

`app/src/main/java/com/deckpuller/domain/model/Deck.kt`:

```kotlin
package com.deckpuller.domain.model

data class Deck(
    val name: String,
    val cards: List<DeckCard>,
)

data class DeckCard(
    val id: Long,
    val scryfallId: String,
    val name: String,
    val typeLine: String,
    val imageUrl: String?,
    val requiredQty: Int,
    val pulledQty: Int,
) {
    val isComplete: Boolean get() = pulledQty >= requiredQty
}
```

`app/src/main/java/com/deckpuller/domain/model/CardGroup.kt`:

```kotlin
package com.deckpuller.domain.model

data class CardGroup(
    val type: String,
    val cards: List<DeckCard>,
)
```

- [ ] **Step 2: Write the failing classifier test**

`app/src/test/java/com/deckpuller/domain/CardTypeClassifierTest.kt`:

```kotlin
package com.deckpuller.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CardTypeClassifierTest {

    @Test
    fun `classifies a simple creature`() {
        assertEquals("Creature", CardTypeClassifier.primaryType("Legendary Creature — Elf Druid"))
    }

    @Test
    fun `artifact creature classified as creature`() {
        assertEquals("Creature", CardTypeClassifier.primaryType("Artifact Creature — Golem"))
    }

    @Test
    fun `classifies a land`() {
        assertEquals("Land", CardTypeClassifier.primaryType("Basic Land — Forest"))
    }

    @Test
    fun `classifies an instant`() {
        assertEquals("Instant", CardTypeClassifier.primaryType("Instant"))
    }

    @Test
    fun `uses front face of a modal double-faced type line`() {
        assertEquals("Creature", CardTypeClassifier.primaryType("Creature — Elf // Land — Forest"))
    }

    @Test
    fun `blank type line is Unknown`() {
        assertEquals("Unknown", CardTypeClassifier.primaryType(""))
        assertEquals("Unknown", CardTypeClassifier.primaryType(null))
    }

    @Test
    fun `unrecognized type line is Other`() {
        assertEquals("Other", CardTypeClassifier.primaryType("Dungeon"))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.domain.CardTypeClassifierTest"`
Expected: FAIL — `CardTypeClassifier` unresolved.

- [ ] **Step 4: Implement `CardTypeClassifier`**

`app/src/main/java/com/deckpuller/domain/CardTypeClassifier.kt`:

```kotlin
package com.deckpuller.domain

object CardTypeClassifier {
    // Order = display/priority order. First match in the (pre-dash, front-face)
    // segment of the type line wins, so "Artifact Creature" classifies as Creature.
    val TYPE_ORDER = listOf(
        "Creature",
        "Planeswalker",
        "Instant",
        "Sorcery",
        "Artifact",
        "Enchantment",
        "Battle",
        "Land",
    )

    fun primaryType(typeLine: String?): String {
        if (typeLine.isNullOrBlank()) return "Unknown"
        val front = typeLine.substringBefore("//").substringBefore("—")
        return TYPE_ORDER.firstOrNull { front.contains(it, ignoreCase = true) } ?: "Other"
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.domain.CardTypeClassifierTest"`
Expected: PASS (7 tests).

- [ ] **Step 6: Write the failing grouping test**

`app/src/test/java/com/deckpuller/domain/DeckGroupingTest.kt`:

```kotlin
package com.deckpuller.domain

import com.deckpuller.domain.model.DeckCard
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckGroupingTest {

    private fun card(name: String, typeLine: String) = DeckCard(
        id = 0,
        scryfallId = name,
        name = name,
        typeLine = typeLine,
        imageUrl = null,
        requiredQty = 1,
        pulledQty = 0,
    )

    @Test
    fun `groups by primary type and sorts cards by name within a group`() {
        val cards = listOf(
            card("Llanowar Elves", "Creature — Elf Druid"),
            card("Birds of Paradise", "Creature — Bird"),
            card("Forest", "Basic Land — Forest"),
        )

        val groups = DeckGrouping.group(cards)

        assertEquals(listOf("Creature", "Land"), groups.map { it.type })
        assertEquals(
            listOf("Birds of Paradise", "Llanowar Elves"),
            groups.first { it.type == "Creature" }.cards.map { it.name },
        )
    }

    @Test
    fun `groups are ordered by the canonical type order with Other and Unknown last`() {
        val cards = listOf(
            card("Mystery", "Dungeon"),
            card("Sol Ring", "Artifact"),
            card("Shock", "Instant"),
        )

        val groups = DeckGrouping.group(cards)

        assertEquals(listOf("Instant", "Artifact", "Other"), groups.map { it.type })
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.domain.DeckGroupingTest"`
Expected: FAIL — `DeckGrouping` unresolved.

- [ ] **Step 8: Implement `DeckGrouping`**

`app/src/main/java/com/deckpuller/domain/DeckGrouping.kt`:

```kotlin
package com.deckpuller.domain

import com.deckpuller.domain.model.CardGroup
import com.deckpuller.domain.model.DeckCard

object DeckGrouping {
    private val ORDER = CardTypeClassifier.TYPE_ORDER + listOf("Other", "Unknown")

    fun group(cards: List<DeckCard>): List<CardGroup> =
        cards
            .groupBy { CardTypeClassifier.primaryType(it.typeLine) }
            .map { (type, groupCards) -> CardGroup(type, groupCards.sortedBy { it.name }) }
            .sortedBy { group ->
                ORDER.indexOf(group.type).let { if (it == -1) ORDER.size else it }
            }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.domain.DeckGroupingTest"`
Expected: PASS (2 tests).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/deckpuller/domain app/src/test/java/com/deckpuller/domain
git commit -m "feat: classify card types and group decks for display"
```

---

### Task 4: Remote DTOs, JSON config & API interfaces

DTOs for Archidekt and Scryfall, a configured `Json`, and Retrofit interfaces. Tests prove the DTOs deserialize real-shaped JSON (including the double-faced image fallback).

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/remote/dto/ArchidektDto.kt`
- Create: `app/src/main/java/com/deckpuller/data/remote/dto/ScryfallDto.kt`
- Create: `app/src/main/java/com/deckpuller/data/remote/ArchidektApi.kt`
- Create: `app/src/main/java/com/deckpuller/data/remote/ScryfallApi.kt`
- Test: `app/src/test/java/com/deckpuller/data/remote/DtoParsingTest.kt`

- [ ] **Step 1: Create the Archidekt DTOs**

`app/src/main/java/com/deckpuller/data/remote/dto/ArchidektDto.kt`:

```kotlin
package com.deckpuller.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArchidektDeckDto(
    val name: String,
    val cards: List<ArchidektCardDto>,
)

@Serializable
data class ArchidektCardDto(
    val quantity: Int,
    val card: ArchidektCardDetailDto,
)

@Serializable
data class ArchidektCardDetailDto(
    val uid: String,
    val oracleCard: ArchidektOracleCardDto,
)

@Serializable
data class ArchidektOracleCardDto(
    val name: String,
)
```

- [ ] **Step 2: Create the Scryfall DTOs**

`app/src/main/java/com/deckpuller/data/remote/dto/ScryfallDto.kt`:

```kotlin
package com.deckpuller.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScryfallCollectionRequest(
    val identifiers: List<ScryfallIdentifier>,
)

@Serializable
data class ScryfallIdentifier(
    val id: String,
)

@Serializable
data class ScryfallCollectionResponse(
    val data: List<ScryfallCardDto> = emptyList(),
    @SerialName("not_found") val notFound: List<ScryfallIdentifier> = emptyList(),
)

@Serializable
data class ScryfallCardDto(
    val id: String,
    val name: String,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("image_uris") val imageUris: ScryfallImageUris? = null,
    @SerialName("card_faces") val cardFaces: List<ScryfallCardFaceDto> = emptyList(),
) {
    /** Top-level type line, falling back to the front face for double-faced cards. */
    fun bestTypeLine(): String? = typeLine ?: cardFaces.firstOrNull()?.typeLine

    /** Normal image, falling back to the front face for double-faced cards. */
    fun bestImageUrl(): String? =
        imageUris?.normal ?: cardFaces.firstOrNull()?.imageUris?.normal
}

@Serializable
data class ScryfallCardFaceDto(
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("image_uris") val imageUris: ScryfallImageUris? = null,
)

@Serializable
data class ScryfallImageUris(
    val small: String? = null,
    val normal: String? = null,
)
```

- [ ] **Step 3: Create the API interfaces**

`app/src/main/java/com/deckpuller/data/remote/ArchidektApi.kt`:

```kotlin
package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ArchidektDeckDto
import retrofit2.http.GET
import retrofit2.http.Path

interface ArchidektApi {
    @GET("decks/{id}/")
    suspend fun getDeck(@Path("id") deckId: String): ArchidektDeckDto
}
```

`app/src/main/java/com/deckpuller/data/remote/ScryfallApi.kt`:

```kotlin
package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ScryfallCollectionRequest
import com.deckpuller.data.remote.dto.ScryfallCollectionResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ScryfallApi {
    @POST("cards/collection")
    suspend fun getCollection(
        @Body request: ScryfallCollectionRequest,
    ): ScryfallCollectionResponse
}
```

- [ ] **Step 4: Write the failing DTO parsing test**

`app/src/test/java/com/deckpuller/data/remote/DtoParsingTest.kt`:

```kotlin
package com.deckpuller.data.remote

import com.deckpuller.data.remote.dto.ArchidektDeckDto
import com.deckpuller.data.remote.dto.ScryfallCollectionResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DtoParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses archidekt deck ignoring unknown fields`() {
        val payload = """
        {
          "id": 42,
          "name": "My Deck",
          "private": false,
          "cards": [
            {
              "id": 1, "quantity": 4, "modifier": "Normal",
              "card": {
                "id": 9, "uid": "abc-123", "rarity": "common",
                "oracleCard": { "name": "Llanowar Elves", "types": ["Creature"] }
              }
            }
          ]
        }
        """.trimIndent()

        val deck = json.decodeFromString<ArchidektDeckDto>(payload)

        assertEquals("My Deck", deck.name)
        assertEquals(1, deck.cards.size)
        assertEquals(4, deck.cards[0].quantity)
        assertEquals("abc-123", deck.cards[0].card.uid)
        assertEquals("Llanowar Elves", deck.cards[0].card.oracleCard.name)
    }

    @Test
    fun `parses scryfall single-faced card`() {
        val payload = """
        {
          "data": [
            {
              "id": "abc-123",
              "name": "Llanowar Elves",
              "type_line": "Creature — Elf Druid",
              "image_uris": { "small": "s.jpg", "normal": "n.jpg" }
            }
          ],
          "not_found": []
        }
        """.trimIndent()

        val resp = json.decodeFromString<ScryfallCollectionResponse>(payload)
        val card = resp.data.single()

        assertEquals("Creature — Elf Druid", card.bestTypeLine())
        assertEquals("n.jpg", card.bestImageUrl())
        assertEquals(0, resp.notFound.size)
    }

    @Test
    fun `parses scryfall double-faced card using front face for type and image`() {
        val payload = """
        {
          "data": [
            {
              "id": "dfc-1",
              "name": "Front // Back",
              "card_faces": [
                { "type_line": "Creature — Werewolf", "image_uris": { "normal": "front.jpg" } },
                { "type_line": "Creature — Werewolf", "image_uris": { "normal": "back.jpg" } }
              ]
            }
          ]
        }
        """.trimIndent()

        val card = json.decodeFromString<ScryfallCollectionResponse>(payload).data.single()

        assertEquals("Creature — Werewolf", card.bestTypeLine())
        assertEquals("front.jpg", card.bestImageUrl())
        assertNull(card.imageUris)
    }
}
```

- [ ] **Step 5: Run test to verify it fails, then passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.data.remote.DtoParsingTest"`
Expected: After Steps 1–3 the classes exist, so this should PASS (3 tests). If you wrote the test first it FAILS with unresolved references; either order is fine as long as it ends green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/remote app/src/test/java/com/deckpuller/data/remote
git commit -m "feat: add Archidekt and Scryfall DTOs and API interfaces"
```

---

### Task 5: Room database, entities & DAO

Persistence for the single current deck and its cards. DAO behavior is verified with Robolectric + in-memory Room.

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/local/entity/DeckEntity.kt`
- Create: `app/src/main/java/com/deckpuller/data/local/entity/CardEntity.kt`
- Create: `app/src/main/java/com/deckpuller/data/local/entity/DeckWithCards.kt`
- Create: `app/src/main/java/com/deckpuller/data/local/DeckDao.kt`
- Create: `app/src/main/java/com/deckpuller/data/local/AppDatabase.kt`
- Test: `app/src/test/java/com/deckpuller/data/local/DeckDaoTest.kt`

- [ ] **Step 1: Create the entities**

`app/src/main/java/com/deckpuller/data/local/entity/DeckEntity.kt`:

```kotlin
package com.deckpuller.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Only one deck exists at a time, so the row id is fixed.
const val CURRENT_DECK_ID = 1L

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: Long = CURRENT_DECK_ID,
    val name: String,
    val importedAt: Long,
)
```

`app/src/main/java/com/deckpuller/data/local/entity/CardEntity.kt`:

```kotlin
package com.deckpuller.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deckId")],
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val scryfallId: String,
    val name: String,
    val typeLine: String,
    val imageUrl: String?,
    val requiredQty: Int,
    val pulledQty: Int,
)
```

`app/src/main/java/com/deckpuller/data/local/entity/DeckWithCards.kt`:

```kotlin
package com.deckpuller.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class DeckWithCards(
    @Embedded val deck: DeckEntity,
    @Relation(parentColumn = "id", entityColumn = "deckId")
    val cards: List<CardEntity>,
)
```

- [ ] **Step 2: Create the DAO**

`app/src/main/java/com/deckpuller/data/local/DeckDao.kt`:

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
    @Query("SELECT * FROM decks LIMIT 1")
    fun observeDeck(): Flow<DeckWithCards?>

    @Insert
    suspend fun insertDeck(deck: DeckEntity)

    @Insert
    suspend fun insertCards(cards: List<CardEntity>)

    @Query("DELETE FROM decks")
    suspend fun clearDecks()

    @Query("UPDATE cards SET pulledQty = :pulled WHERE id = :cardId")
    suspend fun updatePulled(cardId: Long, pulled: Int)

    @Transaction
    suspend fun replaceDeck(deck: DeckEntity, cards: List<CardEntity>) {
        clearDecks()
        insertDeck(deck)
        insertCards(cards)
    }
}
```

- [ ] **Step 3: Create the database**

`app/src/main/java/com/deckpuller/data/local/AppDatabase.kt`:

```kotlin
package com.deckpuller.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity

@Database(
    entities = [DeckEntity::class, CardEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
}
```

- [ ] **Step 4: Write the failing DAO test**

`app/src/test/java/com/deckpuller/data/local/DeckDaoTest.kt`:

```kotlin
package com.deckpuller.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.deckpuller.data.local.entity.CURRENT_DECK_ID
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

    private fun card(name: String, required: Int, pulled: Int = 0) = CardEntity(
        deckId = CURRENT_DECK_ID,
        scryfallId = name,
        name = name,
        typeLine = "Creature",
        imageUrl = null,
        requiredQty = required,
        pulledQty = pulled,
    )

    @Test
    fun `observeDeck emits null when empty`() = runTest {
        assertNull(dao.observeDeck().first())
    }

    @Test
    fun `replaceDeck stores deck with its cards`() = runTest {
        dao.replaceDeck(
            DeckEntity(name = "Deck A", importedAt = 1L),
            listOf(card("Forest", 4), card("Sol Ring", 1)),
        )

        val stored = dao.observeDeck().first()!!
        assertEquals("Deck A", stored.deck.name)
        assertEquals(2, stored.cards.size)
    }

    @Test
    fun `replaceDeck wipes the previous deck`() = runTest {
        dao.replaceDeck(DeckEntity(name = "Old", importedAt = 1L), listOf(card("Forest", 1)))
        dao.replaceDeck(DeckEntity(name = "New", importedAt = 2L), listOf(card("Island", 1)))

        val stored = dao.observeDeck().first()!!
        assertEquals("New", stored.deck.name)
        assertEquals(1, stored.cards.size)
        assertEquals("Island", stored.cards.single().name)
    }

    @Test
    fun `updatePulled changes the pulled count and emits`() = runTest {
        dao.replaceDeck(DeckEntity(name = "Deck", importedAt = 1L), listOf(card("Forest", 4)))
        val cardId = dao.observeDeck().first()!!.cards.single().id

        dao.observeDeck().test {
            assertEquals(0, awaitItem()!!.cards.single().pulledQty)
            dao.updatePulled(cardId, 3)
            assertEquals(3, awaitItem()!!.cards.single().pulledQty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearDecks removes everything`() = runTest {
        dao.replaceDeck(DeckEntity(name = "Deck", importedAt = 1L), listOf(card("Forest", 1)))
        dao.clearDecks()
        assertNull(dao.observeDeck().first())
    }
}
```

- [ ] **Step 5: Run test to verify it fails (before Steps 1–3 written) then passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.data.local.DeckDaoTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/local app/src/test/java/com/deckpuller/data/local
git commit -m "feat: add Room database, entities, and deck DAO"
```

---

### Task 6: Mappers (entity → domain)

Translate `DeckWithCards` into the domain `Deck`. Kept tiny and pure.

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/Mappers.kt`
- Test: `app/src/test/java/com/deckpuller/data/MappersTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/deckpuller/data/MappersTest.kt`:

```kotlin
package com.deckpuller.data

import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import com.deckpuller.data.local.entity.DeckWithCards
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun `maps DeckWithCards to domain Deck preserving fields`() {
        val entity = DeckWithCards(
            deck = DeckEntity(id = 1L, name = "Deck", importedAt = 5L),
            cards = listOf(
                CardEntity(
                    id = 7L, deckId = 1L, scryfallId = "uid-1", name = "Forest",
                    typeLine = "Basic Land — Forest", imageUrl = "f.jpg",
                    requiredQty = 4, pulledQty = 2,
                ),
            ),
        )

        val deck = entity.toDomain()

        assertEquals("Deck", deck.name)
        val card = deck.cards.single()
        assertEquals(7L, card.id)
        assertEquals("uid-1", card.scryfallId)
        assertEquals("Forest", card.name)
        assertEquals("Basic Land — Forest", card.typeLine)
        assertEquals("f.jpg", card.imageUrl)
        assertEquals(4, card.requiredQty)
        assertEquals(2, card.pulledQty)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.data.MappersTest"`
Expected: FAIL — `toDomain` unresolved.

- [ ] **Step 3: Implement the mapper**

`app/src/main/java/com/deckpuller/data/Mappers.kt`:

```kotlin
package com.deckpuller.data

import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckWithCards
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckCard

fun DeckWithCards.toDomain(): Deck = Deck(
    name = deck.name,
    cards = cards.map { it.toDomain() },
)

fun CardEntity.toDomain(): DeckCard = DeckCard(
    id = id,
    scryfallId = scryfallId,
    name = name,
    typeLine = typeLine,
    imageUrl = imageUrl,
    requiredQty = requiredQty,
    pulledQty = pulledQty,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.data.MappersTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/Mappers.kt app/src/test/java/com/deckpuller/data/MappersTest.kt
git commit -m "feat: map persisted deck to domain model"
```

---

### Task 7: Image prefetcher seam

A small interface so the repository can request image prefetching without depending on Coil directly (keeps the repository unit-testable).

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/image/ImagePrefetcher.kt`

- [ ] **Step 1: Create the interface and Coil implementation**

`app/src/main/java/com/deckpuller/data/image/ImagePrefetcher.kt`:

```kotlin
package com.deckpuller.data.image

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import javax.inject.Inject

/** Warms an image cache so cards display offline after import. */
interface ImagePrefetcher {
    fun prefetch(urls: List<String>)
}

class CoilImagePrefetcher @Inject constructor(
    private val context: Context,
    private val imageLoader: ImageLoader,
) : ImagePrefetcher {
    override fun prefetch(urls: List<String>) {
        urls.forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .build(),
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deckpuller/data/image/ImagePrefetcher.kt
git commit -m "feat: add image prefetcher seam"
```

---

### Task 8: Deck repository

The import pipeline (Archidekt → batched Scryfall → prefetch → persist), plus deck observation and pull mutations. Tested with fake APIs, a fake prefetcher, and in-memory Room.

**Files:**
- Create: `app/src/main/java/com/deckpuller/data/InvalidDeckUrlException.kt`
- Create: `app/src/main/java/com/deckpuller/data/repository/DeckRepository.kt`
- Create: `app/src/main/java/com/deckpuller/data/repository/DefaultDeckRepository.kt`
- Test: `app/src/test/java/com/deckpuller/data/repository/DefaultDeckRepositoryTest.kt`

- [ ] **Step 1: Create the exception and repository interface**

`app/src/main/java/com/deckpuller/data/InvalidDeckUrlException.kt`:

```kotlin
package com.deckpuller.data

class InvalidDeckUrlException : Exception("Not a valid Archidekt deck URL")
```

`app/src/main/java/com/deckpuller/data/repository/DeckRepository.kt`:

```kotlin
package com.deckpuller.data.repository

import com.deckpuller.domain.model.Deck
import kotlinx.coroutines.flow.Flow

interface DeckRepository {
    fun observeDeck(): Flow<Deck?>

    /** @throws com.deckpuller.data.InvalidDeckUrlException for an unparseable URL. */
    suspend fun importDeck(url: String)

    suspend fun setPulled(cardId: Long, pulled: Int)

    suspend fun clearDeck()
}
```

- [ ] **Step 2: Write the failing repository test**

`app/src/test/java/com/deckpuller/data/repository/DefaultDeckRepositoryTest.kt`:

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
import com.deckpuller.data.remote.dto.ArchidektOracleCardDto
import com.deckpuller.data.remote.dto.ScryfallCardDto
import com.deckpuller.data.remote.dto.ScryfallCollectionRequest
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
        id = id,
        name = name,
        typeLine = type,
        imageUris = ScryfallImageUris(small = "$id-s.jpg", normal = "$id-n.jpg"),
    )

    private fun repository(
        archidekt: ArchidektApi,
        scryfall: ScryfallApi,
    ) = DefaultDeckRepository(archidekt, scryfall, db.deckDao(), fakePrefetcher)

    @Test
    fun `importDeck throws on bad url`() = runTest {
        val repo = repository(
            archidekt = { fail("should not be called"); error("") },
            scryfall = { fail("should not be called"); error("") },
        )
        try {
            repo.importDeck("https://example.com/not-a-deck")
            fail("expected InvalidDeckUrlException")
        } catch (e: InvalidDeckUrlException) {
            // expected
        }
    }

    @Test
    fun `importDeck builds cards from archidekt and scryfall and prefetches images`() = runTest {
        val deckDto = ArchidektDeckDto(
            name = "Test Deck",
            cards = listOf(
                archidektCard("uid-1", "Forest", 4),
                archidektCard("uid-2", "Sol Ring", 1),
            ),
        )
        val repo = repository(
            archidekt = { deckDto },
            scryfall = { req ->
                ScryfallCollectionResponse(
                    data = req.identifiers.map {
                        when (it.id) {
                            "uid-1" -> scryfallCard("uid-1", "Forest", "Basic Land — Forest")
                            else -> scryfallCard("uid-2", "Sol Ring", "Artifact")
                        }
                    },
                )
            },
        )

        repo.importDeck("https://archidekt.com/decks/999/test")

        val deck = repo.observeDeck().first()!!
        assertEquals("Test Deck", deck.name)
        assertEquals(2, deck.cards.size)
        val forest = deck.cards.first { it.name == "Forest" }
        assertEquals(4, forest.requiredQty)
        assertEquals(0, forest.pulledQty)
        assertEquals("Basic Land — Forest", forest.typeLine)
        assertEquals("uid-1-n.jpg", forest.imageUrl)
        assertTrue(prefetched.containsAll(listOf("uid-1-n.jpg", "uid-2-n.jpg")))
    }

    @Test
    fun `importDeck falls back to archidekt name and Unknown type for missing scryfall card`() = runTest {
        val deckDto = ArchidektDeckDto(
            name = "Deck",
            cards = listOf(archidektCard("uid-x", "Mystery Card", 1)),
        )
        val repo = repository(
            archidekt = { deckDto },
            scryfall = { ScryfallCollectionResponse(data = emptyList()) },
        )

        repo.importDeck("https://archidekt.com/decks/1")

        val card = repo.observeDeck().first()!!.cards.single()
        assertEquals("Mystery Card", card.name)
        assertEquals("Unknown", card.typeLine)
        assertNull(card.imageUrl)
    }

    @Test
    fun `setPulled updates the stored count`() = runTest {
        val repo = repository(
            archidekt = {
                ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 4)))
            },
            scryfall = {
                ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land")))
            },
        )
        repo.importDeck("https://archidekt.com/decks/1")
        val cardId = repo.observeDeck().first()!!.cards.single().id

        repo.setPulled(cardId, 2)

        assertEquals(2, repo.observeDeck().first()!!.cards.single().pulledQty)
    }

    @Test
    fun `clearDeck empties the deck`() = runTest {
        val repo = repository(
            archidekt = {
                ArchidektDeckDto("Deck", listOf(archidektCard("uid-1", "Forest", 1)))
            },
            scryfall = {
                ScryfallCollectionResponse(listOf(scryfallCard("uid-1", "Forest", "Land")))
            },
        )
        repo.importDeck("https://archidekt.com/decks/1")

        repo.clearDeck()

        assertNull(repo.observeDeck().first())
    }
}
```

Note: the fakes above rely on `ArchidektApi`/`ScryfallApi` being single-abstract-method interfaces, so a lambda satisfies them.

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.data.repository.DefaultDeckRepositoryTest"`
Expected: FAIL — `DefaultDeckRepository` unresolved.

- [ ] **Step 4: Implement `DefaultDeckRepository`**

`app/src/main/java/com/deckpuller/data/repository/DefaultDeckRepository.kt`:

```kotlin
package com.deckpuller.data.repository

import com.deckpuller.data.ArchidektUrlParser
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.image.ImagePrefetcher
import com.deckpuller.data.local.DeckDao
import com.deckpuller.data.local.entity.CURRENT_DECK_ID
import com.deckpuller.data.local.entity.CardEntity
import com.deckpuller.data.local.entity.DeckEntity
import com.deckpuller.data.remote.ArchidektApi
import com.deckpuller.data.remote.ScryfallApi
import com.deckpuller.data.remote.dto.ScryfallCollectionRequest
import com.deckpuller.data.remote.dto.ScryfallIdentifier
import com.deckpuller.data.toDomain
import com.deckpuller.domain.model.Deck
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

    override fun observeDeck(): Flow<Deck?> =
        dao.observeDeck().map { it?.toDomain() }

    override suspend fun importDeck(url: String) {
        val deckId = ArchidektUrlParser.parseDeckId(url) ?: throw InvalidDeckUrlException()
        val deckDto = archidektApi.getDeck(deckId)

        val scryfallById = deckDto.cards
            .map { it.card.uid }
            .distinct()
            .chunked(SCRYFALL_BATCH)
            .flatMapIndexed { index, chunk ->
                if (index > 0) delay(SCRYFALL_THROTTLE_MS)
                val request = ScryfallCollectionRequest(chunk.map { ScryfallIdentifier(it) })
                scryfallApi.getCollection(request).data
            }
            .associateBy { it.id }

        val cards = deckDto.cards.map { entry ->
            val scryfall = scryfallById[entry.card.uid]
            CardEntity(
                deckId = CURRENT_DECK_ID,
                scryfallId = entry.card.uid,
                name = scryfall?.name ?: entry.card.oracleCard.name,
                typeLine = scryfall?.bestTypeLine() ?: "Unknown",
                imageUrl = scryfall?.bestImageUrl(),
                requiredQty = entry.quantity,
                pulledQty = 0,
            )
        }

        imagePrefetcher.prefetch(cards.mapNotNull { it.imageUrl })

        dao.replaceDeck(
            DeckEntity(name = deckDto.name, importedAt = System.currentTimeMillis()),
            cards,
        )
    }

    override suspend fun setPulled(cardId: Long, pulled: Int) =
        dao.updatePulled(cardId, pulled)

    override suspend fun clearDeck() = dao.clearDecks()
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.data.repository.DefaultDeckRepositoryTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/deckpuller/data app/src/test/java/com/deckpuller/data/repository
git commit -m "feat: implement deck import repository pipeline"
```

---

### Task 9: ImportViewModel

Drives the Import screen: loading/error state around `importDeck`. Success isn't a state — the app reacts to the new deck appearing (Task 15).

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/import/ImportViewModel.kt`
- Test: `app/src/test/java/com/deckpuller/ui/import/ImportViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/deckpuller/ui/import/ImportViewModelTest.kt`:

```kotlin
package com.deckpuller.ui.import

import app.cash.turbine.test
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun repo(
        onImport: suspend (String) -> Unit,
    ): DeckRepository = object : DeckRepository {
        override fun observeDeck(): Flow<Deck?> = flowOf(null)
        override suspend fun importDeck(url: String) = onImport(url)
        override suspend fun setPulled(cardId: Long, pulled: Int) = Unit
        override suspend fun clearDeck() = Unit
    }

    @Test
    fun `import shows loading then returns to idle on success`() = runTest {
        val vm = ImportViewModel(repo { /* success */ })

        vm.state.test {
            assertEquals(ImportUiState.Idle, awaitItem())
            vm.import("https://archidekt.com/decks/1")
            assertEquals(ImportUiState.Loading, awaitItem())
            assertEquals(ImportUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invalid url produces a friendly error`() = runTest {
        val vm = ImportViewModel(repo { throw InvalidDeckUrlException() })

        vm.state.test {
            assertEquals(ImportUiState.Idle, awaitItem())
            vm.import("nope")
            assertEquals(ImportUiState.Loading, awaitItem())
            val error = awaitItem()
            assertTrue(error is ImportUiState.Error)
            assertTrue((error as ImportUiState.Error).message.contains("Archidekt", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `network failure produces a generic error`() = runTest {
        val vm = ImportViewModel(repo { throw RuntimeException("boom") })

        vm.state.test {
            assertEquals(ImportUiState.Idle, awaitItem())
            vm.import("https://archidekt.com/decks/1")
            assertEquals(ImportUiState.Loading, awaitItem())
            assertTrue(awaitItem() is ImportUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissError returns to idle`() = runTest {
        val vm = ImportViewModel(repo { throw RuntimeException("boom") })
        vm.import("https://archidekt.com/decks/1")

        vm.dismissError()

        assertEquals(ImportUiState.Idle, vm.state.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.ui.import.ImportViewModelTest"`
Expected: FAIL — `ImportViewModel` / `ImportUiState` unresolved.

- [ ] **Step 3: Implement the ViewModel**

`app/src/main/java/com/deckpuller/ui/import/ImportViewModel.kt`:

```kotlin
package com.deckpuller.ui.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.InvalidDeckUrlException
import com.deckpuller.data.repository.DeckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Error(val message: String) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: DeckRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun import(url: String) {
        _state.value = ImportUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                repository.importDeck(url)
                ImportUiState.Idle
            } catch (e: InvalidDeckUrlException) {
                ImportUiState.Error("That doesn't look like an Archidekt deck URL.")
            } catch (e: Exception) {
                ImportUiState.Error("Couldn't import the deck. Check your connection and try again.")
            }
        }
    }

    fun dismissError() {
        _state.value = ImportUiState.Idle
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.ui.import.ImportViewModelTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/import/ImportViewModel.kt app/src/test/java/com/deckpuller/ui/import/ImportViewModelTest.kt
git commit -m "feat: add ImportViewModel with loading and error states"
```

---

### Task 10: PullViewModel

Exposes the grouped deck + progress, and handles increment/decrement (clamped) and clear.

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/pull/PullViewModel.kt`
- Test: `app/src/test/java/com/deckpuller/ui/pull/PullViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/deckpuller/ui/pull/PullViewModelTest.kt`:

```kotlin
package com.deckpuller.ui.pull

import app.cash.turbine.test
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import com.deckpuller.domain.model.DeckCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PullViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun card(id: Long, name: String, type: String, required: Int, pulled: Int) =
        DeckCard(id, "uid-$id", name, type, null, required, pulled)

    /** Fake repo backed by a mutable flow; setPulled rewrites the matching card. */
    private class FakeRepo(initial: Deck?) : DeckRepository {
        val deck = MutableStateFlow(initial)
        override fun observeDeck(): Flow<Deck?> = deck
        override suspend fun importDeck(url: String) = Unit
        override suspend fun setPulled(cardId: Long, pulled: Int) {
            deck.update { current ->
                current?.copy(cards = current.cards.map {
                    if (it.id == cardId) it.copy(pulledQty = pulled) else it
                })
            }
        }
        override suspend fun clearDeck() {
            deck.value = null
        }
    }

    @Test
    fun `state groups cards and reports progress`() = runTest {
        val deck = Deck(
            "Deck",
            listOf(
                card(1, "Forest", "Land", required = 4, pulled = 1),
                card(2, "Sol Ring", "Artifact", required = 1, pulled = 0),
            ),
        )
        val vm = PullViewModel(FakeRepo(deck))

        vm.state.test {
            // first emission may be the null seed; advance to the populated state
            var s = awaitItem()
            while (s == null) s = awaitItem()
            assertEquals("Deck", s!!.deckName)
            assertEquals(1, s.pulled)
            assertEquals(5, s.total)
            assertFalse(s.isComplete)
            assertEquals(listOf("Artifact", "Land"), s.groups.map { it.type })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `increment is clamped to required quantity`() = runTest {
        val repo = FakeRepo(Deck("Deck", listOf(card(1, "Sol Ring", "Artifact", 1, 1))))
        val vm = PullViewModel(repo)

        vm.increment(card(1, "Sol Ring", "Artifact", required = 1, pulled = 1))

        assertEquals(1, repo.deck.value!!.cards.single().pulledQty)
    }

    @Test
    fun `decrement is clamped to zero`() = runTest {
        val repo = FakeRepo(Deck("Deck", listOf(card(1, "Sol Ring", "Artifact", 1, 0))))
        val vm = PullViewModel(repo)

        vm.decrement(card(1, "Sol Ring", "Artifact", required = 1, pulled = 0))

        assertEquals(0, repo.deck.value!!.cards.single().pulledQty)
    }

    @Test
    fun `isComplete is true when every card is fully pulled`() = runTest {
        val deck = Deck("Deck", listOf(card(1, "Sol Ring", "Artifact", 1, 1)))
        val vm = PullViewModel(FakeRepo(deck))

        vm.state.test {
            var s = awaitItem()
            while (s == null) s = awaitItem()
            assertTrue(s!!.isComplete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clear empties the repository`() = runTest {
        val repo = FakeRepo(Deck("Deck", listOf(card(1, "Sol Ring", "Artifact", 1, 1))))
        val vm = PullViewModel(repo)

        vm.clear()

        assertEquals(null, repo.deck.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.ui.pull.PullViewModelTest"`
Expected: FAIL — `PullViewModel` / `PullUiState` unresolved.

- [ ] **Step 3: Implement the ViewModel**

`app/src/main/java/com/deckpuller/ui/pull/PullViewModel.kt`:

```kotlin
package com.deckpuller.ui.pull

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.DeckGrouping
import com.deckpuller.domain.model.CardGroup
import com.deckpuller.domain.model.DeckCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PullUiState(
    val deckName: String,
    val groups: List<CardGroup>,
    val pulled: Int,
    val total: Int,
) {
    val isComplete: Boolean get() = total > 0 && pulled == total
}

@HiltViewModel
class PullViewModel @Inject constructor(
    private val repository: DeckRepository,
) : ViewModel() {

    val state: StateFlow<PullUiState?> = repository.observeDeck()
        .map { deck ->
            deck?.let {
                PullUiState(
                    deckName = it.name,
                    groups = DeckGrouping.group(it.cards),
                    pulled = it.cards.sumOf { card -> card.pulledQty },
                    total = it.cards.sumOf { card -> card.requiredQty },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun increment(card: DeckCard) {
        if (card.pulledQty >= card.requiredQty) return
        viewModelScope.launch { repository.setPulled(card.id, card.pulledQty + 1) }
    }

    fun decrement(card: DeckCard) {
        if (card.pulledQty <= 0) return
        viewModelScope.launch { repository.setPulled(card.id, card.pulledQty - 1) }
    }

    fun clear() {
        viewModelScope.launch { repository.clearDeck() }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.ui.pull.PullViewModelTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/pull/PullViewModel.kt app/src/test/java/com/deckpuller/ui/pull/PullViewModelTest.kt
git commit -m "feat: add PullViewModel with grouping, progress, and clamped counters"
```

---

### Task 11: Hilt dependency injection modules

Wires the network stack, database, image loader, and repository binding.

**Files:**
- Create: `app/src/main/java/com/deckpuller/di/NetworkModule.kt`
- Create: `app/src/main/java/com/deckpuller/di/DatabaseModule.kt`
- Create: `app/src/main/java/com/deckpuller/di/RepositoryModule.kt`

- [ ] **Step 1: Create the network module**

`app/src/main/java/com/deckpuller/di/NetworkModule.kt`:

```kotlin
package com.deckpuller.di

import android.content.Context
import coil.ImageLoader
import com.deckpuller.data.remote.ArchidektApi
import com.deckpuller.data.remote.ScryfallApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "DeckPuller/1.0")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides
    @Singleton
    fun provideArchidektApi(client: OkHttpClient, json: Json): ArchidektApi =
        Retrofit.Builder()
            .baseUrl("https://archidekt.com/api/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ArchidektApi::class.java)

    @Provides
    @Singleton
    fun provideScryfallApi(client: OkHttpClient, json: Json): ScryfallApi =
        Retrofit.Builder()
            .baseUrl("https://api.scryfall.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ScryfallApi::class.java)

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
        ImageLoader.Builder(context).build()
}
```

- [ ] **Step 2: Create the database module**

`app/src/main/java/com/deckpuller/di/DatabaseModule.kt`:

```kotlin
package com.deckpuller.di

import android.content.Context
import androidx.room.Room
import com.deckpuller.data.local.AppDatabase
import com.deckpuller.data.local.DeckDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "deckpuller.db").build()

    @Provides
    fun provideDeckDao(database: AppDatabase): DeckDao = database.deckDao()
}
```

- [ ] **Step 3: Create the repository + prefetcher bindings**

`app/src/main/java/com/deckpuller/di/RepositoryModule.kt`:

```kotlin
package com.deckpuller.di

import android.content.Context
import coil.ImageLoader
import com.deckpuller.data.image.CoilImagePrefetcher
import com.deckpuller.data.image.ImagePrefetcher
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.data.repository.DefaultDeckRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideImagePrefetcher(
        @ApplicationContext context: Context,
        imageLoader: ImageLoader,
    ): ImagePrefetcher = CoilImagePrefetcher(context, imageLoader)

    @Provides
    @Singleton
    fun provideDeckRepository(impl: DefaultDeckRepository): DeckRepository = impl
}
```

- [ ] **Step 4: Build to verify Hilt graph compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (Hilt code generation passes — no missing bindings).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/di
git commit -m "feat: wire Hilt modules for network, database, and repository"
```

---

### Task 12: Import screen UI

The Compose Import screen: URL field, Import button, loading and error states.

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/import/ImportScreen.kt`
- Test: `app/src/test/java/com/deckpuller/ui/import/ImportScreenTest.kt`

- [ ] **Step 1: Implement the screen**

`app/src/main/java/com/deckpuller/ui/import/ImportScreen.kt`:

```kotlin
package com.deckpuller.ui.import

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ImportScreen(
    state: ImportUiState,
    onImport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }
    val isLoading = state is ImportUiState.Loading

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Import a deck", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Paste an Archidekt deck URL to start pulling cards.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Archidekt deck URL") },
            singleLine = true,
            isError = state is ImportUiState.Error,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state is ImportUiState.Error) {
            Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .semantics { contentDescription = "Importing" },
            )
        } else {
            Button(
                onClick = { onImport(url) },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import")
            }
        }
    }
}
```

- [ ] **Step 2: Write a Compose interaction test**

`app/src/test/java/com/deckpuller/ui/import/ImportScreenTest.kt`:

```kotlin
package com.deckpuller.ui.import

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `tapping import passes the entered url`() {
        var imported: String? = null
        rule.setContent {
            ImportScreen(state = ImportUiState.Idle, onImport = { imported = it })
        }

        rule.onNodeWithText("Archidekt deck URL").performTextInput("https://archidekt.com/decks/5")
        rule.onNodeWithText("Import").assertIsEnabled()
        rule.onNodeWithText("Import").performClick()

        assertEquals("https://archidekt.com/decks/5", imported)
    }

    @Test
    fun `error message is shown`() {
        rule.setContent {
            ImportScreen(state = ImportUiState.Error("Bad URL"), onImport = {})
        }
        rule.onNodeWithText("Bad URL").assertExists()
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.ui.import.ImportScreenTest"`
Expected: PASS (2 tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/import/ImportScreen.kt app/src/test/java/com/deckpuller/ui/import/ImportScreenTest.kt
git commit -m "feat: add Import screen UI"
```

---

### Task 13: Card row & Pull screen UI

The card row (thumbnail + name + counter) and the grouped pull list with a progress bar.

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/pull/CardRow.kt`
- Create: `app/src/main/java/com/deckpuller/ui/pull/PullScreen.kt`
- Test: `app/src/test/java/com/deckpuller/ui/pull/CardRowTest.kt`

- [ ] **Step 1: Implement the card row**

`app/src/main/java/com/deckpuller/ui/pull/CardRow.kt`:

```kotlin
package com.deckpuller.ui.pull

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deckpuller.domain.model.DeckCard

@Composable
fun CardRow(
    card: DeckCard,
    onIncrement: (DeckCard) -> Unit,
    onDecrement: (DeckCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onIncrement(card) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (card.isComplete) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 40.dp, height = 56.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Text(
            text = card.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (card.isComplete) FontWeight.Normal else FontWeight.Medium,
            modifier = Modifier.width(0.dp).weight(1f),
        )
        IconButton(
            onClick = { onDecrement(card) },
            enabled = card.pulledQty > 0,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrement ${card.name}")
        }
        Text(
            text = "${card.pulledQty}/${card.requiredQty}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(48.dp),
        )
    }
}
```

Note: `Icons.Filled.Remove` comes from the bundled `material-icons-core` (included with `material3`). If unresolved, replace the `Icon` with `Text("−")`.

- [ ] **Step 2: Implement the pull screen**

`app/src/main/java/com/deckpuller/ui/pull/PullScreen.kt`:

```kotlin
package com.deckpuller.ui.pull

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deckpuller.domain.model.DeckCard

@Composable
fun PullScreen(
    state: PullUiState,
    onIncrement: (DeckCard) -> Unit,
    onDecrement: (DeckCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PullHeader(deckName = state.deckName, pulled = state.pulled, total = state.total)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            state.groups.forEach { group ->
                stickyHeader(key = "header-${group.type}") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PullHeader(deckName: String, pulled: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(deckName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "$pulled / $total pulled",
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else pulled.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
```

- [ ] **Step 3: Write a Compose interaction test for the counter**

`app/src/test/java/com/deckpuller/ui/pull/CardRowTest.kt`:

```kotlin
package com.deckpuller.ui.pull

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.deckpuller.domain.model.DeckCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun card(pulled: Int, required: Int) = DeckCard(
        id = 1, scryfallId = "uid", name = "Sol Ring", typeLine = "Artifact",
        imageUrl = null, requiredQty = required, pulledQty = pulled,
    )

    @Test
    fun `tapping the row increments`() {
        var incremented = false
        rule.setContent {
            CardRow(card(pulled = 0, required = 1), onIncrement = { incremented = true }, onDecrement = {})
        }
        rule.onNodeWithText("Sol Ring").performClick()
        assertEquals(true, incremented)
    }

    @Test
    fun `decrement button is disabled at zero`() {
        rule.setContent {
            CardRow(card(pulled = 0, required = 4), onIncrement = {}, onDecrement = {})
        }
        rule.onNodeWithContentDescription("Decrement Sol Ring").assertIsNotEnabled()
    }

    @Test
    fun `shows pulled over required count`() {
        rule.setContent {
            CardRow(card(pulled = 2, required = 4), onIncrement = {}, onDecrement = {})
        }
        rule.onNodeWithText("2/4").assertExists()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.ui.pull.CardRowTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/pull/CardRow.kt app/src/main/java/com/deckpuller/ui/pull/PullScreen.kt app/src/test/java/com/deckpuller/ui/pull/CardRowTest.kt
git commit -m "feat: add card row and grouped pull screen UI"
```

---

### Task 14: Celebration overlay

A confetti overlay shown when the deck is complete; after it plays it triggers a callback (used in Task 15 to clear the deck).

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/pull/CelebrationOverlay.kt`

- [ ] **Step 1: Implement the overlay**

`app/src/main/java/com/deckpuller/ui/pull/CelebrationOverlay.kt`:

```kotlin
package com.deckpuller.ui.pull

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

private const val CELEBRATION_MS = 3500L

@Composable
fun CelebrationOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(CELEBRATION_MS)
        onFinished()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Deck complete! 🎉",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
            }
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = listOf(
                    Party(
                        emitter = Emitter(duration = 2, TimeUnit.SECONDS).max(200),
                        position = Position.Relative(0.5, 0.3),
                    ),
                ),
            )
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/deckpuller/ui/pull/CelebrationOverlay.kt
git commit -m "feat: add confetti celebration overlay"
```

---

### Task 15: App root, MainViewModel & wiring

Ties everything together: a `MainViewModel` reports whether a deck exists; `AppRoot` shows Import or Pull accordingly, overlays the celebration when complete, and clears the deck when the celebration finishes. Replaces the placeholder `MainActivity` body.

**Files:**
- Create: `app/src/main/java/com/deckpuller/ui/MainViewModel.kt`
- Create: `app/src/main/java/com/deckpuller/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/deckpuller/MainActivity.kt`
- Test: `app/src/test/java/com/deckpuller/ui/MainViewModelTest.kt`

- [ ] **Step 1: Write the failing MainViewModel test**

`app/src/test/java/com/deckpuller/ui/MainViewModelTest.kt`:

```kotlin
package com.deckpuller.ui

import app.cash.turbine.test
import com.deckpuller.data.repository.DeckRepository
import com.deckpuller.domain.model.Deck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MainViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo(initial: Deck?) : DeckRepository {
        val deck = MutableStateFlow(initial)
        override fun observeDeck(): Flow<Deck?> = deck
        override suspend fun importDeck(url: String) = Unit
        override suspend fun setPulled(cardId: Long, pulled: Int) = Unit
        override suspend fun clearDeck() { deck.value = null }
    }

    @Test
    fun `hasDeck reflects deck presence`() = runTest {
        val repo = FakeRepo(Deck("Deck", emptyList()))
        val vm = MainViewModel(repo)

        vm.hasDeck.test {
            assertEquals(null, awaitItem())       // initial seed (loading)
            assertEquals(true, awaitItem())        // deck present
            repo.clearDeck()
            assertEquals(false, awaitItem())       // deck gone
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.ui.MainViewModelTest"`
Expected: FAIL — `MainViewModel` unresolved.

- [ ] **Step 3: Implement `MainViewModel`**

`app/src/main/java/com/deckpuller/ui/MainViewModel.kt`:

```kotlin
package com.deckpuller.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deckpuller.data.repository.DeckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    repository: DeckRepository,
) : ViewModel() {

    // null = still loading initial state; true/false = deck present or not.
    val hasDeck: StateFlow<Boolean?> = repository.observeDeck()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.deckpuller.ui.MainViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Implement `AppRoot`**

`app/src/main/java/com/deckpuller/ui/AppRoot.kt`:

```kotlin
package com.deckpuller.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deckpuller.ui.import.ImportScreen
import com.deckpuller.ui.import.ImportViewModel
import com.deckpuller.ui.pull.CelebrationOverlay
import com.deckpuller.ui.pull.PullScreen
import com.deckpuller.ui.pull.PullViewModel

@Composable
fun AppRoot(
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val hasDeck by mainViewModel.hasDeck.collectAsStateWithLifecycle()

    when (hasDeck) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        false -> ImportRoute()
        true -> PullRoute()
    }
}

@Composable
private fun ImportRoute(viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImportScreen(state = state, onImport = viewModel::import)
}

@Composable
private fun PullRoute(viewModel: PullViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state?.let { pull ->
        PullScreen(
            state = pull,
            onIncrement = viewModel::increment,
            onDecrement = viewModel::decrement,
        )
        if (pull.isComplete) {
            CelebrationOverlay(onFinished = viewModel::clear)
        }
    }
}
```

- [ ] **Step 6: Update `MainActivity` to host `AppRoot`**

Replace the body of `app/src/main/java/com/deckpuller/MainActivity.kt`:

```kotlin
package com.deckpuller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.deckpuller.ui.AppRoot
import com.deckpuller.ui.theme.DeckPullerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeckPullerTheme {
                Surface {
                    AppRoot()
                }
            }
        }
    }
}
```

- [ ] **Step 7: Add the missing lifecycle-compose dependency**

`collectAsStateWithLifecycle` lives in `androidx.lifecycle:lifecycle-runtime-compose`. Add to `gradle/libs.versions.toml` under `[libraries]`:

```toml
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
```

Add to `app/build.gradle.kts` dependencies, and also add Hilt's compose navigation helper for `hiltViewModel`:

```kotlin
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
```

(`libs.hilt.navigation.compose` is already defined in the catalog from Task 1.)

- [ ] **Step 8: Build and run the full test suite**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all unit/Robolectric tests pass.

- [ ] **Step 9: Manual verification on a device/emulator**

Run: `./gradlew installDebug` (with an emulator or device connected), then:
1. Launch DeckPuller → Import screen appears.
2. Paste a real Archidekt deck URL → tap Import → spinner → Pull screen with cards grouped by type, each showing `0/N` and a thumbnail.
3. Tap rows / use `−` → counts change and persist after backgrounding the app.
4. Bad URL → inline error.
5. Pull every card to its required count → confetti celebration → list clears → back to Import screen.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat: wire app root, deck-presence routing, and celebration"
```

---

## Self-Review

**Spec coverage:**
- Native Kotlin + Compose → Task 1 (stack), all UI tasks. ✓
- Import from Archidekt by URL → Task 2 (parse), Task 4 (API), Task 8 (pipeline). ✓
- Scryfall batch lookup for type + image → Task 4 (DTOs/API), Task 8 (chunked `/cards/collection`). ✓
- One deck at a time / replace on import → Task 5 (`replaceDeck`, fixed `CURRENT_DECK_ID`), Task 8. ✓
- Multiple copies via counter per row → Task 5 (`requiredQty`/`pulledQty`), Task 10 (clamping), Task 13 (`pulled/required` row). ✓
- Group by type, sorted by name → Task 3 (`CardTypeClassifier`, `DeckGrouping`), Task 13 (sticky headers). ✓
- Offline image caching → Task 7 (`ImagePrefetcher`), Task 8 (prefetch on import), Task 11 (Coil `ImageLoader`). ✓
- Progress + completion → Task 10 (`PullUiState.isComplete`), Task 13 (progress bar). ✓
- Celebration then clear → Task 14 (overlay), Task 15 (`onFinished` → `clear`). ✓
- Error handling (bad URL, network, missing card, image fallback) → Task 8 (Unknown/null fallback), Task 9 (error states), Task 13 (`AsyncImage` placeholder). ✓
- Testing (URL, DTO, repo, viewmodels) → Tasks 2,3,4,5,6,8,9,10,12,13,15. ✓

**Placeholder scan:** No "TBD"/"implement later"; every code step is complete. The two "if unresolved, substitute…" notes (material icon, wrapper generation) are real fallbacks, not placeholders.

**Type consistency:** `DeckRepository` interface signature is identical across the impl and all fakes (`observeDeck`, `importDeck`, `setPulled`, `clearDeck`). `DeckCard` field set is consistent everywhere. `replaceDeck`/`updatePulled`/`clearDecks` DAO names match their callers. `CURRENT_DECK_ID` used consistently. `PullUiState`/`ImportUiState` shapes match their tests.

**Known simplification (explicit):** v1 imports every entry in the Archidekt `cards` array. Sideboard/maybeboard filtering is out of scope (commander/single-deck use case); revisit if needed.
