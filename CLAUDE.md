# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A personal, single-user Android book library app (Goodreads-like) in Kotlin + Jetpack Compose. Built in 7 phases; the design spec and per-phase plans are the source of truth:

- Spec: `docs/superpowers/specs/2026-11-21-book-library-app-design.md`
- Plans: `docs/superpowers/plans/` (one per phase; Phase 1 = library core)
- Research behind the choices: `reports/Open source Android book app base.md`, `research_notes/`

Read the spec and the current phase plan before changing architecture or adding features.

## Toolchain (Windows, installed via scoop)

The system `java` on PATH is Java 8 — Gradle needs JDK 21. Set these in each shell before building:

```powershell
$env:JAVA_HOME="$env:USERPROFILE\scoop\apps\temurin21-jdk\current"
$env:ANDROID_HOME="$env:USERPROFILE\scoop\apps\android-clt\current"
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
```

## Commands

```powershell
.\gradlew.bat assembleDebug                      # build APK -> app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat testDebugUnitTest                  # all JVM unit tests (Robolectric for Room)
.\gradlew.bat testDebugUnitTest --tests "com.exc33ded.library.data.BookRepositoryTest"   # one class
.\gradlew.bat testDebugUnitTest --tests "*.BookRulesTest.finishingSetsFinishedAtAndLastPage" # one test
.\gradlew.bat installDebug                       # install on connected phone/emulator
```

Test results: `app/build/test-results/testDebugUnitTest/*.xml`. No lint/format task is configured.

## Build quirks

- AGP 9 has **built-in Kotlin**: never apply `org.jetbrains.kotlin.android`. The Kotlin version (2.4.21) is pinned via the root `buildscript` classpath — LiteRT-LM (Phase 3) needs Kotlin ≥ 2.3.
- Compose 1.13 requires **compileSdk 37.1+**, declared as `compileSdk { version = release(37) { minorApiLevel = 2 } }` (SDK package `platforms;android-37.2`).
- All versions live in `gradle/libs.versions.toml` (pinned from Maven on 2026-11-21).
- Room exports schemas to `app/schemas/` — commit new schema JSON with any entity change; it's needed for migrations.
- Robolectric tests use `@Config(sdk = [35])`.

## Architecture

Single Gradle `app` module; packages under `com.exc33ded.library` stand in for the spec's planned modules (`data`, `di`, `ui.*`; later `backup`, `metadata`, `ai`, `scan`, `feed`, `profile`).

- **Room is the single source of truth.** All writes go through `data/BookRepository.kt`, which stamps `updatedAt`, sets `dirty = true`, and soft-deletes (`deleted = true`). Rows are never hard-deleted — storage is phone-only (cloud sync dropped 2026-11-22), but these sync-ready columns are kept so a cloud copy can be added later without a migration; backup import merges by id + newer updatedAt. DAO queries filter `deleted = 0`.
- **Sync-safe IDs:** client-generated UUIDs (`data/Ids.kt`). Book↔category links use `Ids.pair(bookId, categoryId)` (deterministic) so the same link created offline on two devices doesn't duplicate.
- **Domain rules are pure functions** in `data/BookRules.kt` (`withStatus`, `withProgress`: start/finish timestamps, reaching the last page marks READ). Form validation (`ui/book/BookForm.kt`) and library filtering (`ui/library/LibraryFilter.kt`) are also pure and unit-tested; ViewModels just combine flows.
- **UI:** Compose + Navigation 3. `ui/nav/Keys.kt` holds `@Serializable NavKey`s; `ui/nav/AppNav.kt` owns the back stack, bottom bar (Library · Feed · [add] · Stats · Profile), and `entryProvider`. Top-level tabs reset the stack. ViewModels are Hilt; screens with an id use assisted injection (`hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(id) })`).
- **Theme/motion:** `ui/theme/Theme.kt` wraps everything in `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())` with dynamic color. Motion quality is a first-class requirement — take animation specs from `MaterialTheme.motionScheme`, read animated values in deferred lambdas (`graphicsLayer {}`, `offset {}`). `StatusSelector` (SegmentedButton) and the plain add FAB are deliberate placeholders replaced in Phase 7.

## Project rules

- The GitHub repo `exc33ded/Library-App` is **public**: `local.properties` (Google Books key) and `docs/Books-Photo/` (96 private test photos used for the Phase 2/3 accuracy benchmark) are gitignored and must stay that way.
- Target phone is a 6 GB RAM Samsung Galaxy A07-class device: on-device AI must stay optional and background-only (see spec §7).
- Routine plan tasks are delegated to a Claude subagent on **Sonnet** (Agent tool, `model: "sonnet"`), given the full task text from the plan. It may build and run tests but does not commit; the main session reviews the diff, re-runs verification, and commits. Never pass `local.properties` contents to a subagent. (Earlier tasks used `opencode run` with free models; if opencode is used again, free models only.)
- Code adapted from tn3w/Shelf (MIT) must keep attribution; GPL projects (Openreads, Openshelf, NeverTooManyBooks) are reference-only — don't copy their code.
