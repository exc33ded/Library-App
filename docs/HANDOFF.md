# Handoff — 2026-11-22 (updated after matching fixes, Profile and tab motion)

Personal Android book library app (Kotlin + Compose). Read `CLAUDE.md` first (toolchain env vars,
commands, architecture, project rules), then the spec
`docs/superpowers/specs/2026-11-21-book-library-app-design.md`.

Repo: `exc33ded/Library-App` (public) — local `main` is ahead of the remote by many commits — **nothing pushed this session** (public repo; push only when the user says so).

## Where things stand

| Phase | Plan | Status |
|---|---|---|
| 1 Library core (CRUD, statuses, categories, theme, Nav3) | `plans/2026-11-21-phase-1-library-core.md` | ✅ done, checked on emulator |
| 2a Lookup + ISBN scan + covers | `plans/2026-11-22-phase-2a-lookup-and-scan.md` | ✅ done; search→prefill→save checked on emulator. **ISBN scan untested** (needs real phone) |
| 2b Cover photo + batch import | `plans/2026-11-22-phase-2b-photo-import.md` | ✅ built; benchmark run. **Task 8 checklist items 1–5 not hand-tested** (camera, Add/Edit/Wrong-book buttons, resume after kill) |
| UI overhaul + Settings | `plans/2026-11-22-ui-overhaul-settings.md` (spec `specs/2026-11-22-ui-overhaul-settings-design.md`) | ✅ done, checked on emulator: Overlay/Caption/List modes, 2–6 columns, badges, Light/Dark/System, dynamic colors on/off (Ink & amber fallback), AMOLED, persistence across relaunch. Not checked: many-book grids (emulator library has 1 book), real phone |
| Matching fixes + OCR comparison | `plans/2026-11-22-matching-and-ocr.md` (spec `specs/2026-11-22-matching-and-ocr-design.md`) | ✅ matching done: **64% strict / 74% author-lenient** on ML Kit text (was 33%/43%). PaddleOCR comparison **pending** — see Benchmark below |
| Profile + tab motion | `plans/2026-11-22-profile-and-tab-motion.md` (spec `specs/2026-11-22-profile-and-tab-motion-design.md`) | ✅ done, checked on emulator: profile edit (name, bio, goal) saves and persists across relaunch, goal ring, stats card, Library greeting, **slide + fade between tabs confirmed from a 10x-slowed screen recording**. Not checked: photo pick/remove, light theme on Profile, goal ring above 0% |
| 3 On-device AI | not written | next; includes the optional AI title-correction stage (matching spec) |
| 4 Backup (export/import zip) | not written | Supabase **dropped** 2026-11-22 — phone-only storage |
| 5 Stats tab · 6 Feed · 7 Motion polish | not written | Profile screen itself is done (above); Stats/Feed tabs are still placeholders |

103 JVM unit tests pass (1 skipped: the env-gated benchmark replay) (`.\gradlew.bat testDebugUnitTest`).

## Decisions made this session (don't re-litigate)

- **Storage is phone-only.** No Supabase, no accounts. Schema keeps sync-ready columns
  (`updatedAt`, `deleted`, `dirty`, UUIDs) in case cloud is wanted later. Backup = Phase 4 zip export/import.
- **Coding is delegated; the main session is the "head"** (plans, reviews, re-runs tests, commits). Workers: Claude subagents on
  Sonnet (default for app code) and **opencode free models as an "intern"** for small, fully specified tasks
  (`opencode run --auto -m opencode/hy4-flash-free "<prompt>"`; `--auto` is required headless, user approved it). Never give a worker
  `local.properties` or `docs/Books-Photo/`. This session opencode wrote the PaddleOCR script and Profile Tasks 1-3 (all reviewed);
  `opencode stats --days 1 --models` shows its real token use (about 163K input / 21K output at $0 for those runs).
- Target phone: **Samsung Galaxy A07-class, 6 GB RAM** → AI optional and background-only; default
  cover reading = ML Kit OCR + online lookup. Model plan in spec §3/§7 (Qwen3-0.6B summaries,
  LFM2.5-VL-1.6B / Gemma 4 E2B optional experiments).
- Single Gradle module; packages per area.
- UI-skill suggestion (`android-design-ui-architecture`) reviewed and **not installed** — it matches what we already follow.

## Benchmark (spec §14)

96 user photos in `docs/Books-Photo/` (gitignored, **private**), labels in `docs/Books-Photo/labels.csv`
(drafted by subagents — **user has not confirmed yet**; the "Not a book" row is skipped, so 95 are scored).

| Run | Strict | Author-lenient |
|---|---|---|
| Baseline (Phase 2b, first-result matching) | 32/96 (33%) | ~41 (~43%) |
| New matching, ML Kit text, first cut | 58/95 (61%) | 66 (69%) |
| + big-word coverage, stricter confidence (0.75), best-overall fallback | **61/95 (64%)** | **71 (74%)** |
| PaddleOCR PP-OCRv6, detection capped at 960 px (**unfair**, my speed shortcut) | 48/95 (50%) | 53 (55%) |
| PaddleOCR PP-OCRv6, full detection size | **pending** (run was at ~38/96 photos when this was written) | |

Remaining misses (ML Kit text): OCR misreads (Kite Runner, Mountains Echoed), Urdu/Islamic-press covers, near-miss titles.
These are what the AI title-correction stage and the Phase 3 vision model are for.

**Replay without the emulator** (the point of the new harness): the ML Kit OCR text is in `docs/Books-Photo/ocr_mlkit.json`
and Open Library answers are cached in `docs/Books-Photo/ol_cache.json`, so tuning the scoring in `imports/Matching.kt` is a replay:
```powershell
$env:BENCH_OCR="D:\claude-exp\words-and-wounds\docs\Books-Photo\ocr_mlkit.json"      # or ocr_paddle.json
$env:BENCH_LABELS="D:\claude-exp\words-and-wounds\docs\Books-Photo\labels.csv"
$env:BENCH_OUT="D:\claude-exp\words-and-wounds\docs\Books-Photo\report.txt"
$env:BENCH_CACHE="D:\claude-exp\words-and-wounds\docs\Books-Photo\ol_cache.json"
.\gradlew.bat testDebugUnitTest --tests "*.MatchBenchmarkTest" --rerun-tasks   # prints strict/lenient, writes the report
```
**To finish the OCR decision:** when `docs/Books-Photo/ocr_paddle.json` exists (script `tools/ocr_paddle.py`, run with
`C:\Users\sarim\paddle-venv\Scripts\python.exe`, a Python 3.12 venv outside the repo; set `PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True`),
run the replay above with `BENCH_OCR` pointing at it and apply the spec's rule (adopt Paddle only if >= 8 points better than ML Kit
and an Android runtime fits the 6 GB phone). `ocr_paddle_det960.json` (the unfair run) is kept for reference.
Fresh ML Kit dump (if the OCR code changes): `adb shell am broadcast -n com.exc33ded.library/.debug.OcrDumpReceiver` with the app in the
foreground (it must not be backgrounded), wait for `files/ocr_mlkit.done`, then `adb exec-out run-as com.exc33ded.library cat files/ocr_mlkit.json`.
Paddle on Windows needs `enable_mkldnn=False` (already in the script); at full size it takes ~50 s/photo on CPU.

## Planned next (agreed with user 2026-11-22)

Four sub-projects, each its own spec → plan → build: (1) UI overhaul + Settings ✅; (2) matching fixes + OCR comparison
(offline PP-OCRv6 vs ML Kit on the 96 photos via Python PaddleOCR; PP-OCRv6 unified model has no Arabic/Urdu — would need the v5 Arabic
model; ML Kit stays default unless numbers justify a swap); (3) AI model comparison (Gemma vs others for the 6 GB phone);
(4) **model manager** in Settings ("Models & storage" row is a disabled placeholder now): user downloads/deletes models, and a storage
path setting. Constraint to design around: LiteRT-LM needs a real file path, so a SAF folder pick (content URI) won't work directly —
likely default to app-specific external files dir (`Android/data/<pkg>/files/models`, no permission needed, visible in file managers),
with free-space check and per-model delete.

(5) **Navigation motion + Profile** (user decisions 2026-11-22): tab switches get a *slide + fade* (direction follows the tab's position in the bar; timings from `MaterialTheme.motionScheme`, per the Phase 7 motion rule); Profile screen holds editable **name + photo** (gallery pick), a **yearly reading goal** with progress ring, a compact **stats summary** card, and a **bio / favourite quote** line, with Settings still reachable from it. Needs its own spec → plan; stored in DataStore/Room (not yet designed).

## Next steps, in order

1. **User:** confirm/correct `labels.csv` (flagged: #1 envelope "Not a book", Urdu covers #7/#8/#32,
   Rumi author, Jannah, #35 author, Russian Masters, Fearless #3; rows #89–96 were not re-verified).
2. **User:** add `googleBooksKey=…` to `local.properties` (Google Cloud → Books API → API key). Never commit it.
3. ~~Improve matching~~ done (see Benchmark). Still open: Google Books first when a key exists; `lang` params on the sources (deferred: they would also change manual search).
4. Finish the PaddleOCR comparison (see Benchmark), then record the decision in spec §14.
5. Hand-test on a **real phone**: ISBN scan (the + menu → Scan ISBN), camera photo import, Profile photo pick, the Phase 2b checklist.
6. Write the **Phase 3 plan** (LiteRT-LM 0.19.1, model download, summary + optional vision reader),
   using benchmark numbers to decide how much vision AI is needed (Urdu covers need it regardless).

## Gotchas

- Use the JDK 21 env vars from `CLAUDE.md`; system `java` is 8. If `GRADLE_USER_HOME` is preset to a scoop dir without a wrapper distribution, run `Remove-Item Env:GRADLE_USER_HOME` before `gradlew` (first build after that takes ~4 min).
- Tab switches clear the back stack (and ViewModels), so settings are read from `LocalAppSettings` (collected once in `MainActivity`) instead of per-screen collectors, to avoid a defaults flash.
- Android's first-use "Try out your stylus" tutorial can pop up when tapping a text field on the emulator; tap Cancel. `adb input text` needs `%s` for spaces.
- To see tab/screen animations in a test: `adb shell settings put global animator_duration_scale 10`, screen-record (`screenrecord`), extract frames with OpenCV; **restore the scale to 1**.
- DataStore cannot write twice to the same file in a JVM test on Windows (`File.renameTo` will not overwrite) — repository tests use an in-memory `DataStore<Preferences>` fake (see `ProfileRepositoryTest`).
- Git Bash rewrites device paths like `/data/...` in `adb` commands; use PowerShell or `MSYS_NO_PATHCONV=1`.
- Emulator quick-boot can show a black screen for ~20 s after launch; wait before concluding anything.
- `java.util.Properties` in `build.gradle.kts` must be imported (inside Gradle scripts `java.` resolves to the `java {}` extension).
- Hilt `@AndroidEntryPoint` on a BroadcastReceiver doesn't compile with the Gradle plugin here — use an `@EntryPoint` (see `app/src/debug/.../ImportDirReceiver.kt`).
- The emulator is slow right after boot ("System UI isn't responding" → tap Wait); scripted `adb input tap`s get lost — wait a few seconds between taps.
- Windows Python console is cp1252: scripts printing non-Latin text need `sys.stdout.reconfigure(encoding="utf-8")`.
- Known deprecations to clean in Phase 7: old `Slider` overload in `BookDetailScreen`; `StatusSelector`/FAB/DropdownMenu are placeholders for Expressive ButtonGroup / FAB menu.
