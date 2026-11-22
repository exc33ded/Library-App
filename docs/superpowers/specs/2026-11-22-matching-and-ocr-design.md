# Cover matching fixes + OCR comparison — design

Date: 2026-11-22. Sub-project 2 of 4. Baseline (spec §14): 32/96 strict (33%), ~41/96 same-work (43%), Open Library only.

## Goal
Raise cover-photo → book matching accuracy cheaply, and decide with numbers whether PP-OCRv6 (PaddleOCR) is worth replacing ML Kit OCR.

## Key idea: iterate offline, not on the emulator
Today every tuning run needs the emulator and the full 96-photo pipeline. Instead:
1. **Dump OCR once** per engine to JSON (`[{file, lines:[{text,height}]}]`), stored under `docs/Books-Photo/` (gitignored, private).
   - ML Kit: debug-only broadcast receiver (same pattern as `ImportDirReceiver`) runs `MlKitTextReader` over `files/bench/*.jpg` and writes `ocr_mlkit.json`.
   - PaddleOCR (PC): `tools/ocr_paddle.py` writes `ocr_paddle.json` in the same format (Python PaddleOCR package; height = bbox height).
2. **`MatchBenchmarkTest`** (JVM, skipped unless env `BENCH_OCR` points at a JSON file) loads the dump + `labels.csv`, runs the *real* `buildQueries` / ranking / `MetadataLookup` against live Open Library, and prints strict + same-work scores and the wrong/failed lists. Same code path as the app, no emulator.
3. Pure logic (query variants, candidate scoring, title normalisation) gets normal fast unit tests with fixture lines.

## Matching changes (in `imports/` and `metadata/`)
- **Query variants:** `buildQueries(lines): List<String>` ordered full → top line + author-ish line → each top line alone. `ImportProcessor` tries them in order until a candidate passes the score threshold.
- **Candidate ranking:** `scoreCandidate(ocrText, candidate)` = token overlap of OCR words with title + author words (fuzzy: edit distance ≤ 1 for words ≥ 5 letters, to absorb OCR misreads like "velloweace"). Pick the best candidate above a threshold instead of `firstOrNull()`.
- **English preference:** pass `lang=eng` to Open Library search; `langRestrict=en` to Google Books; among equal scores prefer English edition titles.
- **Benchmark scoring:** a result counts as same-work when title matches OR author matches and the Open Library work key matches (`tools/benchmark.py` and the JVM test share the rule: normalised-title ≥ 60% word overlap, or same author surname + any title word).

## OCR comparison (decision rule)
Run the same matching eval on `ocr_mlkit.json` and `ocr_paddle.json`. Adopt PaddleOCR on Android only if it improves same-work accuracy by ≥ 8 points over ML Kit **after** the matching fixes, and an Android runtime (NCNN/ONNX/ExecuTorch) fits the 6 GB phone budget. Otherwise ML Kit stays. Urdu/Arabic covers are expected to need the Phase 3 vision model either way (PP-OCRv6's unified model has no Arabic; the v5 Arabic model would be a separate download).

## AI title correction (user idea 2026-11-22) — measured here, shipped with sub-project 3
A small text model takes the raw OCR lines and returns a cleaned `title` / `author` ("velloweace" -> "Yellowface", drops taglines and publisher text), which then feeds the same search + ranking. Design points:
- It is an **optional stage after the cheap fixes**: pipeline = OCR -> query variants + fuzzy ranking -> (only if no candidate passes the threshold) AI correction -> search again. No model downloaded = the stage is skipped, nothing breaks.
- Runs on-device and in the background only (6 GB phone, spec §7); model comes from the model manager (sub-project 4), candidate models are compared in sub-project 3.
- Prompt output is constrained to `{"title":..., "author":...}` and never trusted blindly: the corrected string is only a search query, and the candidate must still pass `scoreCandidate` against the *original* OCR text, so a hallucinated title cannot win on its own.
- Measured with the same JVM benchmark by adding a `corrector` hook (identity by default); the offline gain is computed with a locally-run model before any Android work. Photos and their OCR text stay on this PC and are not sent to any online service.

## Out of scope
Shipping PaddleOCR on Android (only a follow-up if the rule above passes), the Phase 3 vision model, Google Books key plumbing beyond the `langRestrict` param.

## Inputs still needed from the user (not blocking)
Confirm `docs/Books-Photo/labels.csv` (drafted by subagents), and add `googleBooksKey` to `local.properties` for a Google-assisted run. Scores are reported against the draft labels until then.

## Testing
Unit tests for `buildQueries`, `scoreCandidate`, same-work rule; the benchmark test is the end-to-end measure. Success = report of before/after numbers in spec §14.
