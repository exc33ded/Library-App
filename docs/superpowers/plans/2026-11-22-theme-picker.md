# Theme Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. One task, worker = Claude subagent on **Sonnet**, working ONLY in the git worktree `D:\claude-exp\wt-theme` (branch `feat/theme`). The worker does not commit; the main session reviews, merges and commits. Never give a worker `local.properties` or `docs/Books-Photo/`.

**Goal (user, 2026-11-22):** the app must offer *many* colour themes the user can pick in Settings, not just wallpaper colours + one fixed palette. Add six fixed palettes (Ink & amber — already exists, Deep teal, Forest, Ocean, Berry, Graphite) each with light and dark variants, plus the existing "Wallpaper" (Material You) choice, selected from a row of swatches in Settings → Appearance.

**Architecture:** `ThemePalette` enum + `palette` field in `AppSettings` (stored in the existing DataStore). A pure tone generator (`toneColor`) builds Material-style tonal palettes from a hue/saturation by binary-searching HSL lightness for the CIE-L* luminance of each Material tone, so contrast is guaranteed like Material's own palettes. `LibraryTheme` picks: wallpaper if `dynamicColor`, else `paletteScheme(palette, dark)`. Contrast of every palette is asserted in a unit test.

## Toolchain (each PowerShell call)

```powershell
$env:JAVA_HOME="$env:USERPROFILE\scoop\apps\temurin21-jdk\current"
$env:ANDROID_HOME="$env:USERPROFILE\scoop\apps\android-clt\current"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue
cd D:\claude-exp\wt-theme
```
Paths relative to the worktree: main = `app/src/main/java/com/exc33ded/library/`, test = `app/src/test/java/com/exc33ded/library/`. Never write slash-star inside a Kotlin comment.

---

### Task 1: Settings field, palette generator, theme wiring, picker UI

**Read first:** `settings/AppSettings.kt`, `settings/SettingsRepository.kt`, `ui/theme/Palette.kt` (existing `InkLight`/`InkDark`), `ui/theme/Theme.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/DisplayOptions.kt`, and the tests `settings/AppSettingsTest.kt`, `settings/SettingsRepositoryTest.kt`.

- [ ] **Step 1: Failing tests.**
  - `AppSettingsTest.defaultsMatchSpec`: add `assertEquals(ThemePalette.INK_AMBER, d.palette)`.
  - `SettingsRepositoryTest`: add a one-update test persisting `palette = ThemePalette.OCEAN` and reading it back.
  - New `ui/theme/PaletteTest.kt`:
```kotlin
package com.exc33ded.library.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.exc33ded.library.settings.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val l1 = a.luminance(); val l2 = b.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    @Test fun toneEndpointsAreBlackAndWhite() {
        assertEquals(Color.Black, toneColor(200f, 0.5f, 0))
        assertEquals(Color.White, toneColor(200f, 0.5f, 100))
    }

    @Test fun toneLuminanceFollowsCieLightness() {
        // tone 50 -> Y = ((50+16)/116)^3 = 0.1842
        assertTrue(abs(toneColor(210f, 0.6f, 50).luminance() - 0.1842f) < 0.01f)
        assertTrue(toneColor(130f, 0.5f, 90).luminance() > toneColor(130f, 0.5f, 40).luminance())
    }

    @Test fun everyPaletteMeetsContrastInLightAndDark() {
        for (p in ThemePalette.entries) for (dark in listOf(false, true)) {
            val s = paletteScheme(p, dark)
            val pairs = listOf(
                "onPrimary/primary" to contrast(s.onPrimary, s.primary),
                "onPrimaryContainer/primaryContainer" to contrast(s.onPrimaryContainer, s.primaryContainer),
                "onSecondaryContainer/secondaryContainer" to contrast(s.onSecondaryContainer, s.secondaryContainer),
                "onTertiaryContainer/tertiaryContainer" to contrast(s.onTertiaryContainer, s.tertiaryContainer),
                "onSurface/surface" to contrast(s.onSurface, s.surface),
                "onBackground/background" to contrast(s.onBackground, s.background),
                "onSurfaceVariant/surfaceVariant" to contrast(s.onSurfaceVariant, s.surfaceVariant),
            )
            for ((name, c) in pairs) assertTrue("$p dark=$dark $name = $c", c >= 4.5f)
        }
    }

    @Test fun lightAndDarkDiffer() {
        for (p in ThemePalette.entries) assertTrue(paletteScheme(p, false).surface != paletteScheme(p, true).surface)
    }
}
```
  Run `.\gradlew.bat testDebugUnitTest --tests "*.PaletteTest" --tests "*.AppSettingsTest" --tests "*.SettingsRepositoryTest"` → expect FAIL (unresolved references).

- [ ] **Step 2: Settings.** In `AppSettings.kt` add `enum class ThemePalette { INK_AMBER, DEEP_TEAL, FOREST, OCEAN, BERRY, GRAPHITE }` and a LAST field `val palette: ThemePalette = ThemePalette.INK_AMBER`. In `SettingsRepository`: key `PALETTE = stringPreferencesKey("palette")`, written in `update` (`p[PALETTE] = n.palette.name`) and read in `read` with the same `runCatching { valueOf }` fallback pattern as the other enums.

- [ ] **Step 3: Generator** — append to `ui/theme/Palette.kt` (keep `InkLight`/`InkDark` as they are):
```kotlin
/** A colour with the given hue/saturation whose perceptual tone (CIE L*, 0 black .. 100 white) is [tone], like a Material tonal palette. */
fun toneColor(hue: Float, sat: Float, tone: Int): Color {
    if (tone <= 0) return Color.Black
    if (tone >= 100) return Color.White
    val target = if (tone > 8) Math.pow((tone + 16) / 116.0, 3.0).toFloat() else (tone / 903.3).toFloat()
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2
        if (Color.hsl(hue, sat, mid).luminance() < target) lo = mid else hi = mid
    }
    return Color.hsl(hue, sat, (lo + hi) / 2)
}

fun paletteScheme(p: ThemePalette, dark: Boolean): ColorScheme = when (p) {
    ThemePalette.INK_AMBER -> if (dark) InkDark else InkLight
    ThemePalette.DEEP_TEAL -> generatedScheme(174f, 0.65f, dark)
    ThemePalette.FOREST -> generatedScheme(135f, 0.45f, dark)
    ThemePalette.OCEAN -> generatedScheme(214f, 0.75f, dark)
    ThemePalette.BERRY -> generatedScheme(332f, 0.55f, dark)
    ThemePalette.GRAPHITE -> generatedScheme(220f, 0.10f, dark)
}

private fun generatedScheme(hue: Float, sat: Float, dark: Boolean): ColorScheme {
    fun primary(t: Int) = toneColor(hue, sat, t)
    fun secondary(t: Int) = toneColor(hue, sat * 0.35f, t)
    fun tertiary(t: Int) = toneColor((hue + 60f) % 360f, sat * 0.6f, t)
    fun neutral(t: Int) = toneColor(hue, 0.06f, t)
    fun variant(t: Int) = toneColor(hue, 0.10f, t)
    return if (!dark) lightColorScheme(
        primary = primary(40), onPrimary = Color.White, primaryContainer = primary(90), onPrimaryContainer = primary(10),
        secondary = secondary(40), onSecondary = Color.White, secondaryContainer = secondary(90), onSecondaryContainer = secondary(10),
        tertiary = tertiary(40), onTertiary = Color.White, tertiaryContainer = tertiary(90), onTertiaryContainer = tertiary(10),
        background = neutral(98), onBackground = neutral(10), surface = neutral(98), onSurface = neutral(10),
        surfaceVariant = variant(90), onSurfaceVariant = variant(30),
        surfaceContainerLowest = Color.White, surfaceContainerLow = neutral(96), surfaceContainer = neutral(94),
        surfaceContainerHigh = neutral(92), surfaceContainerHighest = neutral(90),
        outline = variant(50), outlineVariant = variant(80),
    ) else darkColorScheme(
        primary = primary(80), onPrimary = primary(20), primaryContainer = primary(30), onPrimaryContainer = primary(90),
        secondary = secondary(80), onSecondary = secondary(20), secondaryContainer = secondary(30), onSecondaryContainer = secondary(90),
        tertiary = tertiary(80), onTertiary = tertiary(20), tertiaryContainer = tertiary(30), onTertiaryContainer = tertiary(90),
        background = neutral(6), onBackground = neutral(90), surface = neutral(6), onSurface = neutral(90),
        surfaceVariant = variant(30), onSurfaceVariant = variant(80),
        surfaceContainerLowest = neutral(4), surfaceContainerLow = neutral(10), surfaceContainer = neutral(12),
        surfaceContainerHigh = neutral(17), surfaceContainerHighest = neutral(22),
        outline = variant(60), outlineVariant = variant(30),
    )
}
```
  (imports: `androidx.compose.material3.ColorScheme`, `androidx.compose.ui.graphics.luminance`, `com.exc33ded.library.settings.ThemePalette`.) If a handcrafted `INK_AMBER` pair fails the contrast test, fix that colour in `InkLight`/`InkDark` minimally and report it; if a generated palette fails, adjust only that palette's saturation and report it.

- [ ] **Step 4: Theme.** In `ui/theme/Theme.kt` replace the fixed-palette branch (`dark -> InkDark else InkLight`) with `else -> paletteScheme(settings.palette, dark)` (dynamic branch unchanged).

- [ ] **Step 5: Picker UI.** New `ui/settings/ThemePicker.kt` with `@Composable fun ThemePicker(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit)`:
  - A title `Text("Color theme", titleSmall)` and a `LazyRow(horizontalArrangement = spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp))` of swatches. The first swatch is **Wallpaper** (preview scheme = `dynamicDarkColorScheme(ctx)`/`dynamicLightColorScheme(ctx)` for the current dark/light resolution); then one per `ThemePalette` with labels `INK_AMBER → "Ink & amber"`, `DEEP_TEAL → "Deep teal"`, `FOREST → "Forest"`, `OCEAN → "Ocean"`, `BERRY → "Berry"`, `GRAPHITE → "Graphite"`. The dark/light resolution uses `isDark(settings.themeMode, isSystemInDarkTheme())`, so previews match what will be shown.
  - Swatch = `Column(Modifier.width(76.dp).clickable { … }, horizontalAlignment = CenterHorizontally)`: a 64 dp rounded square (`RoundedCornerShape(20.dp)`) filled with `scheme.surface`, with three stacked/side-by-side colour blocks inside (primary, primaryContainer, tertiary) drawn with `Box` backgrounds, a 3 dp `scheme.primary` border and a `Icons.Filled.Check` badge when selected (otherwise a 1 dp `outlineVariant` border); the label below in `labelMedium`, one line, ellipsis. Selected = `settings.dynamicColor` for Wallpaper, or `!settings.dynamicColor && settings.palette == p`. Clicking Wallpaper calls `onChange { it.copy(dynamicColor = true) }`; clicking a palette calls `onChange { it.copy(dynamicColor = false, palette = p) }`.
  - Add a `contentDescription`/semantics `selected` for accessibility.
- [ ] **Step 6: Settings screen.** In `SettingsScreen.kt` replace the "Dynamic colors (wallpaper)" `SwitchRow` with `ThemePicker(s, vm::update)`; keep the theme-mode segmented row above it and the AMOLED switch below it.
- [ ] **Step 7: Verify** — run the three test classes (all pass), then `.\gradlew.bat testDebugUnitTest` (everything passes) and `.\gradlew.bat assembleDebug`. Do NOT commit.
