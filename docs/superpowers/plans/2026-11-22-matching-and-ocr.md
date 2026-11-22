# Cover Matching Fixes + OCR Comparison Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **Project rules (CLAUDE.md):** Tasks 1–4 go to a Claude subagent on **Sonnet** with the full task text; the subagent may build and run tests but **does not commit**; the main session reviews the diff, re-runs verification and commits (no `Co-Authored-By`/Claude lines in commit messages). Never pass `local.properties` to a subagent. Task 5 is main-session only (emulator, PC tooling, private photos).

**Goal:** Raise cover-photo → book matching accuracy (baseline 32/96 strict, 33%) with fuzzy query variants + candidate ranking, and measure PaddleOCR vs ML Kit offline.

**Architecture:** Pure matching logic (`Matching.kt`) → `CoverMatcher` orchestrates query variants against `MetadataLookup` and picks the best-scoring candidate → `ImportProcessor` uses it. A debug receiver dumps ML Kit OCR lines to JSON once; a JVM benchmark test replays any OCR dump through the *real* `CoverMatcher` against live Open Library, so tuning needs no emulator.

**Tech Stack:** Kotlin, kotlinx.serialization, Ktor (OkHttp engine on the JVM test classpath), JUnit4; Python (PaddleOCR) for the PC-side OCR dump.

Spec: `docs/superpowers/specs/2026-11-22-matching-and-ocr-design.md` (deviation recorded here: English preference comes from ranking first — `lang`/`langRestrict` source params are deferred because they would also affect manual search; same-work is reported as strict + author-lenient).

## Toolchain (each shell)

```powershell
$env:JAVA_HOME="$env:USERPROFILE\scoop\apps\temurin21-jdk\current"
$env:ANDROID_HOME="$env:USERPROFILE\scoop\apps\android-clt\current"
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue   # preset value has no Gradle distribution
```

Paths: main = `app/src/main/java/com/exc33ded/library/`, test = `app/src/test/java/com/exc33ded/library/`, debug = `app/src/debug/java/com/exc33ded/library/`.

---

### Task 1: Pure matching logic

**Files:**
- Create: `imports/Matching.kt` (main)
- Test: `imports/MatchingTest.kt` (test)

- [ ] **Step 1: Write the failing test** — `imports/MatchingTest.kt`

```kotlin
package com.exc33ded.library.imports

import com.exc33ded.library.metadata.BookCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchingTest {
    private val yellowLines = listOf(OcrLine("YELLOWFACE", 120), OcrLine("REBECCA F. KUANG", 40))

    @Test fun editDistanceBasics() {
        assertEquals(0, editDistance("plague", "plague"))
        assertEquals(1, editDistance("plague", "plaque"))
        assertEquals(2, editDistance("velloweace", "yellowface"))
    }

    @Test fun fuzzyHasToleratesOcrMisreadsOnlyOnLongWords() {
        val ocr = setOf("velloweace", "kuang", "cat")
        assertTrue(fuzzyHas("yellowface", ocr))   // 2 edits, word >= 8 letters
        assertTrue(fuzzyHas("kuang", ocr))        // exact
        assertFalse(fuzzyHas("cut", ocr))         // short words must match exactly
    }

    @Test fun fuzzyHasOneEditForMidLengthWords() {
        assertTrue(fuzzyHas("plague", setOf("plaque")))   // 6 letters -> 1 edit allowed
        assertFalse(fuzzyHas("plague", setOf("plaquez9"))) // too far
    }

    @Test fun exactTitleAndAuthorScoresOne() =
        assertEquals(1.0, scoreCandidate(yellowLines, BookCandidate("Yellowface", "R. F. Kuang")), 1e-9)

    @Test fun titleOnlyScoresPointSeven() =
        assertEquals(0.7, scoreCandidate(listOf(OcrLine("YELLOWFACE", 120)), BookCandidate("Yellowface", "R. F. Kuang")), 1e-9)

    @Test fun stopWordsDoNotCount() =
        assertEquals(1.0, scoreCandidate(listOf(OcrLine("PLAGUE", 100), OcrLine("CAMUS", 40)), BookCandidate("The Plague", "Albert Camus")), 1e-9)

    @Test fun misreadCoverStillScoresAboveThreshold() {
        val lines = listOf(OcrLine("VELLOWEACE", 120), OcrLine("REBECCA F. KUANG", 40))
        assertTrue(scoreCandidate(lines, BookCandidate("Yellowface", "R. F. Kuang")) >= MATCH_THRESHOLD)
    }

    @Test fun wrongBookScoresBelowThreshold() =
        assertTrue(scoreCandidate(yellowLines, BookCandidate("La Peste", "Albert Camus")) < MATCH_THRESHOLD)

    @Test fun queriesAreFullThenTopTwoThenSinglesWithoutDuplicates() = assertEquals(
        listOf(
            "yellowface rebecca f kuang some publisher",
            "yellowface rebecca f kuang",
            "yellowface",
            "rebecca f kuang",
            "some publisher",
        ),
        buildQueries(yellowLines + OcrLine("Some Publisher", 10)),
    )

    @Test fun noReadableTextGivesNoQueries() =
        assertEquals(emptyList<String>(), buildQueries(listOf(OcrLine("#1 NEW YORK TIMES BESTSELLING AUTHOR", 30), OcrLine("@ 7", 50))))
}
```

- [ ] **Step 2: Run, expect FAIL (unresolved references)** — `.\gradlew.bat testDebugUnitTest --tests "*.MatchingTest"`

- [ ] **Step 3: Implement** — `imports/Matching.kt`

```kotlin
package com.exc33ded.library.imports

import com.exc33ded.library.metadata.BookCandidate
import kotlin.math.abs

/** A candidate scoring at least this (0..1) against the OCR text is accepted without trying more queries. */
const val MATCH_THRESHOLD = 0.6

private val STOP_WORDS = setOf("the", "a", "an", "of", "and", "in", "to", "on", "for", "la", "le", "el", "der", "die", "das")

private fun tokens(s: String): List<String> =
    s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(Regex("\\s+")).filter { it.length >= 2 }

fun editDistance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1).also { it[0] = i }
        for (j in 1..b.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        prev = cur
    }
    return prev[b.length]
}

/** Exact hit, or within a small edit distance (OCR misreads like "velloweace" for "yellowface"); short words must match exactly. */
fun fuzzyHas(word: String, ocr: Set<String>): Boolean {
    if (word in ocr) return true
    val max = when { word.length >= 8 -> 2; word.length >= 5 -> 1; else -> 0 }
    return max > 0 && ocr.any { abs(it.length - word.length) <= max && editDistance(it, word) <= max }
}

/** 0.7 * share of title words found in the OCR text + 0.3 * share of author surnames found. */
fun scoreCandidate(lines: List<OcrLine>, c: BookCandidate): Double {
    val ocr = lines.flatMap { tokens(it.text) }.toSet()
    val allTitle = tokens(c.title)
    val title = allTitle.filter { it !in STOP_WORDS }.ifEmpty { allTitle }
    val surnames = c.authors.split(',').flatMap { tokens(it.trim().substringAfterLast(' ')) }
    fun share(words: List<String>) = if (words.isEmpty()) 0.0 else words.count { fuzzyHas(it, ocr) }.toDouble() / words.size
    return 0.7 * share(title) + 0.3 * share(surnames)
}

/** Search queries to try in order: everything, the two biggest lines, then each of the three biggest lines alone. */
fun buildQueries(lines: List<OcrLine>): List<String> {
    val full = buildQuery(lines)
    if (full.isBlank()) return emptyList()
    val byHeight = lines.sortedByDescending { it.height }
    return (listOf(full, buildQuery(byHeight.take(2))) + byHeight.take(3).map { buildQuery(listOf(it)) })
        .filter { it.isNotBlank() }
        .distinct()
}
```

- [ ] **Step 4: Run, expect PASS** — same command. Also run `.\gradlew.bat testDebugUnitTest` (all). Report diff; main session commits `feat(matching): fuzzy scoring and query variants`.

---

### Task 2: CoverMatcher + wire into ImportProcessor; make OcrLine serializable

**Files:**
- Create: `imports/CoverMatcher.kt`, `imports/OcrDump.kt` (main)
- Modify: `imports/QueryBuilder.kt` (add `@Serializable` to `OcrLine`), `imports/ImportProcessor.kt`
- Test: `imports/CoverMatcherTest.kt` (test); existing `ImportProcessorTest` must still pass unchanged.

- [ ] **Step 1: Failing test** — `imports/CoverMatcherTest.kt` (reuses the fake-source pattern from `ImportProcessorTest`, declared privately here)

```kotlin
package com.exc33ded.library.imports

import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.BookSource
import com.exc33ded.library.metadata.MetadataLookup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class MapSource(val results: Map<String, List<BookCandidate>>) : BookSource {
    val queries = mutableListOf<String>()
    override suspend fun byIsbn(isbn: String): BookCandidate? = null
    override suspend fun search(query: String, limit: Int): List<BookCandidate> { queries += query; return results[query].orEmpty() }
}

class CoverMatcherTest {
    private val lines = listOf(OcrLine("YELLOWFACE", 120), OcrLine("REBECCA F. KUANG", 40), OcrLine("Some Publisher", 10))
    private val yf = BookCandidate("Yellowface", "R. F. Kuang")
    private val peste = BookCandidate("La Peste", "Albert Camus")

    private fun matcher(ol: MapSource) = CoverMatcher(MetadataLookup(ol, MapSource(emptyMap())))

    @Test fun picksBestScoringCandidateNotTheFirst() = runTest {
        val m = matcher(MapSource(mapOf("yellowface rebecca f kuang some publisher" to listOf(peste, yf)))).match(lines)
        assertEquals("Yellowface", m!!.candidate.title)
    }

    @Test fun keepsTryingVariantsUntilOneIsConfident() = runTest {
        val ol = MapSource(mapOf(
            "yellowface rebecca f kuang some publisher" to listOf(peste),   // wrong, low score
            "yellowface" to listOf(yf),
        ))
        val m = matcher(ol).match(lines)
        assertEquals("Yellowface", m!!.candidate.title)
        assertEquals("yellowface", m.query)
    }

    @Test fun fallsBackToFirstResultOfFirstNonEmptyQueryWhenNothingIsConfident() = runTest {
        val m = matcher(MapSource(mapOf("yellowface rebecca f kuang some publisher" to listOf(peste)))).match(lines)
        assertEquals("La Peste", m!!.candidate.title)
    }

    @Test fun nothingFoundIsNull() = runTest {
        assertNull(matcher(MapSource(emptyMap())).match(lines))
    }

    @Test fun noQueriesMeansNoSearchAtAll() = runTest {
        val ol = MapSource(emptyMap())
        assertNull(matcher(ol).match(listOf(OcrLine("@ 7", 50))))
        assertEquals(emptyList<String>(), ol.queries)
        assertNotNull(OcrDump("a.jpg", emptyList()))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat testDebugUnitTest --tests "*.CoverMatcherTest"`

- [ ] **Step 3: Implement**

`imports/OcrDump.kt`

```kotlin
package com.exc33ded.library.imports

import kotlinx.serialization.Serializable

/** One photo's OCR result; the on-disk format shared by the ML Kit debug dump, the PaddleOCR script and the benchmark. */
@Serializable
data class OcrDump(val file: String, val lines: List<OcrLine>)
```

In `imports/QueryBuilder.kt` change `data class OcrLine(...)` to be annotated:

```kotlin
@kotlinx.serialization.Serializable
data class OcrLine(val text: String, val height: Int)
```

`imports/CoverMatcher.kt`

```kotlin
package com.exc33ded.library.imports

import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.MetadataLookup

data class CoverMatch(val candidate: BookCandidate, val query: String, val score: Double)

/**
 * Tries each query variant, ranks the results against the OCR text and returns the first confident one.
 * If none is confident, falls back to the first result of the first query that returned anything
 * (the review screen lets the user correct it). Lookup errors propagate to the caller.
 */
class CoverMatcher(private val lookup: MetadataLookup) {
    suspend fun match(lines: List<OcrLine>): CoverMatch? {
        var fallback: CoverMatch? = null
        for (q in buildQueries(lines)) {
            val results = lookup.search(q)
            val best = results.map { CoverMatch(it, q, scoreCandidate(lines, it)) }.maxByOrNull { it.score } ?: continue
            if (best.score >= MATCH_THRESHOLD) return best
            if (fallback == null) fallback = CoverMatch(results.first(), q, scoreCandidate(lines, results.first()))
        }
        return fallback
    }
}
```

`imports/ImportProcessor.kt`: replace the lookup block (from `val shortQuery = ...` through the `val hit = ...` expression) so it reads:

```kotlin
        val matcher = CoverMatcher(lookup)
        return try {
            val hit = matcher.match(lines)
            if (hit == null) {
                withPhoto.copy(state = ImportState.FAILED, query = query, error = "No match for \"$query\"")
            } else {
                withPhoto.copy(state = ImportState.REVIEW, query = query, resultJson = Json.encodeToString(lookup.details(hit.candidate)), error = null)
            }
        } catch (e: CancellationException) {
```
(keep the surrounding `catch` blocks and everything before `val query = buildQuery(lines)` exactly as is; `query` stays the full single query so the review screen's "search again" text is unchanged).

- [ ] **Step 4: Run** `.\gradlew.bat testDebugUnitTest` — all tests (existing `ImportProcessorTest` included) must pass. If an existing ImportProcessorTest case fails because of query order/count, **stop and report** the failing case instead of editing that test. Report diff; main session commits `feat(imports): CoverMatcher ranks candidates over query variants`.

---

### Task 3: ML Kit OCR dump receiver (debug only)

**Files:**
- Create: `debug/OcrDumpReceiver.kt` (debug)
- Modify: `app/src/debug/AndroidManifest.xml`

- [ ] **Step 1: Implement** — `debug/OcrDumpReceiver.kt`

```kotlin
package com.exc33ded.library.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.exc33ded.library.imports.OcrDump
import com.exc33ded.library.imports.PhotoPrep
import com.exc33ded.library.imports.TextReader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Debug builds only: run the real photo prep + ML Kit OCR over filesDir/bench/*.jpg and write filesDir/ocr_mlkit.json
 * (format: List<OcrDump>). Pull with: adb exec-out run-as com.exc33ded.library cat files/ocr_mlkit.json
 *   adb shell am broadcast -n com.exc33ded.library/.debug.OcrDumpReceiver
 */
class OcrDumpReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun prep(): PhotoPrep; fun reader(): TextReader }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val files = File(context.filesDir, "bench").listFiles { f -> f.extension.equals("jpg", true) }?.sortedBy { it.name }.orEmpty()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dumps = files.map { f ->
                    val prepared = deps.prep().prepare(f.absolutePath, "ocrdump-${f.nameWithoutExtension}")
                    OcrDump(f.name, deps.reader().read(prepared))
                }
                File(context.filesDir, "ocr_mlkit.json").writeText(Json.encodeToString(dumps))
                File(context.filesDir, "ocr_mlkit.done").writeText(dumps.size.toString())
            } finally { pending.finish() }
        }
    }
}
```

In `app/src/debug/AndroidManifest.xml` add inside `<application>`, after the existing receiver:

```xml
        <receiver
            android:name=".debug.OcrDumpReceiver"
            android:exported="true" />
```

- [ ] **Step 2: Verify** — `.\gradlew.bat assembleDebug` succeeds (Hilt resolves `PhotoPrep`/`TextReader` from `ImportModule`). Do not run on a device. Main session commits `feat(debug): ML Kit OCR dump receiver`.

---

### Task 4: JVM match benchmark

**Files:**
- Create: `imports/MatchBenchmarkTest.kt` (test)

- [ ] **Step 1: Implement** — `imports/MatchBenchmarkTest.kt`. It is **skipped** unless env `BENCH_OCR` is set. Env: `BENCH_OCR` (OCR dump json), `BENCH_LABELS` (labels.csv, columns `file,title,author,confidence,note`), `BENCH_OUT` (report file path).

```kotlin
package com.exc33ded.library.imports

import com.exc33ded.library.di.buildHttpClient
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.BookSource
import com.exc33ded.library.metadata.MetadataLookup
import com.exc33ded.library.metadata.OpenLibrarySource
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

private object NoSource : BookSource {
    override suspend fun byIsbn(isbn: String): BookCandidate? = null
    override suspend fun search(query: String, limit: Int): List<BookCandidate> = emptyList()
}

private fun words(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.isNotBlank() }

/** Same rule as tools/benchmark.py: >= 60% of the label's title words appear in the found title. */
internal fun titleOk(found: String, truth: String): Boolean {
    val f = words(found).toSet(); val t = words(truth)
    return t.isNotEmpty() && t.count { it in f }.toDouble() / t.size >= 0.6
}

/** Lenient upper bound: right author (surname) even if the title is in another language. */
internal fun authorOk(foundAuthors: String, truthAuthor: String): Boolean {
    val surname = words(truthAuthor).lastOrNull() ?: return false
    return surname in words(foundAuthors)
}

/** Minimal CSV line splitter with double-quote support. */
internal fun csvRow(line: String): List<String> {
    val out = mutableListOf<String>(); val sb = StringBuilder(); var q = false; var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' && q && line.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
            ch == '"' -> q = !q
            ch == ',' && !q -> { out += sb.toString(); sb.clear() }
            else -> sb.append(ch)
        }
        i++
    }
    return out + sb.toString()
}

class MatchBenchmarkTest {
    @Test fun scoringHelpers() {
        assertTrue(titleOk("Yellowface", "Yellowface"))
        assertFalse(titleOk("La Peste", "The Plague"))
        assertTrue(authorOk("Albert Camus", "Albert Camus"))
        assertEquals(listOf("a", "b,c", "d"), csvRow("a,\"b,c\",d"))
    }

    @Test fun replayOcrDumpThroughRealMatcher() = runBlocking {
        val ocrPath = System.getenv("BENCH_OCR"); val labelsPath = System.getenv("BENCH_LABELS")
        assumeTrue("set BENCH_OCR and BENCH_LABELS to run", ocrPath != null && labelsPath != null)
        val dumps = Json.decodeFromString<List<OcrDump>>(File(ocrPath!!).readText())
        val labels = File(labelsPath!!).readLines().drop(1).filter { it.isNotBlank() }.map(::csvRow).associateBy { it[0] }
        val matcher = CoverMatcher(MetadataLookup(OpenLibrarySource(buildHttpClient(OkHttp.create())), NoSource))

        var strict = 0; var lenient = 0; var scored = 0
        val report = StringBuilder()
        for (d in dumps) {
            val row = labels[d.file] ?: continue
            val (title, author) = row[1] to row.getOrElse(2) { "" }
            if (title.equals("Not a book", true)) continue
            scored++
            val hit = try { matcher.match(d.lines) } catch (e: Exception) { report.appendLine("ERROR  $title: ${e.message}"); null }
            delay(250) // be polite to Open Library
            when {
                hit == null -> report.appendLine("FAILED $title")
                titleOk(hit.candidate.title, title) -> { strict++; lenient++ }
                authorOk(hit.candidate.authors, author) -> { lenient++; report.appendLine("LENIENT $title -> ${hit.candidate.title} (q=${hit.query}, score=${"%.2f".format(hit.score)})") }
                else -> report.appendLine("WRONG  $title -> ${hit.candidate.title} (q=${hit.query}, score=${"%.2f".format(hit.score)})")
            }
        }
        val head = "photos=$scored strict=$strict (${strict * 100 / maxOf(scored, 1)}%) authorLenient=$lenient (${lenient * 100 / maxOf(scored, 1)}%)"
        File(System.getenv("BENCH_OUT") ?: "bench-report.txt").writeText(head + "\n" + report)
        println(head)
    }
}
```

- [ ] **Step 2: Verify** — `.\gradlew.bat testDebugUnitTest --tests "*.MatchBenchmarkTest"` passes with `scoringHelpers` green and `replayOcrDumpThroughRealMatcher` **skipped** (no env vars set). Then `.\gradlew.bat testDebugUnitTest` (all). Do not set `BENCH_*` and do not read `docs/Books-Photo/`. Main session commits `test(imports): replayable match benchmark`.

---

### Task 5: Measure and decide (main session only)

- [ ] **Step 1: ML Kit dump.** Start the emulator, `.\gradlew.bat installDebug`, then (photos already in `/data/local/tmp/books`):
  ```powershell
  adb shell 'run-as com.exc33ded.library sh -c "mkdir -p files/bench && cp /data/local/tmp/books/*.jpg files/bench/"'
  adb shell am broadcast -n com.exc33ded.library/.debug.OcrDumpReceiver
  # wait until files/ocr_mlkit.done exists, then:
  adb exec-out run-as com.exc33ded.library cat files/ocr_mlkit.json > docs/Books-Photo/ocr_mlkit.json
  ```
- [ ] **Step 2: Baseline with the new matching.**
  ```powershell
  $env:BENCH_OCR="D:\claude-exp\words-and-wounds\docs\Books-Photo\ocr_mlkit.json"
  $env:BENCH_LABELS="D:\claude-exp\words-and-wounds\docs\Books-Photo\labels.csv"
  $env:BENCH_OUT="D:\claude-exp\words-and-wounds\docs\Books-Photo\report_mlkit.txt"
  .\gradlew.bat testDebugUnitTest --tests "*.MatchBenchmarkTest" --rerun-tasks
  ```
  Compare strict/lenient against the 33% / 43% baseline.
- [ ] **Step 3: PaddleOCR dump.** Write `tools/ocr_paddle.py` (same JSON format: `[{"file", "lines":[{"text","height"}]}]`; same prep as the app: EXIF-transpose, longest edge ≤ 1600, try rotations 0/90/270 and keep the read with most letters; `height` = shorter side of each text box). Look up the current PaddleOCR API and the PP-OCRv6 model name with Context7 first. If `paddlepaddle` has no wheel for the system Python 3.14, use a Python 3.12 venv outside the repo. Run it over `docs/Books-Photo/*.jpg` → `docs/Books-Photo/ocr_paddle.json`, then repeat Step 2 with `BENCH_OCR`/`BENCH_OUT` pointing at the Paddle files.
- [ ] **Step 4: Decide** with the rule in the spec (≥ 8 points same-work gain over ML Kit after the fixes, and a plausible Android runtime). Write the before/after table into spec §14 (`docs/superpowers/specs/2026-11-21-book-library-app-design.md`) and update `docs/HANDOFF.md` (status, numbers, next = AI model comparison; keep `ocr_*.json`/reports in the gitignored `docs/Books-Photo/`). Commit docs and `tools/ocr_paddle.py`.

## Self-review against the spec

- Query variants + candidate ranking + fuzzy misread tolerance → Tasks 1–2.
- English preference → via ranking (Task 1–2); source `lang` params deferred (recorded above).
- Same-work scoring → Task 4 reports strict + author-lenient (deviation recorded above).
- OCR dump (ML Kit debug receiver, Paddle script) + replay benchmark on the real code path → Tasks 3, 4, 5.
- OCR comparison decision rule → Task 5 step 4. AI title correction stage → not built here (ships with sub-project 3); the benchmark replays any OCR/query source.
- Names consistent across tasks: `OcrLine`, `OcrDump`, `buildQuery`, `buildQueries`, `scoreCandidate`, `fuzzyHas`, `editDistance`, `MATCH_THRESHOLD`, `CoverMatcher.match` → `CoverMatch(candidate, query, score)`, `titleOk`, `authorOk`, `csvRow`.
