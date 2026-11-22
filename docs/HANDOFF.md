# Handoff — 2026-11-22

Personal Android book library app (Kotlin + Compose). Read `CLAUDE.md` first (toolchain env vars,
commands, architecture, project rules), then the spec
`docs/superpowers/specs/2026-11-21-book-library-app-design.md`.

Repo: `exc33ded/Library-App` (public) — `main` at `00189e4`, working tree clean, all pushed.

## Where things stand

| Phase | Plan | Status |
|---|---|---|
| 1 Library core (CRUD, statuses, categories, theme, Nav3) | `plans/2026-11-21-phase-1-library-core.md` | ✅ done, checked on emulator |
| 2a Lookup + ISBN scan + covers | `plans/2026-11-22-phase-2a-lookup-and-scan.md` | ✅ done; search→prefill→save checked on emulator. **ISBN scan untested** (needs real phone) |
| 2b Cover photo + batch import | `plans/2026-11-22-phase-2b-photo-import.md` | ✅ built; benchmark run. **Task 8 checklist items 1–5 not hand-tested** (camera, Add/Edit/Wrong-book buttons, resume after kill) |
| 3 On-device AI | not written | next after matching fixes |
| 4 Backup (export/import zip) | not written | Supabase **dropped** 2026-11-22 — phone-only storage |
| 5 Profile · 6 Feed · 7 Motion polish | not written | — |

55 JVM unit tests pass (`.\gradlew.bat testDebugUnitTest`).

## Decisions made this session (don't re-litigate)

- **Storage is phone-only.** No Supabase, no accounts. Schema keeps sync-ready columns
  (`updatedAt`, `deleted`, `dirty`, UUIDs) in case cloud is wanted later. Backup = Phase 4 zip export/import.
- **Coding is delegated to Claude subagents on Sonnet** (Agent tool, `model: "sonnet"`), given full task
  text; main session reviews, re-runs tests, commits. (opencode was used earlier — free models only if ever again.)
- Target phone: **Samsung Galaxy A07-class, 6 GB RAM** → AI optional and background-only; default
  cover reading = ML Kit OCR + online lookup. Model plan in spec §3/§7 (Qwen3-0.6B summaries,
  LFM2.5-VL-1.6B / Gemma 4 E2B optional experiments).
- Single Gradle module; packages per area.
- UI-skill suggestion (`android-design-ui-architecture`) reviewed and **not installed** — it matches what we already follow.

## Benchmark (spec §14)

96 user photos in `docs/Books-Photo/` (gitignored), labels in `docs/Books-Photo/labels.csv`
(drafted by subagents — **user has not confirmed yet**).

First run (OCR + Open Library, no Google Books key): **32/96 strict (33%), ~41/96 same-work (~43%)**;
23 wrong, 41 failed. Causes: OCR misreads on display fonts break exact-token search; Urdu/Arabic-script
covers unreadable by Latin OCR; taglines/second titles leak into the query; original-language titles
returned (La Peste, Белые ночи); Islamic-press covers with publisher-only text.

How to re-run (emulator `library_pixel` already has the photos in `/sdcard/Pictures/books` and
`/data/local/tmp/books`):
```powershell
emulator -avd library_pixel            # from $env:ANDROID_HOME\emulator
.\gradlew.bat installDebug
adb shell 'run-as com.exc33ded.library sh -c "rm -rf files/imports; mkdir -p files/bench && cp /data/local/tmp/books/*.jpg files/bench/"'
# clear old queue: uninstall/reinstall, or clear app data: adb shell pm clear com.exc33ded.library (then re-copy bench)
adb shell am broadcast -n com.exc33ded.library/.debug.ImportDirReceiver   # debug-only hook
# wait until files/imports has 96 files, then:
adb shell am force-stop com.exc33ded.library
adb exec-out run-as com.exc33ded.library cat databases/library.db > library.db   # also -wal and -shm
python -I tools/benchmark.py library.db docs/Books-Photo/labels.csv <empty-or-media_ids.txt>
```

## Next steps, in order

1. **User:** confirm/correct `labels.csv` (flagged: #1 envelope "Not a book", Urdu covers #7/#8/#32,
   Rumi author, Jannah, #35 author, Russian Masters, Fearless #3; rows #89–96 were not re-verified).
2. **User:** add `googleBooksKey=…` to `local.properties` (Google Cloud → Books API → API key). Never commit it.
3. **Improve matching** (cheap fixes before Phase 3), then re-run the benchmark:
   - prefer English titles (Open Library edition/`lang=en`, or Google Books first when a key exists);
   - query variants per photo: full → top line + author line → each top line alone → fuzzy tokens;
   - pick the candidate whose title/author best overlaps the OCR text instead of blindly taking the first;
   - treat "same work, other language" as a match in `tools/benchmark.py` (compare via `olWorkKey` or author).
4. Hand-test Phase 2b checklist on the emulator, and ISBN scan on the real phone.
5. Write the **Phase 3 plan** (LiteRT-LM 0.19.1, model download, summary + optional vision reader),
   using benchmark numbers to decide how much vision AI is needed (Urdu covers need it regardless).

## Gotchas

- Use the JDK 21 env vars from `CLAUDE.md`; system `java` is 8.
- `java.util.Properties` in `build.gradle.kts` must be imported (inside Gradle scripts `java.` resolves to the `java {}` extension).
- Hilt `@AndroidEntryPoint` on a BroadcastReceiver doesn't compile with the Gradle plugin here — use an `@EntryPoint` (see `app/src/debug/.../ImportDirReceiver.kt`).
- The emulator is slow right after boot ("System UI isn't responding" → tap Wait); scripted `adb input tap`s get lost — wait a few seconds between taps.
- Windows Python console is cp1252: scripts printing non-Latin text need `sys.stdout.reconfigure(encoding="utf-8")`.
- Known deprecations to clean in Phase 7: old `Slider` overload in `BookDetailScreen`; `StatusSelector`/FAB/DropdownMenu are placeholders for Expressive ButtonGroup / FAB menu.
