# UI Overhaul + Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **Project rules (CLAUDE.md):** tasks go to a Claude subagent on **Sonnet** with the full task text; the subagent may build and run tests but **does not commit**; the main session reviews the diff, re-runs verification and commits (no `Co-Authored-By`/Claude lines in commit messages). Never pass `local.properties` to a subagent.

**Goal:** Mihon-style library (3 switchable display modes, 2–6 columns, badges), light/dark/AMOLED/dynamic theming with an Ink & amber fallback palette, and a first Settings screen.

**Architecture:** A pure `AppSettings` model + `SettingsRepository` (Preferences DataStore, Hilt singleton) feed `LibraryTheme` (MainActivity), the library screen and the Settings screen through one shared `SettingsViewModel`. Display options UI is one composable (`DisplayOptions`) used by both the library bottom sheet and the Settings screen.

**Tech Stack:** Kotlin 2.4.21, Compose Material 3 (Expressive), Hilt, `androidx.datastore:datastore-preferences:1.3.0`, JUnit4 (no Robolectric needed for new tests), Navigation 3.

Spec: `docs/superpowers/specs/2026-11-22-ui-overhaul-settings-design.md`.

## Toolchain (each shell)

```powershell
$env:JAVA_HOME="$env:USERPROFILE\scoop\apps\temurin21-jdk\current"
$env:ANDROID_HOME="$env:USERPROFILE\scoop\apps\android-clt\current"
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
```

## File structure

| File | Responsibility |
|---|---|
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | DataStore dependency |
| `app/src/main/java/com/exc33ded/library/settings/AppSettings.kt` | enums, `AppSettings`, pure helpers (`clampColumns`, `isDark`, `progressPercent`) |
| `.../settings/SettingsRepository.kt` | DataStore read/write of `AppSettings` |
| `.../di/SettingsModule.kt` | provides `DataStore<Preferences>` |
| `.../ui/settings/SettingsViewModel.kt` | exposes `settings` + `update` |
| `.../ui/settings/DisplayOptions.kt` | shared mode/columns/badges controls |
| `.../ui/settings/SettingsScreen.kt` | Appearance + Library display + disabled "Models & storage" |
| `.../ui/profile/ProfileScreen.kt` | Profile tab with a Settings row |
| `.../ui/theme/Palette.kt`, `Theme.kt` | Ink & amber schemes, AMOLED, status-bar icons |
| `.../ui/library/Badge.kt` | pure `badgeText` |
| `.../ui/library/LibraryItems.kt` | `OverlayTile`, `CaptionTile`, `ListRow` |
| `.../ui/library/LibraryScreen.kt` | uses settings, display-sheet button |
| `.../ui/nav/Keys.kt`, `AppNav.kt`, `MainActivity.kt` | route + wiring |
| `app/src/test/.../settings/AppSettingsTest.kt`, `SettingsRepositoryTest.kt`, `ui/library/BadgeTest.kt` | tests |

All main paths are under `app/src/main/java/com/exc33ded/library/`, all test paths under `app/src/test/java/com/exc33ded/library/`.

---

### Task 1: DataStore dependency + pure settings model

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `settings/AppSettings.kt`
- Test: `settings/AppSettingsTest.kt`

- [ ] **Step 1: Add the dependency**

In `gradle/libs.versions.toml` add `datastore = "1.3.0"` under `[versions]` and
`androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }` under `[libraries]`.
In `app/build.gradle.kts` add `implementation(libs.androidx.datastore.preferences)` next to the other `implementation` lines.

- [ ] **Step 2: Write the failing test** — `settings/AppSettingsTest.kt`

```kotlin
package com.exc33ded.library.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSettingsTest {
    @Test fun columnsClampToRange() {
        assertEquals(2, clampColumns(0))
        assertEquals(2, clampColumns(2))
        assertEquals(4, clampColumns(4))
        assertEquals(6, clampColumns(99))
    }

    @Test fun darkResolution() {
        assertEquals(true, isDark(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(false, isDark(ThemeMode.SYSTEM, systemDark = false))
        assertEquals(false, isDark(ThemeMode.LIGHT, systemDark = true))
        assertEquals(true, isDark(ThemeMode.DARK, systemDark = false))
    }

    @Test fun progressPercent() {
        assertEquals(50, progressPercent(160, 320))
        assertEquals(100, progressPercent(500, 320)) // clamped
        assertNull(progressPercent(10, null))
        assertNull(progressPercent(10, 0))
    }

    @Test fun defaultsMatchSpec() {
        val d = AppSettings()
        assertEquals(ThemeMode.SYSTEM, d.themeMode)
        assertEquals(true, d.dynamicColor)
        assertEquals(false, d.amoled)
        assertEquals(LibraryDisplayMode.OVERLAY_GRID, d.displayMode)
        assertEquals(3, d.columns)
    }
}
```

- [ ] **Step 3: Run it, expect FAIL (unresolved references)**

Run: `.\gradlew.bat testDebugUnitTest --tests "*.AppSettingsTest"`

- [ ] **Step 4: Implement** — `settings/AppSettings.kt`

```kotlin
package com.exc33ded.library.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class LibraryDisplayMode { OVERLAY_GRID, CAPTION_GRID, LIST }

const val MIN_COLUMNS = 2
const val MAX_COLUMNS = 6

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.OVERLAY_GRID,
    val columns: Int = 3,
    val showProgressBadge: Boolean = true,
    val showStatusBadge: Boolean = true,
)

fun clampColumns(n: Int): Int = n.coerceIn(MIN_COLUMNS, MAX_COLUMNS)

fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** 0..100, or null when the page count is unknown. */
fun progressPercent(currentPage: Int, pageCount: Int?): Int? =
    pageCount?.takeIf { it > 0 }?.let { (currentPage * 100 / it).coerceIn(0, 100) }
```

- [ ] **Step 5: Run, expect PASS** — same command as step 3. Report diff; main session commits `feat(settings): AppSettings model + datastore dep`.

---

### Task 2: SettingsRepository (DataStore) + Hilt module

**Files:**
- Create: `settings/SettingsRepository.kt`, `di/SettingsModule.kt`
- Test: `settings/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing test** — `settings/SettingsRepositoryTest.kt`

```kotlin
package com.exc33ded.library.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            File(tmp.root, "settings.preferences_pb")
        },
    )

    @Test fun emptyStoreGivesDefaults() = runBlocking {
        assertEquals(AppSettings(), repo().settings.first())
    }

    @Test fun updatePersistsAllFields() = runBlocking {
        val r = repo()
        r.update {
            it.copy(
                themeMode = ThemeMode.DARK, dynamicColor = false, amoled = true,
                displayMode = LibraryDisplayMode.LIST, columns = 5,
                showProgressBadge = false, showStatusBadge = false,
            )
        }
        val s = r.settings.first()
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(false, s.dynamicColor)
        assertEquals(true, s.amoled)
        assertEquals(LibraryDisplayMode.LIST, s.displayMode)
        assertEquals(5, s.columns)
        assertEquals(false, s.showProgressBadge)
        assertEquals(false, s.showStatusBadge)
    }

    @Test fun columnsAreClampedOnWrite() = runBlocking {
        val r = repo()
        r.update { it.copy(columns = 99) }
        assertEquals(6, r.settings.first().columns)
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat testDebugUnitTest --tests "*.SettingsRepositoryTest"`
(`kotlinx-coroutines` is already an `implementation` dependency, visible to unit tests.)
**Fallback:** if the test, once the implementation exists, throws `Method … not mocked`, add `@RunWith(RobolectricTestRunner::class)` and `@Config(sdk = [35])` to the test class (the repo's Robolectric convention) instead of changing the repository.

- [ ] **Step 3: Implement** — `settings/SettingsRepository.kt`

```kotlin
package com.exc33ded.library.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = store.data.map(::read)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val n = transform(read(p))
            p[THEME] = n.themeMode.name
            p[DYNAMIC] = n.dynamicColor
            p[AMOLED] = n.amoled
            p[MODE] = n.displayMode.name
            p[COLUMNS] = clampColumns(n.columns)
            p[PROGRESS] = n.showProgressBadge
            p[STATUS] = n.showStatusBadge
        }
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[DYNAMIC] ?: d.dynamicColor,
            amoled = p[AMOLED] ?: d.amoled,
            displayMode = p[MODE]?.let { runCatching { LibraryDisplayMode.valueOf(it) }.getOrNull() } ?: d.displayMode,
            columns = clampColumns(p[COLUMNS] ?: d.columns),
            showProgressBadge = p[PROGRESS] ?: d.showProgressBadge,
            showStatusBadge = p[STATUS] ?: d.showStatusBadge,
        )
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val MODE = stringPreferencesKey("display_mode")
        val COLUMNS = intPreferencesKey("columns")
        val PROGRESS = booleanPreferencesKey("show_progress_badge")
        val STATUS = booleanPreferencesKey("show_status_badge")
    }
}
```

`di/SettingsModule.kt`

```kotlin
package com.exc33ded.library.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides @Singleton
    fun settingsStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { ctx.preferencesDataStoreFile("settings") }
}
```

- [ ] **Step 4: Run, expect PASS**, then `.\gradlew.bat assembleDebug` (Hilt graph compiles). Main session commits `feat(settings): DataStore-backed SettingsRepository`.

---

### Task 3: Theme — Ink & amber palette, AMOLED, status-bar icons

**Files:**
- Create: `ui/theme/Palette.kt`
- Modify: `ui/theme/Theme.kt`, `MainActivity.kt`

- [ ] **Step 1: Palette** — `ui/theme/Palette.kt`

```kotlin
package com.exc33ded.library.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Fixed "Ink & amber" scheme, used when dynamic colors are off. */
val InkLight = lightColorScheme(
    primary = Color(0xFFA8691A), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0D9B0), onPrimaryContainer = Color(0xFF3A2200),
    secondary = Color(0xFF6F5B3E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E0D0), onSecondaryContainer = Color(0xFF251A06),
    tertiary = Color(0xFF55633A), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F1E8), onBackground = Color(0xFF1C1A16),
    surface = Color(0xFFF6F1E8), onSurface = Color(0xFF1C1A16),
    surfaceVariant = Color(0xFFE8E0D0), onSurfaceVariant = Color(0xFF4B4639),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1EBDF),
    surfaceContainer = Color(0xFFECE5D6), surfaceContainerHigh = Color(0xFFE6DFCF),
    surfaceContainerHighest = Color(0xFFE0D9C9),
    outline = Color(0xFF7D7667), outlineVariant = Color(0xFFCFC7B8),
)

val InkDark = darkColorScheme(
    primary = Color(0xFFE0A85A), onPrimary = Color(0xFF2A1A00),
    primaryContainer = Color(0xFF4A3000), onPrimaryContainer = Color(0xFFFFDDAE),
    secondary = Color(0xFFD9C3A0), onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF2A2926), onSecondaryContainer = Color(0xFFF1E0BB),
    tertiary = Color(0xFFBBCB98), onTertiary = Color(0xFF273512),
    background = Color(0xFF0F0F10), onBackground = Color(0xFFECE8E1),
    surface = Color(0xFF0F0F10), onSurface = Color(0xFFECE8E1),
    surfaceVariant = Color(0xFF2A2926), onSurfaceVariant = Color(0xFFCFC7B8),
    surfaceContainerLowest = Color(0xFF0A0A0B), surfaceContainerLow = Color(0xFF171715),
    surfaceContainer = Color(0xFF1D1C1A), surfaceContainerHigh = Color(0xFF262522),
    surfaceContainerHighest = Color(0xFF302F2B),
    outline = Color(0xFF989080), outlineVariant = Color(0xFF4B4639),
)
```

- [ ] **Step 2: Theme** — replace `ui/theme/Theme.kt` with:

```kotlin
package com.exc33ded.library.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.isDark

/**
 * Spec §10: every animation pulls its spec from MaterialTheme.motionScheme.
 * minSdk 31 => dynamic color always available.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isDark(settings.themeMode, isSystemInDarkTheme())
    var colors = when {
        settings.dynamicColor -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> InkDark
        else -> InkLight
    }
    if (dark && settings.amoled) {
        colors = colors.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
    }
    // Status/nav bar icon contrast must follow the *chosen* theme, not the system one.
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive()) {
        CompositionLocalProvider(LocalAppSettings provides settings, content = content)
    }
}

/**
 * Settings collected once in MainActivity for the app's lifetime. Screens read this instead of
 * collecting their own copy: top-level tab switches clear the back stack (and the ViewModel), so a
 * per-screen collector would flash the defaults on every return to Library.
 */
val LocalAppSettings = compositionLocalOf { AppSettings() }
```

**Before editing `LibraryTheme`'s signature, grep for `LibraryTheme(` (e.g. `@Preview`s) and update every caller.**

- [ ] **Step 3: MainActivity** — inject the repository and pass settings:

```kotlin
package com.exc33ded.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.SettingsRepository
import com.exc33ded.library.ui.nav.AppNav
import com.exc33ded.library.ui.theme.LibraryTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ponytail: first frame uses defaults until DataStore emits; add a splash hold if the flash is visible.
            val settings by settingsRepo.settings.collectAsStateWithLifecycle(AppSettings())
            LibraryTheme(settings) { AppNav() }
        }
    }
}
```

- [ ] **Step 4: Build** — `.\gradlew.bat assembleDebug` must succeed. (If `ColorScheme.copy` rejects `surfaceContainerLowest`, drop that argument only.) Main session commits `feat(theme): Ink & amber palette, AMOLED, theme mode`.

---

### Task 4: Library display — badge logic, tiles, display options, screen

**Files:**
- Create: `ui/library/Badge.kt`, `ui/library/LibraryItems.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/DisplayOptions.kt`
- Modify: `ui/library/LibraryScreen.kt`
- Test: `ui/library/BadgeTest.kt`

- [ ] **Step 1: Failing test** — `ui/library/BadgeTest.kt`

```kotlin
package com.exc33ded.library.ui.library

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeTest {
    private val reading = BookEntity(id = "b", title = "T", status = ReadingStatus.READING, currentPage = 160, pageCount = 320)

    @Test fun readingShowsPercent() = assertEquals("50%", badgeText(reading, AppSettings()))

    @Test fun readingWithoutPageCountFallsBackToStatus() =
        assertEquals("Reading", badgeText(reading.copy(pageCount = null), AppSettings()))

    @Test fun progressOffFallsBackToStatus() =
        assertEquals("Reading", badgeText(reading, AppSettings(showProgressBadge = false)))

    @Test fun bothOffIsNull() =
        assertNull(badgeText(reading, AppSettings(showProgressBadge = false, showStatusBadge = false)))

    @Test fun wantHasNoBadge() = assertNull(badgeText(reading.copy(status = ReadingStatus.WANT), AppSettings()))

    @Test fun readAndDnfLabels() {
        assertEquals("Read", badgeText(reading.copy(status = ReadingStatus.READ), AppSettings()))
        assertEquals("DNF", badgeText(reading.copy(status = ReadingStatus.DNF), AppSettings()))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat testDebugUnitTest --tests "*.BadgeTest"`

- [ ] **Step 3: Badge logic** — `ui/library/Badge.kt`

```kotlin
package com.exc33ded.library.ui.library

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.progressPercent

fun badgeText(b: BookEntity, s: AppSettings): String? {
    if (s.showProgressBadge && b.status == ReadingStatus.READING) {
        progressPercent(b.currentPage, b.pageCount)?.let { return "$it%" }
    }
    if (!s.showStatusBadge) return null
    return when (b.status) {
        ReadingStatus.READ -> "Read"
        ReadingStatus.DNF -> "DNF"
        ReadingStatus.READING -> "Reading"
        ReadingStatus.WANT -> null
    }
}
```

Run the test again, expect PASS.

- [ ] **Step 4: SettingsViewModel** — `ui/settings/SettingsViewModel.kt`

```kotlin
package com.exc33ded.library.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    // Display values come from LocalAppSettings (Task 3); this ViewModel only writes.
    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.update(transform) }
    }
}
```

- [ ] **Step 5: Shared controls** — `ui/settings/DisplayOptions.kt`

```kotlin
package com.exc33ded.library.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.LibraryDisplayMode
import com.exc33ded.library.settings.MAX_COLUMNS
import com.exc33ded.library.settings.MIN_COLUMNS
import kotlin.math.roundToInt

private val modeLabels = listOf(
    LibraryDisplayMode.OVERLAY_GRID to "Overlay",
    LibraryDisplayMode.CAPTION_GRID to "Caption",
    LibraryDisplayMode.LIST to "List",
)

/** Display mode, column count and badge toggles. Used by the library sheet and the Settings screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayOptions(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Display", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modeLabels.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = settings.displayMode == mode,
                    onClick = { onChange { it.copy(displayMode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(i, modeLabels.size),
                ) { Text(label) }
            }
        }
        if (settings.displayMode != LibraryDisplayMode.LIST) {
            Text("Columns: ${settings.columns}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.columns.toFloat(),
                onValueChange = { v ->
                    val n = v.roundToInt() // fires every drag frame: only write when the step changes
                    if (n != settings.columns) onChange { it.copy(columns = n) }
                },
                valueRange = MIN_COLUMNS.toFloat()..MAX_COLUMNS.toFloat(),
                steps = MAX_COLUMNS - MIN_COLUMNS - 1,
            )
        }
        SwitchRow("Show progress", settings.showProgressBadge) { v -> onChange { it.copy(showProgressBadge = v) } }
        SwitchRow("Show status", settings.showStatusBadge) { v -> onChange { it.copy(showStatusBadge = v) } }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChecked)
    }
```

- [ ] **Step 6: Tiles** — `ui/library/LibraryItems.kt`

```kotlin
package com.exc33ded.library.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.progressPercent
import com.exc33ded.library.ui.common.BookCover

@Composable
private fun BadgePill(text: String, modifier: Modifier) = Text(
    text,
    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onPrimary,
    modifier = modifier
        .padding(6.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.primary)
        .padding(horizontal = 6.dp, vertical = 2.dp),
)

@Composable
private fun ProgressLine(b: BookEntity, s: AppSettings, modifier: Modifier) {
    val pct = if (s.showProgressBadge && b.status == ReadingStatus.READING) progressPercent(b.currentPage, b.pageCount) else null
    if (pct != null) Box(modifier.fillMaxWidth(pct / 100f).height(3.dp).background(MaterialTheme.colorScheme.primary))
}

/** Mihon-style: title over a bottom gradient, corner badge, thin progress line. */
@Composable
fun OverlayTile(b: BookEntity, s: AppSettings, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        BookCover(b.title, b.coverPath)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)),
            ),
        )
        Text(
            b.title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        )
        badgeText(b, s)?.let { BadgePill(it, Modifier.align(Alignment.TopStart)) }
        ProgressLine(b, s, Modifier.align(Alignment.BottomStart))
    }
}

/** Clean cover with title + author underneath. */
@Composable
fun CaptionTile(b: BookEntity, s: AppSettings, modifier: Modifier = Modifier) = Column(modifier) {
    Box(Modifier.clip(RoundedCornerShape(12.dp))) {
        BookCover(b.title, b.coverPath)
        badgeText(b, s)?.let { BadgePill(it, Modifier.align(Alignment.TopStart)) }
        ProgressLine(b, s, Modifier.align(Alignment.BottomStart))
    }
    Text(b.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    if (b.authors.isNotBlank())
        Text(b.authors, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun ListRow(b: BookEntity, s: AppSettings, modifier: Modifier = Modifier) = Row(
    modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
) {
    BookCover(b.title, b.coverPath, Modifier.width(56.dp))
    Column(Modifier.align(Alignment.CenterVertically)) {
        Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (b.authors.isNotBlank())
            Text(b.authors, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        badgeText(b, s)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    }
}
```

- [ ] **Step 7: Library screen** — modify `ui/library/LibraryScreen.kt`:
  1. Add parameter `settingsVm: SettingsViewModel = hiltViewModel()` and `val cfg = LocalAppSettings.current` (import `com.exc33ded.library.ui.theme.LocalAppSettings`; do NOT collect settings in this screen); add `var showDisplay by remember { mutableStateOf(false) }`.
  2. Replace the lone `OutlinedTextField` with a `Row(verticalAlignment = Alignment.CenterVertically)` containing the search field (`Modifier.weight(1f)`) and `IconButton(onClick = { showDisplay = true }) { Icon(Icons.Filled.GridView, contentDescription = "Display options") }`. (`GridView` is in the extended icon set already used for `AutoStories`.)
  3. Replace the `else ->` grid branch with:
     ```kotlin
     else -> if (cfg.displayMode == LibraryDisplayMode.LIST) {
         LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
             items(s.books, key = { it.id }) { b ->
                 ListRow(b, cfg, Modifier.animateItem().clickable { onOpenBook(b.id) })
             }
         }
     } else {
         LazyVerticalGrid(
             columns = GridCells.Fixed(cfg.columns),
             contentPadding = PaddingValues(bottom = 16.dp),
             horizontalArrangement = Arrangement.spacedBy(if (cfg.columns >= 4) 8.dp else 12.dp),
             verticalArrangement = Arrangement.spacedBy(if (cfg.columns >= 4) 8.dp else 16.dp),
         ) {
             items(s.books, key = { it.id }) { b ->
                 val m = Modifier.animateItem().clickable { onOpenBook(b.id) }
                 if (cfg.displayMode == LibraryDisplayMode.OVERLAY_GRID) OverlayTile(b, cfg, m) else CaptionTile(b, cfg, m)
             }
         }
     }
     ```
  4. After the `Column`, add the sheet:
     ```kotlin
     if (showDisplay) ModalBottomSheet(onDismissRequest = { showDisplay = false }) {
         DisplayOptions(cfg, settingsVm::update, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp))
     }
     ```
     (`ModalBottomSheet` needs `@OptIn(ExperimentalMaterial3Api::class)` on `LibraryScreen`.)
  5. Delete the now-unused private `BookTile` and its unused imports (`LazyVerticalGrid` stays). Add the imports for the new symbols.

- [ ] **Step 8: Verify** — `.\gradlew.bat testDebugUnitTest` (all tests, incl. existing 55) and `.\gradlew.bat assembleDebug`. Main session commits `feat(library): switchable display modes, columns, badges`.

---

### Task 5: Settings screen + Profile entry + navigation

**Files:**
- Create: `ui/settings/SettingsScreen.kt`, `ui/profile/ProfileScreen.kt`
- Modify: `ui/nav/Keys.kt`, `ui/nav/AppNav.kt`

- [ ] **Step 1: Route** — in `ui/nav/Keys.kt` add `@Serializable data object SettingsKey : NavKey`.

- [ ] **Step 2: Profile** — `ui/profile/ProfileScreen.kt`

```kotlin
package com.exc33ded.library.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ponytail: stats/profile content arrives in Phase 5; this tab only hosts Settings for now.
@Composable
fun ProfileScreen(onOpenSettings: () -> Unit) = Column(Modifier.fillMaxSize().statusBarsPadding()) {
    ListItem(
        headlineContent = { Text("Settings") },
        leadingContent = { Icon(Icons.Filled.Settings, null) },
        modifier = Modifier.clickable(onClick = onOpenSettings),
    )
}
```

- [ ] **Step 3: Settings screen** — `ui/settings/SettingsScreen.kt`

```kotlin
package com.exc33ded.library.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.exc33ded.library.settings.ThemeMode
import com.exc33ded.library.ui.theme.LocalAppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s = LocalAppSettings.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = s.themeMode == m,
                        onClick = { vm.update { it.copy(themeMode = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
            SwitchRow("Dynamic colors (wallpaper)", s.dynamicColor) { v -> vm.update { it.copy(dynamicColor = v) } }
            SwitchRow("AMOLED black (dark only)", s.amoled) { v -> vm.update { it.copy(amoled = v) } }

            Text("Library", style = MaterialTheme.typography.titleMedium)
            DisplayOptions(s, vm::update)

            Text("On-device AI", style = MaterialTheme.typography.titleMedium)
            // ponytail: disabled until the model manager (sub-project 4) exists.
            ListItem(
                headlineContent = { Text("Models & storage") },
                supportingContent = { Text("Coming with on-device AI") },
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
```
(If `Icons.AutoMirrored.Filled.ArrowBack` does not resolve, use whichever back icon `BookDetailScreen.kt` already uses.)

- [ ] **Step 4: Wire nav** — in `ui/nav/AppNav.kt` replace `entry<ProfileKey> { PlaceholderScreen("Profile — Phase 5") }` with:

```kotlin
entry<ProfileKey> { ProfileScreen(onOpenSettings = { backStack.add(SettingsKey) }) }
entry<SettingsKey> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
```
and add imports `com.exc33ded.library.ui.profile.ProfileScreen`, `com.exc33ded.library.ui.settings.SettingsScreen`.

- [ ] **Step 5: Verify** — `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleDebug` both succeed. Main session commits `feat(settings): Settings screen and Profile entry`.

---

### Task 6: Verify on the emulator, update handoff (main session only)

- [ ] **Step 1:** Start `library_pixel`, `.\gradlew.bat installDebug`, launch the app.
- [ ] **Step 2:** Walk this checklist and screenshot each (`adb exec-out screencap -p`):
  1. Library default = Overlay grid, 3 columns, badges visible.
  2. Display sheet: switch Overlay → Caption → List; slider 2…6 changes columns; badge toggles work.
  3. Profile → Settings: Theme Light/Dark/System, Dynamic off → Ink & amber, AMOLED on in dark.
  4. Status-bar icons stay readable when Theme is forced opposite to the system setting.
  5. Kill and relaunch: all settings persist.
- [ ] **Step 3:** Fix any defect found (small fixes inline, larger ones via a Sonnet subagent), re-run `testDebugUnitTest`.
- [ ] **Step 4:** Update `docs/HANDOFF.md` (status table, this sub-project done, next = matching fixes + OCR comparison, then AI model comparison, then model manager) and commit `docs: handoff after UI overhaul`.

## Self-review against the spec

- Theme (System/Light/Dark, Dynamic default on, AMOLED, Ink & amber fallback, DataStore) → Tasks 1–3.
- Library (3 modes, 2–6 columns, badges, sheet, unchanged chips/search/filter) → Task 4.
- Settings screen from Profile with disabled "Models & storage" → Task 5.
- Out of scope items (model manager, Feed/Stats, motion polish) are not touched.
- Tests: pure helpers + repository + badge logic (Tasks 1, 2, 4); manual emulator check (Task 6).
- Names used across tasks: `AppSettings`, `ThemeMode`, `LibraryDisplayMode`, `clampColumns`, `isDark`, `progressPercent`, `SettingsRepository.update/settings`, `SettingsViewModel.update/settings`, `DisplayOptions`, `SwitchRow`, `badgeText`, `OverlayTile`/`CaptionTile`/`ListRow` are consistent.
