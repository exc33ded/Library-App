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
