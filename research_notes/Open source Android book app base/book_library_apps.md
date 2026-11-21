# Open-source Android book library / reading tracker apps (fork base and reference survey, as of 2026-11-21)

Method: GitHub REST API via `gh` (stars, license SPDX id, created date, last commit date on the default branch), the README of each repo, and dependency catalogs (`libs.versions.toml` / `app/build.gradle(.kts)` / `pubspec.yaml`), all pulled 2026-11-21. Feature claims come from READMEs unless marked otherwise. "Last commit" means the newest commit on the default branch (`/commits?per_page=1`). This can differ from GitHub's `pushed_at`. For example, toshokan-android shows pushed_at 2026-07-06 but its last default-branch commit is 2022-10-18.

Feature legend: ISBN = barcode scan; API = Open Library / Google Books lookup; Cover = cover photo capture/custom cover; Status = reading statuses; Prog = reading progress; Tags = categories/tags/shelves; Stats = statistics/goals; Imp = import/export (GR = Goodreads CSV); Sync = cloud sync/backup. "?" = not verified.

## Q1. Which apps are actively maintained Kotlin + Compose projects with a permissive or compatible license?

### Takeaway
Only a few active, permissively licensed Kotlin + Compose book trackers exist. **sriniketh/prose** (Apache-2.0, Hilt + Room, multi-module) has the cleanest architecture but narrow features. **tn3w/Shelf** (MIT) has the most features but is two months old, has 17 stars, and uses no Room or Hilt. Every mature, feature-complete tracker is copyleft (GPL), Flutter, or Java/XML.

### Cited Findings

Ranked shortlist (hard facts verified via GitHub API on 2026-11-21):

| # | Repo | License | Stars | Last commit | Stack | Architecture / key libs (from dep catalog) |
|---|---|---|---|---|---|---|
| 1 | [sriniketh/prose](https://github.com/sriniketh/prose) | Apache-2.0 | 1 | 2026-11-15 (~796 commits, created 2022-06) | Kotlin, Compose (0 layout XML) | Multi-module `core-*`/`feature-*`, UDF, Hilt, Room, Retrofit + kotlinx.serialization, Coil, ML Kit text recognition, Navigation Compose |
| 2 | [tn3w/Shelf](https://github.com/tn3w/Shelf) | MIT | 17 | 2026-11-20 (~287 commits, created 2026-09-14) | Kotlin, Compose, Material3 adaptive nav suite | No Room, no Hilt/DI; single `ShelfViewModel.kt`; DataStore, OkHttp, Coil3, ZXing, kotlinx.serialization, jsoup; custom offline catalogue pipeline |
| 3 | [SniperOfTDM/ReadTracker](https://github.com/SniperOfTDM/ReadTracker) | **No license file (API: null)** | 1 | 2026-11-07 (~37 commits, created 2026-11-07) | Kotlin, Compose | Room (schema v3), DataStore, WorkManager, Coil, Navigation Compose; offline/online build flavors |
| 4 | [Acclorite/book-story](https://github.com/Acclorite/book-story) (Book's Story) | GPL-3.0 | 1,428 | 2026-11-05 | Kotlin, Compose (619 .kt files, 0 layout XML) | Hilt, Room, DataStore, Coil, Navigation Compose. **An ebook reader, not a tracker** |
| 5 | [tfonteyn/NeverTooManyBooks](https://github.com/tfonteyn/NeverTooManyBooks) | GPL-3.0 | 177 | 2026-11-18 (~14,092 commits) | **Java + XML views** (1,301 .java, 0 .kt, 187 layout XML) | Fragments, ViewBinding, CameraX, ZXing |
| 6 | [mateusz-bak/openreads](https://github.com/mateusz-bak/openreads) | GPL-2.0 | 1,699 | 2026-11-16 (~2,641 commits) | **Flutter/Dart** | flutter_bloc + hydrated_bloc, sqflite, flutter_zxing, image_picker/cropper, fl_chart, csv |
| 7 | [Swappppy/Openshelf](https://github.com/Swappppy/Openshelf) | GPL-3.0 | 31 | 2026-11-20 (created 2026-04-24) | **Flutter/Dart** | Riverpod, Drift (SQLite), mobile_scanner, Tesseract OCR |
| 8 | [alessandrojean/toshokan-android](https://github.com/alessandrojean/toshokan-android) | MIT | 34 | **2022-10-18** (stale) | Kotlin, Compose | Hilt, SQLDelight, Ktor, ML Kit barcode, Coil, Vico charts, DataStore |
| 9 | [fenimore/badreads](https://github.com/fenimore/badreads) ("Bookmark") | GPL-3.0 | 49 | 2024-04-20 (stale) | Kotlin + XML views | Room, ViewBinding, Fragments, ZXing/barcode, Glide |
| 10 | [armutyus/ninova](https://github.com/armutyus/ninova) | MIT | 13 | 2024-06-25 (stale) | Kotlin + XML views | MVVM, Hilt, Room, Retrofit, Glide, **Firebase** |
| 11 | [OkeBenoitHub/A.Reader-Jetpack-Compose](https://github.com/OkeBenoitHub/A.Reader-Jetpack-Compose) | Apache-2.0 | 1 | 2026-09-24 (2 commits; Udemy course project) | Kotlin, Compose | MVVM, Hilt, Retrofit, Coil, **Firebase Auth + Firestore** |
| 12 | [lynnemunini/book-shelf](https://github.com/lynnemunini/book-shelf) | GPL-3.0 | 86 | 2023-09-30 (stale) | Kotlin | Firebase + Google Books (README) |
| 13 | [PatilParas05/ReadStack](https://github.com/PatilParas05/ReadStack) | MIT | 3 | 2026-03-12 | Kotlin, Compose, Room (README) | Clean Architecture, WorkManager; README says "built purely for learning purposes" |
| ref | [android/nowinandroid](https://github.com/android/nowinandroid) | Apache-2.0 | 22,112 | pushed 2026-11-21 | Kotlin, Compose | Hilt, Room, DataStore, WorkManager (catalog-verified) |
| ref | [mihonapp/mihon](https://github.com/mihonapp/mihon) | Apache-2.0 | 25,509 | pushed 2026-11-20 | Kotlin, Compose | SQLDelight, Injekt DI, WorkManager (catalog-verified) |

Feature coverage per app (from READMEs):

- **tn3w/Shelf**: ISBN yes ("Barcodes work too") plus on-device cover-recognition scan. API: Open Library, Wikidata, and many national libraries; Google Books not listed. Cover: pick your own picture, and it generates covers. Status: want-to-read/reading/finished (DNF not stated). Prog: daily page goal and streaks. Tags: shelves, genres, series. Stats: daily goal, streaks, monthly stats, "year in books" recap. Imp: Goodreads/StoryGraph CSV import, CSV export, Markdown/Readwise highlight export. Sync: none; daily backup to a chosen folder (README suggests Syncthing). It also bundles an EPUB/PDF/CBZ reader, audiobooks and TTS, so the scope is much bigger than a tracker. — [README](https://github.com/tn3w/Shelf)
- **sriniketh/prose**: API: Google Books (needs an API key). OCR of highlights via ML Kit. Saved "bookshelf" plus per-book highlights in Room. Statuses, stats, import/export and sync are not mentioned in the README. — [README](https://github.com/sriniketh/prose)
- **SniperOfTDM/ReadTracker**: Prog: page checkpoints, velocity, finish-date ETA, heatmap. Cover from the gallery. Stats: yearly reading target. Open Library title/author/ISBN search (online flavor). Imp: JSON backup only. Statuses/DNF not verified. — [README](https://github.com/SniperOfTDM/ReadTracker)
- **NeverTooManyBooks**: ISBN barcode (including batch scan, per CHANGELOG). API: Google Books, OpenLibrary and about 17 other sources. Tags: bookshelves. Imp: Goodreads CSV, Calibre CSV, Book Catalogue migration. Sync: Calibre Content Server. Requires Android 8+. Status/progress granularity not verified. — [README](https://github.com/tfonteyn/NeverTooManyBooks), [CHANGELOG](https://github.com/tfonteyn/NeverTooManyBooks/blob/main/CHANGELOG.md)
- **Openreads**: Status: finished, reading, want-to-read and DNF lists. Tags: custom tags with filtering. API: Open Library only, by design. ISBN barcode scan. Stats. Imp: CSV import from Goodreads and BookWyrm, plus backup/restore. Cloud sync: none found in pubspec dependencies. — [README](https://github.com/mateusz-bak/openreads), [F-Droid](https://f-droid.org/packages/software.mdev.bookstracker/)
- **Openshelf**: Status: five states (Want to read, Reading, Read, Abandoned, Paused). Prog: page-based with automatic dates. Tags: categories, imprints, collections/series, smart shelves. Stats: widget dashboard. Cover: photo, gallery, URL or API. API: Open Library plus optional Google Books, with a four-step lookup. ISBN: barcode plus Tesseract OCR. Goodreads import and sync not verified in README excerpt. — [README](https://github.com/Swappppy/Openshelf), [F-Droid](https://f-droid.org/packages/org.ftena.openshelf/)
- **toshokan-android**: ISBN barcode, Google Books/Open Library metadata, custom covers, "readings control", statistics, wishlist. Backup/restore is unchecked in its own roadmap. README says "very unstable". — [README](https://github.com/alessandrojean/toshokan-android)
- **badreads**: Shelves (reading/read/to-read) stored locally, barcode lookup on Open Library, import/export with Goodreads and Open Library. README says "unstable". — [README](https://github.com/fenimore/badreads)
- **ninova**: Google Books plus OpenLibrary search, notes/quotes, offline save, Firebase data sync. — [README](https://github.com/armutyus/ninova)
- **A.Reader**: Google Books search, started/read status, rating, notes, stats screen, Firestore storage. — [README](https://github.com/OkeBenoitHub/A.Reader-Jetpack-Compose)

Candidates named in the brief that were checked:
- **ReadYou** ([ReadYouApp/ReadYou](https://github.com/ReadYouApp/ReadYou), GPL-3.0, 7,685 stars) is an RSS reader ("An Android RSS reader presented in Material You style"), not a book app. Only useful as a Compose UI reference.
- **KOReader** ([koreader/koreader](https://github.com/koreader/koreader), AGPL-3.0, Lua) is an ebook reader. Not relevant as a base.
- **Inventaire** ([inventaire/inventaire](https://github.com/inventaire/inventaire)) is TypeScript web, and the GitHub repo is **archived** (last push 2025-05-16). Not an Android app.
- **Open Library official app**: none found. Only the Python [openlibrary-client](https://github.com/internetarchive/openlibrary-client) (AGPL-3.0) turned up.
- **Jelu** ([bayang/jelu](https://github.com/bayang/jelu), MIT, 755 stars, Kotlin) and **BookLogr** ([Mozzo1000/booklogr](https://github.com/Mozzo1000/booklogr), Apache-2.0, 641 stars) are self-hosted server/web trackers, not Android apps. They could serve as references for a data model or a self-hosted backend.

### Inferences
- The only repos that combine Kotlin + Compose, an active project and a permissive license are prose (Apache-2.0), Shelf (MIT), ReadStack (MIT, learning project) and A.Reader (Apache-2.0, 2-commit course project). ReadTracker fits technically, but with no license it is "all rights reserved", so it cannot legally be forked; it can only be read.
- **Copyleft implications:** for a purely personal app that is never distributed, GPL places essentially no obligations on the user. If the app is ever distributed (Play Store, sideloaded APK shared with others), the whole derivative must be released under GPL with source. Book's Story, NeverTooManyBooks, Openshelf and badreads are GPL-3.0. Openreads is GPL-2.0, which is technically incompatible with mixing in Apache-2.0 code unless the project is "or later" (not verified). This is not legal advice.

### Gaps
- Could not find repos named "Librarian", "BookOrganizer" or "Shelfie" as Android book apps in GitHub searches. They may live off GitHub or under other names (unverified).
- "Libretrack" did not appear in book-related searches. I believe it is a parcel-tracking app, but this is unverified and from memory.
- No Codeberg/GitLab search was performed, so trackers hosted only there may be missing.
- No Reddit thread was fetched; community sentiment is not covered.
- Exact Shelf reading-status set (DNF?) and prose's status/stats features are not verified beyond the READMEs.

## Q2. Which is the best fork base vs. which are better as code references for specific pieces?

### Takeaway
No candidate is both feature-complete and a clean, permissive Kotlin + Compose base. The pragmatic path is to fork or scaffold from **prose** (or Now in Android's architecture) and port features using **Shelf** (MIT) as the main code reference. Use GPL apps (Openreads, Openshelf, NeverTooManyBooks) only as feature/UX specifications unless GPL for the whole app is acceptable.

### Cited Findings
- prose: "multi-module app... `core-*` modules cover networking (Retrofit + kotlinx.serialization), persistence (Room)... Compose design system; each `feature-*` module owns one area... Hilt wiring everything together". — [prose README](https://github.com/sriniketh/prose)
- Shelf's source tree has files named `Csv.kt`, `Scan.kt`, `Recognizer.kt`, `Stats.kt`, `Library.kt`, `ScanScreen.kt`, `StatsScreen.kt`, `Wrapped.kt`, `Providers.kt` and 40+ tests, but no Room/Hilt dependencies in `android/gradle/libs.versions.toml`. — [Shelf repo tree](https://github.com/tn3w/Shelf)
- Shelf also bundles audiobook playback, TTS voice models with native C++ code, and an ebook reader. — [Shelf README](https://github.com/tn3w/Shelf)
- Openreads has 133 forks, created 2021-04, and offers four lists including DNF. — [Openreads](https://github.com/mateusz-bak/openreads)
- NeverTooManyBooks is a 2018 fork of Book Catalogue, "largely rewritten", in Java. — [NTMB README](https://github.com/tfonteyn/NeverTooManyBooks)

### Inferences
- **Best fork base (architecture):** sriniketh/prose. It is Apache-2.0, modern (Compose, Hilt, Room, multi-module, Google Books, ML Kit) and actively committed, but it is a one-person project with 1 star. Statuses, progress, stats, ISBN barcode, Goodreads import and sync would all need to be added. Alternative: start from Now in Android's structure (Apache-2.0) as a template.
- **Best feature reference (permissive, copy-able):** tn3w/Shelf (MIT) for Goodreads CSV import, barcode/cover scanning, stats/streaks/goals, folder backup and Open Library provider code. Its single-ViewModel, no-DI, custom-storage design makes it a poor fork base for a Room + Hilt app. It is also very young (about 2 months).
- **Reference for specific pieces:** ReadTracker for page-checkpoint progress, ETA and heatmap UX (read only, since it has no license). toshokan-android (MIT) for ML Kit barcode, Vico stats charts and Compose + Hilt + SQLDelight. Its code dates from 2022, so its dependencies are old. Book's Story (GPL) for polished Material You Compose UI, with Hilt + Room at scale. Mihon (Apache-2.0) for large-app Compose patterns, backup/restore and tracker-sync design.
- **Feature/UX specifications only (GPL, non-Kotlin):** Openreads (status lists, tags, CSV format doc at `doc/csv.md`), Openshelf (five statuses, smart shelves, four-step ISBN lookup) and NeverTooManyBooks (the broadest metadata sources and import formats).

### Gaps
- No candidate was built or run; code quality, test coverage and build health are not verified. Shelf and prose list tests and CI badges in their repos.

## Q3. Do any already use Supabase or Firebase sync?

### Takeaway
No Supabase-based open-source Android book tracker was found. Firebase is used only by small or stale projects (ninova, A.Reader, lynnemunini/book-shelf). The leading trackers (Openreads, Shelf, NeverTooManyBooks, ReadTracker, Openshelf) are local-first, with file backup or CSV/JSON export rather than cloud sync.

### Cited Findings
- ninova: "Data synchronization with firebase"; built with Firebase, Hilt, Room, MVVM; MIT; last commit 2024-06-25. — [ninova README](https://github.com/armutyus/ninova)
- A.Reader: Firebase Authentication plus Cloud Firestore; Apache-2.0; 2 commits. — [A.Reader README](https://github.com/OkeBenoitHub/A.Reader-Jetpack-Compose)
- lynnemunini/book-shelf uses Firebase to store user and book data; GPL-3.0; last commit 2023-09-30. — [book-shelf README](https://github.com/lynnemunini/book-shelf)
- Shelf: "daily backup to a folder you choose, for example a Syncthing folder"; no account. — [Shelf README](https://github.com/tn3w/Shelf)
- NeverTooManyBooks syncs with a Calibre Content Server (self-hosted), not a cloud BaaS. — [NTMB README](https://github.com/tfonteyn/NeverTooManyBooks)
- Openreads' pubspec dependencies contain no Firebase, Google Drive or Supabase packages (sqflite, file_picker, share_plus, archive for backups). — [Openreads pubspec](https://github.com/mateusz-bak/openreads/blob/master/pubspec.yaml)
- GitHub repo searches for "supabase book", "supabase reading" and "books supabase kotlin" returned no Android book trackers. — [GitHub search](https://github.com/search?q=supabase+book&type=repositories)

### Inferences
- Cloud sync (Supabase or Firebase) will almost certainly need to be built from scratch. Mihon's backup/restore and tracker-sync code and ninova's Firebase + Room pattern are the nearest references.

### Gaps
- GitHub code search (as opposed to repo search) for `io.github.jan-tennert.supabase` in book apps was not run, so a small Supabase-backed tracker could have been missed.
