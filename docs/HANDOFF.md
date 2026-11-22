# Handoff — 2026-11-22 (full build: everything except on-device AI)

Personal Android book library app (Kotlin + Compose). Read `CLAUDE.md` first (toolchain env vars,
commands, architecture, project rules), then the spec
`docs/superpowers/specs/2026-11-21-book-library-app-design.md`.

Repo: `exc33ded/Library-App` (public). Local `main` is **far ahead of the remote and nothing was pushed** — push only when the user says so.
The APK to install is `app/build/outputs/apk/debug/app-debug.apk` (debug build, ~125 MB; `.\gradlew.bat assembleDebug`).

## Update after the user's real-phone testing (2026-11-22, later) — read this first

The user installed the debug APK on their phone and sent screenshots (kept in `docs/screenshots/`, gitignored — private). Fixed since:
- **Install the release build, not debug.** `.\gradlew.bat assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (~47 MB vs 119 MB): R8 + resource shrinking + a hand-written baseline profile (`app/src/main/baseline-prof.txt`) + lighter shared-axis transitions (short tweens, small offsets; the old spring/full-width slides were the "laggy" feel on a budget phone; debug builds run Compose unoptimised). It is signed with the **debug key on purpose**, so it installs over an existing debug install and keeps the data. Optional full pre-compile on the phone: `adb shell cmd package compile -m speed-profile -f com.exc33ded.library`. Emulator frame stats were inconclusive (x86 emulator ≠ budget ARM phone): 99th-percentile frame 400 ms → 150 ms, jank rate unchanged.
- **Scanner:** the "works from + but not from the search icon" bug was Google's code-scanner module downloading on first use (`MlKitException: Waiting for the Barcode UI module to be downloaded`); both entry points share one flow that now pre-installs the module and waits, shows "Downloading the scanner (one time)…", and falls back to an "Enter ISBN" action. R8 had also broken the Google clients (NPE) → `-keep` rules for `com.google.android.gms.**`/`mlkit` in `app/proguard-rules.pro`. **Not provable on the emulator** (no Play account → download fails → clear message); needs the user's phone.
- **UI:** pill-shaped search field (`ui/common/SearchField.kt`, one-line placeholder), status filter as scrollable pill chips (no mid-word wrap), **Dismiss** per card + "Dismiss failed"/"Dismiss all" in Review imports, **theme picker** (Wallpaper + Ink & amber, Deep teal, Forest, Ocean, Berry, Graphite, each light+dark, tone-generated and contrast-tested in `PaletteTest`), **cover editing** in the add/edit/verify form (gallery, camera, remove, use online cover), **save feedback** (Idle → Saving → Saved button, sheet-style slide-down, "Saved “title”" snackbar; "Adding…" + snackbar for one-tap import Add). Checked on emulator: theme picker, cover sheet, save flow, the scan message. **Not visually checked:** Review-imports Dismiss buttons (queue was empty; DAO logic is unit-tested), picking/taking an actual cover photo, the "Saving…" spinner frame.
- **App icons:** `docs/icons/app-icons.html` shows six concept icons (open the file in a browser; palette + launcher-shape switchers). The user has not picked one yet; the next step is to turn the chosen concept into the adaptive launcher icon (foreground/background/monochrome vectors) and rename the app label from "Library" to "Words and Wounds".
- 176 JVM tests pass (1 skipped: the env-gated benchmark replay).

## What the app does now

| Area | Status |
|---|---|
| Library: Overlay / Caption / List views, 2–6 columns, progress + status badges, search over title, author **and notes**, status chips, categories | ✅ checked on emulator |
| Add menu ("+"): animated scrim + staggered action pills (photo, gallery, scan ISBN, search, manual), "+" morphs to "x"; scan button also next to the search box | ✅ checked on emulator |
| Add by ISBN scan (Play-services scanner), Open Library lookup, cover download | ✅ built; **scanner untested on a real phone** |
| Cover photo / gallery batch import: ML Kit OCR → query variants → fuzzy candidate ranking; **a valid ISBN printed in the photo is looked up first** | ✅ **64% strict / 74% author-lenient** on the 96 photos (was 33%/43%) |
| Profile: name, photo (gallery pick, **untested**), bio, yearly goal ring, stats card, "Hi, name" on Library, Backup + Settings rows | ✅ checked on emulator |
| Stats tab: year selector, books/pages/avg rating, books-per-month chart, top categories, rating bars | ✅ checked on emulator |
| Feed tab: 8 verified RSS feeds, pull-to-refresh, unread bold titles, tap opens browser, Manage feeds (toggle/add/remove/restore) | ✅ list checked live; manage screen and offline behaviour **not** hand-tested |
| Settings: Light/Dark/System, Dynamic colors (Ink & amber fallback), AMOLED, display options, **Your data → Backup & restore**, **On-device AI → Models & storage** | ✅ checked on emulator |
| **Backup & restore** (Settings and Profile): export `.zip` (books, categories, links, notes, cover images, profile text) via the system save dialog; import merges (newer `updatedAt` wins) | ✅ **full round trip on emulator**: export → wipe app data → import ("1 new") → book, cover, status, profile text all back; importing again → "0 new, 0 updated, 1 unchanged" |
| **Models & storage**: choose models folder (Phone storage default / App folder / SD card if present), free space + usage, Wi-Fi-only switch, 4 free ungated models (Qwen3 0.6B recommended, Qwen3 1.7B, Qwen3.5 0.8B Vision, Gemma 4 E2B) with Download / Cancel / Resume / Delete | ✅ **real download of Qwen3 0.6B on emulator**: progress, cancel → "Paused at 98 MB" → resume continues from 98 MB, finishes at exactly 343,490,560 bytes with checksum check, delete empties the folder. The other three models were not downloaded in tests. |
| Tab switches: slide + fade, direction follows the tab order | ✅ confirmed from a 10×-slowed screen recording |
| **Not built:** on-device AI that *uses* the downloaded models (title correction, summaries, vision cover reading), reading streaks/badges (need a reading-log table), "new from your authors" in Feed, shared-element/pull-tension/swipe motion polish (Phase 7) | next |

Where data lives: database, covers, photos and profile photo are in the app's **private storage** (deleted when the app is uninstalled — use Backup & restore to move or keep them). Only the models folder is relocatable. A database path option was deliberately not built (a live Room database on removable/shared storage can corrupt or vanish; Backup export is the supported way to move data).

165 JVM unit tests pass, 1 skipped (the env-gated benchmark replay): `.\gradlew.bat testDebugUnitTest`.

## Specs and plans (this session)

Specs in `docs/superpowers/specs/`: ui-overhaul-settings, matching-and-ocr, profile-and-tab-motion, model-manager-and-storage (all dated 2026-11-22).
Plans in `docs/superpowers/plans/`: ui-overhaul-settings, matching-and-ocr, profile-and-tab-motion, model-manager-and-storage, add-menu, backup, feed, stats.
Feature branches `feat/isbn-notes`, `feat/backup`, `feat/stats`, `feat/feed` are merged into `main` (the worktrees `D:\claude-exp\wt-*` were created for parallel work and can be deleted with `git worktree remove`).

## Decisions (don't re-litigate)

- **Storage is phone-only.** No Supabase/accounts. Schema keeps sync-ready columns (`updatedAt`, `deleted`, `dirty`, UUIDs). Backup/export is the way to move or keep data.
- **No Google Books key** (user: skip it). Open Library only; the keyless Google Books fallback stays in code but is rate-limited. If a key is ever added: `googleBooksKey=…` in `local.properties`, restrict it to the Books API, never commit it.
- **Coding is delegated; the main session is the "head"** (plans, reviews, re-runs tests, commits). Workers: Claude subagents on Sonnet (default for app code) and **opencode free models as an "intern"** for small, fully specified tasks (`opencode run --auto -m opencode/hy4-flash-free "<prompt>"`; `--auto` is required headless, user approved it). Never give a worker `local.properties` or `docs/Books-Photo/`. Parallel work used git worktrees; the machine has 15 GB RAM, so at most two Gradle builds at once. opencode did the Paddle script, Profile logic, models settings/catalog/helpers, ISBN+notes, and the pure cores of backup, Stats and Feed (all reviewed and re-tested by the main session). `opencode stats --days 1 --models` shows its real token use.
- Target phone: **Samsung Galaxy A07-class, 6 GB RAM** → AI optional and background-only; default cover reading = ML Kit OCR + online lookup.
- Model catalog (all Apache-2.0, **not gated**, `.litertlm`, sizes/SHA-256 from the HF API): Qwen3-0.6B INT4 343 MB, Qwen3-1.7B INT4 977 MB, Qwen3.5-0.8B-VL 1.30 GB, Gemma 4 E2B 2.59 GB. Gemma 3n is excluded (manual-approval gate). Whether each file runs on the bundled LiteRT-LM is validated in Phase 3.
- Single Gradle module; packages per area.

## OCR benchmark (spec §14)

96 private photos in `docs/Books-Photo/` (gitignored), labels in `docs/Books-Photo/labels.csv` (drafted by subagents — **user has not confirmed**; the "Not a book" row is skipped, so 95 are scored).

| Run | Strict | Author-lenient |
|---|---|---|
| Baseline (Phase 2b, first-result matching) | 32/96 (33%) | ~41 (~43%) |
| New matching, ML Kit text, first cut | 58/95 (61%) | 66 (69%) |
| + big-word coverage, confidence 0.75, best-overall fallback | **61/95 (64%)** | **71 (74%)** |
| PaddleOCR PP-OCRv6, speed shortcuts (960 px detection / skipping rotations) | 48/95 (50%) | 53 (55%) |

**OCR verdict: inconclusive; ML Kit stays the default.** Both Paddle runs so far were *unfair*: the 960 px detection cap and the "skip rotations when the upright read has ≥15 letters" shortcut each degraded PP-OCRv6 badly (the same Yellowface photo read "YELLOWFACE" with 3 rotations at default size but "YELLOLEACE" with the shortcut). The fair setup (3 rotations, default detection, `tools/ocr_paddle.py` as committed) costs ~50–90 s per photo on CPU, i.e. 1.5–2.5 h for all 96, and starves everything else — it was stopped. Partial output (first ~5 photos) is in `docs/Books-Photo/ocr_paddle.json`.
To finish it on an idle machine (e.g. overnight): `$env:PYTHONIOENCODING="utf-8"; $env:PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK="True"; C:\Users\sarim\paddle-venv\Scripts\python.exe tools\ocr_paddle.py docs\Books-Photo docs\Books-Photo\ocr_paddle.json` (Python 3.12 venv outside the repo; Windows needs `enable_mkldnn=False`, already in the script), then replay (below) with `BENCH_OCR` pointing at it. Rule from the matching spec: adopt Paddle only if ≥ 8 points better than ML Kit **and** an Android runtime fits the 6 GB phone. New fact: `litert-community/PP-OCRv6-Small-LiteRT` exists on Hugging Face (det 12.4 MB + rec 21.5 MB `.tflite`), so an on-device PP-OCRv6 is realistic if the numbers justify it.

**Replay without the emulator:** the ML Kit OCR text is in `docs/Books-Photo/ocr_mlkit.json` and Open Library answers are cached in `docs/Books-Photo/ol_cache.json`, so tuning `imports/Matching.kt` is a replay:
```powershell
$env:BENCH_OCR="D:\claude-exp\words-and-wounds\docs\Books-Photo\ocr_mlkit.json"      # or ocr_paddle.json
$env:BENCH_LABELS="D:\claude-exp\words-and-wounds\docs\Books-Photo\labels.csv"
$env:BENCH_OUT="D:\claude-exp\words-and-wounds\docs\Books-Photo\report.txt"
$env:BENCH_CACHE="D:\claude-exp\words-and-wounds\docs\Books-Photo\ol_cache.json"
.\gradlew.bat testDebugUnitTest --tests "*.MatchBenchmarkTest" --rerun-tasks   # prints strict/lenient, writes the report
```
Fresh ML Kit dump (if OCR code changes): `adb shell am broadcast -n com.exc33ded.library/.debug.OcrDumpReceiver` with the app in the foreground, wait for `files/ocr_mlkit.done`, then `adb exec-out run-as com.exc33ded.library cat files/ocr_mlkit.json` (PowerShell, not Git Bash).
Remaining misses: OCR misreads (Kite Runner, Mountains Echoed), Urdu/Islamic-press covers, near-miss titles — what the AI title-correction stage and a vision model are for.

## Next steps, in order

1. **User, on a real phone:** install the APK and check ISBN scan (the "+" menu or the button beside search), camera photo import, Profile photo pick, Feed manage screen, Backup export/import with real files, and the add-menu actions. Also confirm/correct `labels.csv`.
2. **Phase 3 — on-device AI** (the downloaded models are only stored for now): LiteRT-LM runtime, AI title correction as an optional stage after the fuzzy matching (corrected string is only a search query; the candidate must still match the original OCR text), summaries, optional vision cover reader for non-Latin covers. Validate each catalog model on the bundled runtime and measure speed on the target phone.
3. Finish the fair PaddleOCR run (above) and record the decision in spec §14.
4. Ideas parked: Inventaire as a third lookup source (OpenShelf idea, evaluate with the benchmark cache), saved filters/"shelves" and a Paused status, reading streaks + badges (needs a reading-log table), "new from your authors" feed, Phase 7 motion polish.

## Gotchas

- Use the JDK 21 env vars from `CLAUDE.md`; system `java` is 8. If `GRADLE_USER_HOME` is preset to a scoop dir without a wrapper distribution, run `Remove-Item Env:GRADLE_USER_HOME` before `gradlew`.
- Memory: 15 GB machine; emulator ~1.7 GB + each Gradle build ~3 GB. Two concurrent builds max; stop idle daemons with `.\gradlew.bat --stop`. A starved emulator freezes (clock stops) — don't conclude anything from it.
- Tab switches clear the back stack (and ViewModels), so settings and profile are read from `LocalAppSettings` / `LocalProfile` (collected once in `MainActivity`) instead of per-screen collectors.
- DataStore cannot write twice to the same file in a JVM test on Windows (`File.renameTo` won't overwrite) — repository tests use an in-memory `DataStore<Preferences>` fake (see `ProfileRepositoryTest`).
- Git Bash rewrites device paths like `/data/...` in `adb` commands and `/tmp` differs from Windows Python's view; use PowerShell or `MSYS_NO_PATHCONV=1`.
- Emulator: quick-boot can show a black screen ~20 s; scripted `adb input tap`s get lost under load; Android's first-use "Try out your stylus" tutorial can pop up on a text field (tap Cancel); `adb input text` needs `%s` for spaces; the system file picker works (Downloads) for backup tests.
- To see animations: `adb shell settings put global animator_duration_scale 10`, `screenrecord`, extract frames with OpenCV; **restore the scale to 1**.
- `OcrDumpReceiver` must not hold the broadcast (`goAsync` caused an ANR); keep the app in the foreground while it runs.
- `opencode run` without `--auto` rejects its own file edits headlessly.
- `java.util.Properties` in `build.gradle.kts` must be imported; Hilt `@AndroidEntryPoint` on a BroadcastReceiver needs an `@EntryPoint` instead (see `ImportDirReceiver`).
- Windows Python console is cp1252: scripts printing non-Latin text need `sys.stdout.reconfigure(encoding="utf-8")`.
- Known deprecations: old `Slider` overload in `BookDetailScreen`, the `ListItem(headlineContent=…)` overload in several screens, `StatusSelector` placeholders for Expressive ButtonGroup.

## Icons & brand (latest)
- App is named "Words and Wounds". Launcher icon = stitched open book, one `activity-alias` per theme (IconInk/Teal/Forest/Ocean/Berry/Graphite); `ui/theme/AppIcon.kt` swaps it when the theme changes (Settings > "Match app icon to theme"; Wallpaper colours use the Ink icon).
- Art is generated by `python tools/gen_icons.py` (writes res/ + docs/icons/svg). Brand page `docs/icons/brand.html` (terms + launcher variants A-F; user has not picked yet, current art = A Classic). Promo/store PNGs in `docs/icons/png/` (from `docs/icons/promo.html#<theme>`).
- 182 unit tests pass; release APK refreshed at `dist/WordsAndWounds-release.apk`.
