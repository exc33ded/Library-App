# Model manager + storage settings — design

Date: 2026-11-22. Sub-project 4 (with the model catalog from sub-project 3's research).

## Goal
Settings → "Models & storage" becomes real: the user can see where data lives, choose where large downloads go, download optional AI models (free, no account), and delete them. Plus a one-tap ISBN scan button on the Library toolbar.

## 1. Model catalog (research 2026-11-22, Hugging Face API)
All files are `.litertlm` for LiteRT-LM, **Apache-2.0, not gated** (no login/token). Sizes/SHA-256 come from the HF API (`lfs.sha256`); download URL = `https://huggingface.co/<repo>/resolve/main/<file>`.

| id | Name | Repo / file | Size | Use |
|---|---|---|---|---|
| `qwen3-0.6b` | Qwen3 0.6B (recommended) | `litert-community/Qwen3-0.6B` / `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` | 343.5 MB | fix misread titles, short summaries |
| `qwen3-1.7b` | Qwen3 1.7B | `litert-community/Qwen3-1.7B` / `Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm` | 977 MB | better quality, needs more RAM |
| `qwen3.5-0.8b-vl` | Qwen3.5 0.8B Vision | `litert-community/Qwen3.5-0.8B` / `Qwen3.5-0.8B-VL_int8.litertlm` | 1.30 GB | read covers (incl. non-Latin) |
| `gemma4-e2b` | Gemma 4 E2B | `litert-community/gemma-4-E2B-it-litert-lm` / `gemma-4-E2B-it.litertlm` | 2.59 GB | best vision, heavy for a 6 GB phone |

Excluded: Gemma 3n E2B (manual-approval gate), vendor-specific NPU builds. Whether each file runs on the bundled LiteRT-LM runtime is validated in Phase 3; this sub-project only manages files. Also noted for the OCR decision: `litert-community/PP-OCRv6-Small-LiteRT` exists (det 12.4 MB + rec 21.5 MB `.tflite`), so an on-device PP-OCRv6 is realistic.

## 2. Storage (`storage/`)
- **Models folder** has three possible roots: *Phone storage* (`filesDir/models`, default), *App folder on shared storage* (`getExternalFilesDirs(null)[0]/models`, visible in file managers, no permission), *SD card* (`getExternalFilesDirs(null)[1]/models`, only offered when present). The choice is a setting (`modelsLocation`); changing it **moves** installed model files (copy, verify size, delete) and is blocked while a download runs.
- **Database, covers, photos stay in private app storage on purpose** (a database on removable or shared storage can corrupt or vanish when the card is removed; there is no safe in-app way to move a live Room DB). The screen shows their sizes read-only with this explanation, and points to the Phase 4 backup export for getting data out. (User asked for a path option for the database too; this is the deliberate, documented exception.)
- Screen shows: current models path, free space on that volume, space used by models; sizes of database, covers, import photos.

## 3. Downloader (`models/`)
- `ModelCatalog` (the table above as Kotlin constants), `ModelRepository` (state per model: NOT_DOWNLOADED / DOWNLOADING(progress) / READY; derived from files on disk + WorkManager state), `ModelDownloadWorker` (`CoroutineWorker`, unique work `model-download-<id>`).
- Download streams to `<file>.part` with HTTP **Range resume**, reports progress, checks free space first (size + 5% margin), verifies **SHA-256** at the end, then atomically renames. A mismatch deletes the file and reports an error. Cancel keeps the `.part` for resume; Delete removes both.
- **Wi-Fi only** switch (default on) = WorkManager `NetworkType.UNMETERED`; off = `CONNECTED`. Foreground notification while downloading (progress, Cancel).
- UI: one card per model (name, size, purpose, status, Download / Cancel / Delete). Honest note on the screen: "On-device AI features that use these models arrive in a later update."

## 4. Scan shortcut
Library toolbar gets a barcode icon next to the display-options button, calling the existing ISBN scan flow (`AppNav.scanIsbn`) via a new `onScanIsbn` parameter on `LibraryScreen`. Unchanged: the **+** menu entry, the Play-services scanner, the "Scanner unavailable" message.

## 5. Out of scope
Running models (Phase 3), moving the database, backup/export (Phase 4), resumable-download UI beyond Cancel/Download, model update checks.

## 6. Testing
Pure/JVM: catalog sanity (unique ids, https URLs, 64-hex SHA-256, positive sizes), root resolution + free-space check, `.part`/resume offset logic, SHA-256 verifier on known bytes, state derivation. Downloader against a Ktor `MockEngine`/local server with Range support. Manual on emulator: real download of Qwen3 0.6B over Wi-Fi, cancel + resume, delete, location switch, free-space numbers, and the scan button.
