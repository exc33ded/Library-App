# UI overhaul + Settings screen — design

Date: 2026-11-22. Sub-project 1 of 4 (others: matching fixes + OCR comparison, AI model comparison, model manager).
Reference look: Mihon (library grid, display modes, column setting).

## Goal
Replace the ordinary-looking library UI with a Mihon-style cover grid, add light/dark/dynamic theming, and add the first Settings screen.

## 1. Theme (`ui/theme/Theme.kt`)
- Settings → Appearance: **Theme** (System / Light / Dark), **Dynamic colors** (default on), **AMOLED black** (dark only).
- Dynamic colors on → Material You wallpaper colors (as today, with improved surfaces).
- Dynamic colors off → fixed **Ink & amber** palette: warm paper (light), near-black (dark), amber accent.
- Keep `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`.
- Preferences stored in DataStore (no settings store exists yet).

## 2. Library screen (`ui/library/`)
- Toolbar action opens a display sheet: **mode** (Overlay grid = default, Caption grid, List), **columns** slider 2–6, toggles for progress/status badges.
- Overlay grid: title on a bottom gradient over the cover, corner badge (progress % / NEW), thin progress bar.
- Caption grid: clean cover, title + author below (better for long and Urdu/Arabic titles).
- List: small cover + title, author, status, progress.
- One `LazyVerticalGrid` with a fixed column count serves both grid modes; only the item composable differs. List uses `LazyColumn`.
- Unchanged: status chips, search, `LibraryFilter.kt` logic.

## 3. Settings screen
- New route in `ui/nav/Keys.kt` / `AppNav.kt`, reached from the Profile tab (currently a placeholder).
- Sections: Appearance, Library display (same options as the sheet, same DataStore keys), and a disabled "Models & storage" row (filled in by sub-project 4).

## 4. Out of scope
Model path/download/delete, Feed and Stats content, Phase 7 motion polish.

## 5. Testing
- Pure, JVM-tested: column clamping (2–6), display-mode mapping, theme-preference defaults.
- Manual on emulator: light, dark, AMOLED, dynamic on/off, all three modes, column changes.

## Decisions (user-confirmed 2026-11-22)
- All three display modes switchable; Overlay grid is the default.
- Dynamic colors on by default; fixed fallback theme = B (Ink & amber). No theme picker.
