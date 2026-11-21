# Book Library App — Design Spec

Date: 2026-11-21 · Status: draft for review · Working name: "Words & Wounds"
Research basis: `reports/Open source Android book app base.md`

## 1. Goal

A personal, single-user, multi-device Android book library (Goodreads-like) where books are added by
camera scan, cover photo, batch gallery import, or manual entry; enriched by on-device Gemma 4; kept
offline-first in Room and synced/backed up to Supabase; with a Material 3 Expressive UI where motion
quality is a first-class requirement.

### Success criteria
- Add a book by ISBN scan in < 3 s (online).
- Batch import of the user's existing 30–50 book photos completes in the background; >= 80% of
  photos produce a correct title+author with no manual edit (measured on that set) — proposed
  target, adjust if you like.
- Every change made offline appears on a second device within one sync cycle after reconnecting.
- Export → wipe → import restores the full library including covers.
- Scrolling the library grid and all signature transitions run without visible jank on the target phone.

### Non-goals (YAGNI)
Multi-user, social features, sharing, ebook/audiobook reading, iOS, Play Store distribution,
Goodreads CSV import (can be added later; Shelf's `Csv.kt` is the reference).

## 2. Approach

Fresh project with Now in Android's module/architecture pattern (Apache-2.0). Port algorithms from
tn3w/Shelf (MIT, with attribution), lift the Gemma download/engine code from Google AI Edge Gallery
(Apache-2.0), and the motion/camera-button patterns from Androidify (Apache-2.0). GPL projects
(Openreads, Openshelf, NeverTooManyBooks) are used as feature/UX references only — no code copied.

## 3. Stack (pinned 2026-11-21)

| Component | Version |
|---|---|
| Kotlin | 2.3+ (required by LiteRT-LM >= 0.17) — confirm build first |
| Compose BOM | 2026.11.01 · material3 1.5.0 (Expressive stable) |
| Navigation 3 | 1.2.2 |
| activity-compose (Photo Picker) | 1.13.0 |
| WorkManager | 2.12.0 |
| Hilt / Room | latest stable at project creation |
| CameraX (core, camera-compose, camera-mlkit-vision) | 1.7.0 |
| ML Kit barcode (bundled) / text-recognition | 17.3.0 / 16.0.1 |
| Google Code Scanner | 16.1.0 |
| LiteRT-LM | `com.google.ai.edge.litertlm:litertlm-android:0.19.1` |
| Models (all `.litertlm`, pinned HF commits, one runtime) | text: Qwen3-0.6B int4 (328 MiB, Apache-2.0) default; vision: LFM2.5-VL-1.6B int4 (1.30 GB, LFM Open License — verify terms) optional; Gemma 4 E2B (2.58 GB, Apache-2.0) opt-in "experimental" on < 8 GB, default on >= 8 GB; E4B on >= 12 GB |
| supabase-kt BOM | 3.9.0 (auth, postgrest, storage) — Ktor 3.x |
| Coil 3 + coil-network-okhttp | 3.6.3 |
| Vico (compose-m3) | 3.3.1 |
| RSS-Parser (prof18) | 6.2.0 |

minSdk 31, target/compileSdk latest.

## 4. Modules

```
app/            navigation (Nav3), MaterialExpressiveTheme(MotionScheme.expressive()), bottom bar
core/data       Room DB + DAOs + repositories (single source of truth)
core/sync       Supabase silent sign-in, LWW sync worker, cover upload, JSON+zip export/import
core/metadata   Open Library -> Google Books lookup chain, cover download
core/ai         model download/management, LiteRT-LM engine, extract + summarize
core/scan       Code Scanner, CameraX capture, ML Kit barcode + OCR on stills
feature/library cover grid, category tabs, status filter, search, swipe-to-change-status
feature/book    detail, edit, delete, progress logging, AI summary
feature/add     scan / photo / batch import queue + review cards / manual form
feature/feed    RSS tab + "New from your authors" tab
feature/profile goal ring, stats, streaks, badges, settings (model, sync, backup)
```

**Implementation note:** starts as a single Gradle `app` module with one package per box above
(`data`, `ui.library`, …); split into Gradle modules only if build times or boundaries demand it.

Each module exposes a small interface (e.g. `BookRepository`, `MetadataLookup`, `BookExtractor`,
`Summarizer`, `SyncEngine`) so it can be tested with fakes and swapped (e.g. AICore later).

## 5. Data model (Room; mirrored in Supabase Postgres)

All synced tables carry: `id` UUID (client-generated), `user_id` (server default `auth.uid()`),
`updated_at` (server-set by trigger; local copy for display), `deleted` bool, local-only `dirty` bool. Exceptions to avoid duplicates from offline edits on two
devices: `badges` uses `badge_id` as primary key; `book_categories` uses a deterministic UUID
derived from (book_id, category_id).

| Table | Fields |
|---|---|
| books | title, authors, isbn, description, ai_summary, ai_summary_source (DESCRIPTION/COVER/USER), cover_path, cover_remote_path, page_count, status (WANT/READING/READ/DNF), current_page, rating, started_at, finished_at, ol_work_key, notes |
| categories | name, color |
| book_categories | book_id, category_id |
| reading_log | book_id, date, pages_read |
| badges | badge_id, unlocked_at (rules live in code) |
| feeds | url, title, enabled |
| followed_authors | ol_author_key, name |
| feed_items (local only, cache) | feed_id/author_key, title, link, published_at, read |
| import_queue (local only) | uri, state (PENDING/PROCESSING/REVIEW/DONE/FAILED), result_json, error |

## 6. Sync & backup

- **Auth:** no login UI. Supabase URL, anon key, and a single user's email/password live in
  `local.properties` -> `BuildConfig` (never committed). App signs in silently on first run; RLS
  restricts every table and the storage bucket to `auth.uid()`. The service_role key is never in the app.
  Caveat: the credentials are baked into the APK, so anyone holding the APK has full access to this
  account — acceptable only because the APK is personal and never shared. **Disable public sign-ups**
  in Supabase Auth settings so the extractable anon key can't be used to create accounts.
- **Sync worker** (WorkManager; on app start, debounced after writes, periodic 6 h, network-constrained):
  1. push dirty rows as upserts -> clear dirty;
  2. pull rows `updated_at > lastPulledAt` -> merge, last-write-wins per row;
  3. upload/download covers. Covers resized on-device to ~600 px WebP, stored at private bucket
     path `{uid}/{bookId}.webp` with owner-only SELECT/INSERT/UPDATE policies.
- **Paused/unreachable backend** = offline; app fully usable; sync status shown in Profile.
- **Backup:** Profile -> Backup -> Export writes a `.zip` (JSON of all tables + cover files) via the
  Storage Access Framework; Import merges by `id`. Required because Supabase Free has no backups.

## 7. Adding books & AI

**Entry points** (centre camera button: tap = scan, long-press = FAB menu):
Scan ISBN · Take cover photo · Import from gallery · Manual entry · (Search by title in manual form).

**Pipeline per image:**
1. ML Kit barcode on the image; accept only checksum-valid EAN-13 starting 978/979.
2. ISBN -> Open Library (User-Agent with app name; <= 1–3 req/s) -> Google Books (API key in
   `local.properties`) fallback. Covers by `cover_i` id (not ISBN) to avoid the 100/5 min limit.
3. No ISBN -> ML Kit OCR text + Gemma vision (image first, then prompt; visual token budget 560–1120;
   `ResponseFormat.json` schema `{title, authors[]}`; capped max output tokens) -> title search lookup.
4. Summary: Gemma (text only, thinking off) rewrites the retrieved description into one short
   paragraph (`ai_summary_source = DESCRIPTION`). If none found: paragraph from cover + OCR text,
   labelled "from cover" (`COVER`). User can edit (`USER`).
5. Categories suggested from Open Library subjects mapped to the user's categories; editable.
6. Result -> review card -> user confirms -> saved to Room (dirty) -> sync.

**Batch import:** Photo Picker multi-select (limit from `MediaStore.getPickImagesMaxLimit()`),
persist URI permissions, enqueue in `import_queue`; one sequential `CoroutineWorker` with foreground
progress notification; user swipes through review cards when ready.

**Model management (Settings):** a model list (JSON allowlist, Gallery pattern) with two roles —
*summary* (text) and *cover reader* (vision, optional). Wi-Fi-only resumable download, delete model,
switch models freely to experiment. RAM-based defaults for the 6 GB target phone:
- summary: Qwen3-0.6B int4 (~2 GB peak) — default;
- cover reader: none by default (ML Kit OCR + lookup); LFM2.5-VL-1.6B int4 offered;
  Gemma 4 E2B offered as "experimental — may be killed for memory" (no verified 6 GB Android report);
- >= 8 GB devices: Gemma 4 E2B for both roles; >= 12 GB: E4B offered.
Vision only runs as background enrichment on 6 GB (never blocks the UI); context kept small
(≈1–2k tokens) to fit GPU memory; on OOM the worker falls back to OCR and marks the model unsuitable.
Phase 3 benchmark compares OCR-only vs LFM2.5-VL vs Gemma 4 E2B on the user's photo set
(accuracy, seconds/photo, crashes) and the winner becomes the default.
Log the actually-loaded backend (GPU may silently fall back to CPU). Engine initialised lazily and
kept alive while a queue is processing.

**Errors:** lookup failure -> card shows extracted fields + manual search; Gemma failure/OOM -> fall
back to OCR result; unreadable image -> FAILED with retry; no network -> queued lookups retry later.

## 8. Feed

- **RSS tab:** RSS-Parser; preloaded feeds (Book Riot new books, Kirkus, NYT Books, Lit Hub, Reactor,
  Publishers Weekly, Porchlight new releases), add/remove URLs; items cached in Room; pull-to-refresh.
- **New from your authors:** daily worker polls Open Library `search.json?author_key=…&sort=new`
  for followed authors (follow from book detail), filters by language, dedupes by work key, shows new works.

## 9. Profile

- Yearly goal (books) with animated wavy circular progress ring.
- Stats: books read, pages, top categories, books-per-month column chart (Vico), year-in-books recap.
- Streaks: consecutive days with a `reading_log` entry.
- Badges: rules in code (first book, 10/25/50 books, 5 categories, 1,000/10,000 pages, 7/30-day
  streak, first DNF "permission to quit"), evaluated after each status/progress change; unlock
  animation + haptic.
- Settings: AI model, sync status/manual sync, export/import, feeds.

## 10. UI & motion (first-class requirement)

- `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`, dynamic color; every animation
  takes its spec from `MaterialTheme.motionScheme` (spatial for bounds, effects for color/alpha).
- **Bottom bar:** Library · Feed · [camera] · Stats · Profile. Camera button = `MaterialShapes`
  cookie shape with spring press-scale (Androidify pattern); long-press opens FAB menu.
- **Library:** adaptive cover grid, category tabs, status `ButtonGroup` filter, search bar,
  `animateItem()` placement animations, items keyed by id.
- **Shared element:** cover grid -> detail via `SharedTransitionLayout` around `NavDisplay`,
  `sharedElement("cover-$id")`; predictive back scrubs it back into the grid.
- **Pull-to-refresh with tension** (Library: pull = manual sync; Feed: pull = refetch feeds):
  `PullToRefreshBox` + custom indicator using `distanceFraction`
  with a diminishing-returns curve, expressive `LoadingIndicator`, `GestureThresholdActivate`
  haptic at threshold; system stretch overscroll elsewhere.
- **Swipe-to-change-status** on cards: `anchoredDraggable`, `SegmentTick` haptic per anchor,
  `Confirm` on commit.
- **Predictive back** everywhere (target Android 15+, no root `BackHandler`).
- Progress uses wavy linear indicator; scan screen has animated reticle overlay.
- Performance: read animated values only in deferred lambdas (`graphicsLayer {}`, `offset {}`),
  baseline profile generated in phase 7.

## 11. Phases (each ends with a runnable app)

1. Skeleton + library CRUD, statuses, categories, manual entry, theme/motion scheme.
2. Scan + metadata lookup + cover photo + batch import queue (no AI: OCR + lookup).
3. Gemma: model download/settings, extraction, summaries; accuracy run on user's photo set.
4. Supabase sync + covers + export/import.
5. Profile: goal, stats, streaks, badges.
6. Feed: RSS + author follow.
7. Motion polish: shared elements, pull tension, swipe status, haptics, baseline profile.

**De-risk first (start of phase 2/3/4):** one cover through LiteRT-LM at 1120 visual tokens with a
JSON schema on the target phone (measure latency); 40 photos through the queue; one push/pull cycle
against a free Supabase project.

## 12. Testing

- Unit (JVM): ISBN checksum/validation, metadata response mapping, LWW merge logic, badge rules,
  streak computation, export/import round-trip, subject->category mapping.
- Room DAO tests (in-memory DB); sync worker against fake `SyncEngine`.
- Instrumented: one end-to-end add-book flow with fake extractor; Compose UI tests for library filter
  and status change.
- Manual accuracy benchmark: the user's 30–50 photos -> % correct title+author, avg seconds/photo,
  per model (E2B vs E4B).

## 13. Project facts & open items

- Code lives in the **public** GitHub repo `exc33ded/Library-App`. `.gitignore` must exclude
  `local.properties`, keystores and any `*.env` from the first commit; MIT attribution for Shelf-derived
  code kept in `NOTICE`/file headers.
- Target phone has **6 GB RAM** — below Gallery's 8 GB E2B minimum; §7 model defaults set from
  `research_notes/.../models_6gb.md`. Phone: **Samsung Galaxy A07-class** (budget/mid; believed
  Helio G99 + Mali-G57 — unverified). Assume CPU backend; GPU attempted and the loaded backend logged.
  On first model load the app runs a one-off timing probe (one short prompt) and stores
  tokens/s; if vision would exceed ~60 s/photo it stays background-only (charging + idle constraint).
- Photo test set: `docs/Books-Photo/` — 96 JPEGs, 4096×3072, ~312 MB. **Never committed**
  (gitignored; private + large). Observed from a sample: front covers only (no barcodes), stored
  sideways (honour EXIF, then retry OCR at 90°/270°), busy patterned backgrounds, display fonts,
  author sometimes larger than title, Indian editions. Consequences: OCR + title/author search is the
  primary path for imports (not a fallback); Google Books search by `intitle:+inauthor:` is required
  alongside Open Library; downscale to ~1600 px long edge before OCR/VLM. Ground truth for the
  benchmark lives in `docs/Books-Photo/labels.csv` (file,title,author), drafted then confirmed by the user.
- Final app name: open.
