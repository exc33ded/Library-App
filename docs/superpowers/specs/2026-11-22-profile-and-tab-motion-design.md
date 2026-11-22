# Profile + tab motion — design

Date: 2026-11-22. Sub-project 5. Decisions from the user (2026-11-22): Profile has **name + photo, reading goal, stats summary, bio/quote**; tab switches use a **slide + fade**.

## Goal
Give the Profile tab real content, and make switching between Library · Feed · Stats · Profile feel animated instead of an instant swap.

## 1. Profile data (`profile/`)
- `ProfileRepository` (DataStore Preferences, same pattern as `SettingsRepository`, own file `profile`): `name: String`, `bio: String`, `yearlyGoal: Int?` (null = no goal set), `avatarPath: String?`.
- `Profile` is a plain data class with defaults (empty name/bio, no goal, no avatar). Name max 40 chars, bio max 140 (clamped on write).
- Avatar: picked with `ActivityResultContracts.PickVisualMedia`, decoded with `ImageDecoder`, centre-cropped to a square, downscaled to 512 px, saved to `filesDir/profile/avatar.jpg` (overwritten; path stored). Removing the photo deletes the file. Phone-only, like everything else (not part of any cloud sync).
- Like settings, the profile is collected once in `MainActivity` and exposed as `LocalProfile` so tab switches (which clear ViewModels) never flash empty values.

## 2. Stats (`profile/ProfileStats.kt`, pure)
`computeStats(books, nowMillis, zone)` returns: `read` (status READ), `reading`, `want`, `dnf`, `pagesRead` (sum of `pageCount` for READ books + `currentPage` for READING), `averageRating` (null when no rated books), `readThisYear` (READ with `finishedAt` in the current calendar year). Unit-tested with fixed clocks around a year boundary.

## 3. Profile screen (`ui/profile/ProfileScreen.kt`)
Top to bottom: avatar (initials on a tonal circle when no photo) + name + bio/quote; **Edit** button; **goal card** (progress ring, "N of M this year", or "Set a reading goal" when null); **stats card** (read / reading / want / DNF counts, pages, average rating); **Settings** row (existing). Edit opens a bottom sheet with name, bio, goal (number field, empty clears it) and photo pick/remove; Save writes the repository. Library shows a small greeting "Hi, <name>" above the search row only when a name is set.

## 4. Tab motion (`ui/nav/AppNav.kt`)
- Top-level tab switches animate with **slide + fade**: the new tab enters from the side matching its position in the bar (moving right → enters from the right, exits to the left; reverse when moving left) and fades; slide distance is a third of the width so it reads as a nudge, not a full-page swipe.
- Timing/easing come from `MaterialTheme.motionScheme` (project rule: spatial spec for the slide, effects spec for the fade); values are read in deferred lambdas (`graphicsLayer {}`/`offset {}`) per CLAUDE.md. System "remove animations" is honoured automatically by the animator scale.
- Pushing detail screens (book detail, edit, settings) keeps the existing default transition; only top-level switches change.
- Needs a short spike first: how Navigation 3's `NavDisplay` `transitionSpec` behaves when the back stack is replaced (`clear(); add(key)`) rather than pushed; if it does not fire for a same-size replace, drive the animation from a `previousTab` index kept in `AppNav` state.

## 5. Out of scope
Cloud profile, multiple profiles, avatar cropping UI beyond the centre crop, the Stats tab content (Phase 5 proper), the Feed.

## 6. Testing
Pure/JVM: `computeStats` (year boundary, unrated books, empty library), profile clamping (name/bio length, goal ≥ 1 or null), repository round-trip (same temp-file DataStore pattern as `SettingsRepositoryTest`). Manual on the emulator: edit + persist across relaunch, photo pick/remove, goal ring at 0 %, partial and ≥ 100 %, light/dark/AMOLED, tab slide in both directions, detail screens unchanged.
