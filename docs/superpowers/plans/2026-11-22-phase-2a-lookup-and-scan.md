# Phase 2a — Book Lookup, ISBN Scan & Covers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Execution model:** each task is pasted whole into `opencode run` (free model, edit-only permissions). Claude reviews the diff, runs the verify commands, commits. Never pass `local.properties` to opencode.

**Goal:** Add books by scanning an ISBN barcode or searching by title/author: metadata (title, authors, description, pages, subjects) comes from Open Library with Google Books as fallback, the cover image is downloaded and shown everywhere, and subjects become suggested categories on the review/edit screen.

**Architecture:** New `metadata` package: two `BookSource`s (Open Library, Google Books) behind a `MetadataLookup` that chains them and tolerates one source failing. A Ktor `HttpClient` (OkHttp engine, kotlinx-json) is provided by Hilt and built by one function that tests reuse with `MockEngine`. Covers are downloaded to `filesDir/covers/{bookId}.jpg` and stored in `BookEntity.coverPath` (column already exists). The add-book FAB opens a menu: Scan ISBN (Google Code Scanner — no camera permission), Search, Manual. Phase 2b (photos, OCR, batch import) builds on `MetadataLookup.search()`.

**Tech Stack:** Ktor 3.6.1 (okhttp, content-negotiation, kotlinx-json, mock) · Coil 3.6.3 (`coil-compose`, `coil-network-ktor3`) · Google Code Scanner `play-services-code-scanner:16.1.0` · `kotlinx-coroutines-play-services:1.11.0`. Versions pinned 2026-11-22.

**Verified API facts (2026-11-22):**
- Open Library `GET https://openlibrary.org/search.json?{isbn=|q=}&fields=key,title,author_name,cover_i,number_of_pages_median,isbn,subject&limit=N` → `{"docs":[{"key":"/works/OL…W","title":…,"author_name":[…],"cover_i":13195421,"number_of_pages_median":352,"isbn":[…],"subject":[…]}]}`. Free-text `q=` matched "ruskin bond great stories children".
- Work description: `GET https://openlibrary.org/works/OL…W.json` → `"description"` is either a string or `{"type":"/type/text","value":"…"}`.
- Covers: `https://covers.openlibrary.org/b/id/{cover_i}-L.jpg` (by-id has no rate limit; by-ISBN is 100/5 min).
- Send `User-Agent: LibraryApp/0.1 (https://github.com/exc33ded/Library-App)` (raises OL limit to 3 req/s). Do not put personal email in it.
- Google Books without a key returns HTTP 429 (shared quota exhausted) → key is required: `local.properties` `googleBooksKey=…`.

---

## File map

```
gradle/libs.versions.toml                       + ktor, coil, code-scanner, coroutines-play-services
app/build.gradle.kts                            + deps, buildConfig, GOOGLE_BOOKS_KEY from local.properties
app/src/main/AndroidManifest.xml                + INTERNET permission
app/src/main/java/com/exc33ded/library/
  metadata/Isbn.kt                              normalise / validate ISBN-10/13 (pure)
  metadata/BookCandidate.kt                     source-agnostic result + subject cleaning (pure)
  metadata/BookSource.kt                        interface
  metadata/OpenLibrarySource.kt
  metadata/GoogleBooksSource.kt
  metadata/MetadataLookup.kt                    chain + merge + error tolerance
  metadata/CoverStore.kt                        download cover to filesDir
  di/NetworkModule.kt                           buildHttpClient() + Hilt bindings
  ui/common/BookCover.kt                        Coil image or CoverPlaceholder
  ui/nav/Keys.kt                                + FindBookKey, BookEditKey(prefill)
  ui/nav/AppNav.kt                              FAB menu, scanner, new destinations
  ui/find/FindBookViewModel.kt
  ui/find/FindBookScreen.kt
  ui/book/BookEditViewModel.kt                  prefill, suggested categories, cover download on save
  ui/book/BookEditScreen.kt                     cover preview + suggestion chips
  ui/library/LibraryScreen.kt                   BookCover instead of CoverPlaceholder
  ui/book/BookDetailScreen.kt                   BookCover instead of CoverPlaceholder
app/src/test/java/com/exc33ded/library/metadata/
  IsbnTest.kt  BookCandidateTest.kt  OpenLibrarySourceTest.kt  GoogleBooksSourceTest.kt  MetadataLookupTest.kt
```

---

### Task 1: Dependencies, BuildConfig key, INTERNET

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: `gradle/libs.versions.toml`** — add under `[versions]`:

```toml
ktor = "3.6.1"
coil = "3.6.3"
codeScanner = "16.1.0"
```

and under `[libraries]`:

```toml
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-ktor3 = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }
play-services-code-scanner = { module = "com.google.android.gms:play-services-code-scanner", version.ref = "codeScanner" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "coroutines" }
```

- [ ] **Step 2: `app/build.gradle.kts`**

Add at the top of the file, after the `plugins { }` block:

```kotlin
import java.util.Properties  // at top of file
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
```

Inside `android { defaultConfig { … } }` add:

```kotlin
        buildConfigField("String", "GOOGLE_BOOKS_KEY", "\"${localProps.getProperty("googleBooksKey", "")}\"")
```

Change `buildFeatures { compose = true }` to:

```kotlin
    buildFeatures { compose = true; buildConfig = true }
```

Add to `dependencies { }`:

```kotlin
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.play.services.code.scanner)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.ktor.client.mock)
```

- [ ] **Step 3: Manifest** — add as the first child of `<manifest>`:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 4: Verify + commit**

Run: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL
```powershell
git add gradle app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "build: ktor, coil, code scanner; Google Books key from local.properties"
```

---

### Task 2: ISBN utilities and candidate model (pure)

**Files:** Create `metadata/Isbn.kt`, `metadata/BookCandidate.kt`; Test `metadata/IsbnTest.kt`, `metadata/BookCandidateTest.kt`

- [ ] **Step 1: Failing tests**

`app/src/test/java/com/exc33ded/library/metadata/IsbnTest.kt`
```kotlin
package com.exc33ded.library.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsbnTest {
    @Test fun validIsbn13IsKept() = assertEquals("9780063250833", Isbn.normalize("978-0-06-325083-3"))
    @Test fun badChecksumRejected() = assertNull(Isbn.normalize("9780063250834"))
    @Test fun nonBookEanRejected() = assertNull(Isbn.normalize("4006381333931")) // valid EAN, not 978/979
    @Test fun isbn10ConvertedTo13() = assertEquals("9780063250833", Isbn.normalize("0063250837"))
    @Test fun isbn10WithXCheckDigit() = assertEquals("9780804429573", Isbn.normalize("080442957X"))
    @Test fun garbageRejected() {
        assertNull(Isbn.normalize(""))
        assertNull(Isbn.normalize("12345"))
    }
}
```

`app/src/test/java/com/exc33ded/library/metadata/BookCandidateTest.kt`
```kotlin
package com.exc33ded.library.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class BookCandidateTest {
    @Test fun subjectsAreCleanedDedupedAndCapped() {
        val raw = listOf(
            "form:novel", "genre:satire", "Satire", "cultural appropriation",
            "nyt:combined-print-and-e-book-fiction=2023-06-04", "Fiction -- General",
            "A very long subject heading that nobody wants as a category", "Fiction",
        )
        assertEquals(listOf("Novel", "Satire", "Cultural Appropriation", "Fiction"), cleanSubjects(raw))
    }

    @Test fun suggestionsPutExistingCategoriesFirstAndCapAtFive() {
        val subjects = listOf("Mystery", "Cats", "Fiction", "Bookstores", "Cozy", "Women Sleuths", "Extra")
        val existing = listOf("Fiction", "Thriller")
        assertEquals(listOf("Fiction", "Mystery", "Cats", "Bookstores", "Cozy"), suggestCategories(subjects, existing))
    }
}
```

- [ ] **Step 2: Run** `.\gradlew.bat testDebugUnitTest --tests "com.exc33ded.library.metadata.*"` → FAIL (unresolved).

- [ ] **Step 3: Implement**

`app/src/main/java/com/exc33ded/library/metadata/Isbn.kt`
```kotlin
package com.exc33ded.library.metadata

object Isbn {
    /** Returns a valid 978/979 ISBN-13 or null. Accepts ISBN-10 (converted), hyphens and spaces. */
    fun normalize(raw: String): String? {
        val s = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
        return when {
            s.length == 13 && s.all(Char::isDigit) && checksum13(s.take(12)) == s[12] - '0' &&
                (s.startsWith("978") || s.startsWith("979")) -> s
            s.length == 10 && isValid10(s) -> "978" + s.take(9) + checksum13("978" + s.take(9))
            else -> null
        }
    }

    private fun checksum13(first12: String): Int =
        (10 - first12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum() % 10) % 10

    private fun isValid10(s: String): Boolean {
        if (!s.take(9).all(Char::isDigit)) return false
        val last = s[9]
        if (!last.isDigit() && last != 'X') return false
        val sum = (0 until 9).sumOf { (s[it] - '0') * (10 - it) } + if (last == 'X') 10 else last - '0'
        return sum % 11 == 0
    }
}
```

`app/src/main/java/com/exc33ded/library/metadata/BookCandidate.kt`
```kotlin
package com.exc33ded.library.metadata

import kotlinx.serialization.Serializable

/** Source-agnostic lookup result; serializable so it can ride in a Navigation 3 key. */
@Serializable
data class BookCandidate(
    val title: String,
    val authors: String,
    val isbn: String? = null,
    val description: String? = null,
    val pageCount: Int? = null,
    val coverUrl: String? = null,
    val subjects: List<String> = emptyList(),
    val olWorkKey: String? = null,
)

/** Turn raw OL/Google subject strings into short, human category names. */
fun cleanSubjects(raw: List<String>): List<String> = raw.asSequence()
    .filterNot { ':' in it.substringAfter("form:").substringAfter("genre:") || '=' in it }
    .map { it.removePrefix("form:").removePrefix("genre:").substringBefore(" -- ").substringBefore(" / ").trim() }
    .filter { it.length in 3..30 }
    .map { s -> s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } } }
    .distinctBy { it.lowercase() }
    .toList()

/** Existing category names that match come first (with the existing spelling), then new ones; max 5. */
fun suggestCategories(subjects: List<String>, existing: List<String>): List<String> {
    val cleaned = cleanSubjects(subjects)
    val byLower = existing.associateBy { it.lowercase() }
    val matched = cleaned.mapNotNull { byLower[it.lowercase()] }
    val fresh = cleaned.filter { it.lowercase() !in byLower }
    return (matched + fresh).take(5)
}
```

Why the first test's expectation holds: `"nyt:combined…=…"` is dropped (`:`/`=` remain after prefix stripping); `"Fiction -- General"` becomes `"Fiction"` and dedupes with the later `"Fiction"`; the long heading is > 30 chars; order follows first appearance. Matched existing categories are returned with the **existing** spelling.

- [ ] **Step 4: Run** `.\gradlew.bat testDebugUnitTest --tests "com.exc33ded.library.metadata.*"` → PASS (8 tests).

- [ ] **Step 5: Commit**
```powershell
git add app/src
git commit -m "feat(metadata): ISBN normalisation and subject-to-category suggestions"
```

---

### Task 3: HTTP client and the two sources

**Files:** Create `di/NetworkModule.kt`, `metadata/BookSource.kt`, `metadata/OpenLibrarySource.kt`, `metadata/GoogleBooksSource.kt`; Test `metadata/OpenLibrarySourceTest.kt`, `metadata/GoogleBooksSourceTest.kt`

- [ ] **Step 1: Failing tests**

`app/src/test/java/com/exc33ded/library/metadata/OpenLibrarySourceTest.kt`
```kotlin
package com.exc33ded.library.metadata

import com.exc33ded.library.di.buildHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLibrarySourceTest {
    private val searchJson = """
        {"numFound":1,"docs":[{"key":"/works/OL29050559W","title":"Yellowface","author_name":["R. F. Kuang"],
        "cover_i":13195421,"number_of_pages_median":352,"isbn":["0063250837","9780063250833"],
        "subject":["form:novel","genre:satire"]}]}
    """.trimIndent()

    private fun source(vararg bodies: Pair<String, String>): Pair<OpenLibrarySource, MutableList<String>> {
        val urls = mutableListOf<String>()
        val engine = MockEngine { req ->
            urls += req.url.toString()
            val body = bodies.firstOrNull { req.url.encodedPath.startsWith(it.first) }?.second ?: """{"docs":[]}"""
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return OpenLibrarySource(buildHttpClient(engine)) to urls
    }

    @Test fun byIsbnMapsFields() = runTest {
        val (src, urls) = source("/search.json" to searchJson)
        val c = src.byIsbn("9780063250833")!!
        assertEquals("Yellowface", c.title)
        assertEquals("R. F. Kuang", c.authors)
        assertEquals("9780063250833", c.isbn)
        assertEquals(352, c.pageCount)
        assertEquals("https://covers.openlibrary.org/b/id/13195421-L.jpg", c.coverUrl)
        assertEquals("/works/OL29050559W", c.olWorkKey)
        assertTrue(urls.single().contains("isbn=9780063250833"))
    }

    @Test fun searchReturnsEmptyWhenNoDocs() = runTest {
        val (src, _) = source()
        assertTrue(src.search("nothing").isEmpty())
    }

    @Test fun byIsbnNullWhenNotFound() = runTest {
        assertNull(source().first.byIsbn("9780000000002"))
    }

    @Test fun enrichReadsStringOrObjectDescription() = runTest {
        val base = BookCandidate("Yellowface", "R. F. Kuang", olWorkKey = "/works/OL1W")
        val (s1, _) = source("/works/" to """{"description":"Plain text."}""")
        assertEquals("Plain text.", s1.enrich(base).description)
        val (s2, _) = source("/works/" to """{"description":{"type":"/type/text","value":"Object text."}}""")
        assertEquals("Object text.", s2.enrich(base).description)
    }
}
```

`app/src/test/java/com/exc33ded/library/metadata/GoogleBooksSourceTest.kt`
```kotlin
package com.exc33ded.library.metadata

import com.exc33ded.library.di.buildHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleBooksSourceTest {
    private val json = """
        {"items":[{"volumeInfo":{"title":"Yellowface","authors":["R. F. Kuang"],"description":"A satire.",
        "pageCount":336,"categories":["Fiction / Satire"],
        "imageLinks":{"thumbnail":"http://books.google.com/books/content?id=x&printsec=frontcover&img=1&zoom=1&edge=curl"},
        "industryIdentifiers":[{"type":"ISBN_10","identifier":"0063250837"},{"type":"ISBN_13","identifier":"9780063250833"}]}}]}
    """.trimIndent()

    private val urls = mutableListOf<io.ktor.http.Url>()
    private fun source(key: String) = GoogleBooksSource(
        buildHttpClient(MockEngine { req ->
            urls += req.url
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
        apiKey = key,
    )

    @Test fun mapsVolume() = runTest {
        val c = source("").byIsbn("9780063250833")!!
        assertEquals("Yellowface", c.title)
        assertEquals("A satire.", c.description)
        assertEquals("9780063250833", c.isbn)
        assertEquals(listOf("Fiction / Satire"), c.subjects)
        assertEquals("https://books.google.com/books/content?id=x&printsec=frontcover&img=1&zoom=1", c.coverUrl)
        assertEquals("isbn:9780063250833", urls.single().parameters["q"])
    }

    @Test fun keyOnlySentWhenPresent() = runTest {
        source("").search("dune")
        assertNull(urls.last().parameters["key"])
        source("abc").search("dune")
        assertEquals("abc", urls.last().parameters["key"])
    }
}
```

- [ ] **Step 2: Run** `.\gradlew.bat testDebugUnitTest --tests "com.exc33ded.library.metadata.*"` → FAIL (unresolved).

- [ ] **Step 3: Implement**

`app/src/main/java/com/exc33ded/library/di/NetworkModule.kt`
```kotlin
package com.exc33ded.library.di

import com.exc33ded.library.BuildConfig
import com.exc33ded.library.metadata.GoogleBooksSource
import com.exc33ded.library.metadata.OpenLibrarySource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

const val USER_AGENT = "LibraryApp/0.1 (https://github.com/exc33ded/Library-App)"

/** One place that configures HTTP; tests pass a MockEngine. */
fun buildHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
    install(UserAgent) { agent = USER_AGENT }
    install(HttpTimeout) { requestTimeoutMillis = 15_000 }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun httpClient(): HttpClient = buildHttpClient(OkHttp.create())
    @Provides @Singleton fun openLibrary(client: HttpClient) = OpenLibrarySource(client)
    @Provides @Singleton fun googleBooks(client: HttpClient) = GoogleBooksSource(client, BuildConfig.GOOGLE_BOOKS_KEY)
}
```

`app/src/main/java/com/exc33ded/library/metadata/BookSource.kt`
```kotlin
package com.exc33ded.library.metadata

interface BookSource {
    suspend fun byIsbn(isbn: String): BookCandidate?
    suspend fun search(query: String, limit: Int = 10): List<BookCandidate>
    /** Fill in details the search response lacks (e.g. description). */
    suspend fun enrich(c: BookCandidate): BookCandidate = c
}
```

`app/src/main/java/com/exc33ded/library/metadata/OpenLibrarySource.kt`
```kotlin
package com.exc33ded.library.metadata

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class OpenLibrarySource(private val client: HttpClient) : BookSource {

    override suspend fun byIsbn(isbn: String): BookCandidate? =
        query { parameter("isbn", isbn); parameter("limit", 1) }.firstOrNull()?.copy(isbn = isbn)

    override suspend fun search(query: String, limit: Int): List<BookCandidate> =
        query { parameter("q", query); parameter("limit", limit) }

    override suspend fun enrich(c: BookCandidate): BookCandidate {
        if (c.description != null || c.olWorkKey == null) return c
        val work: OlWork = client.get("$BASE${c.olWorkKey}.json").body()
        val text = when (val d = work.description) {
            is JsonPrimitive -> d.content
            is JsonObject -> d["value"]?.jsonPrimitive?.content
            else -> null
        }
        return c.copy(description = text?.trim()?.ifEmpty { null })
    }

    private suspend fun query(params: io.ktor.client.request.HttpRequestBuilder.() -> Unit): List<BookCandidate> {
        val res: OlSearch = client.get("$BASE/search.json") {
            parameter("fields", "key,title,author_name,cover_i,number_of_pages_median,isbn,subject")
            params()
        }.body()
        return res.docs.map { it.toCandidate() }
    }

    private fun OlDoc.toCandidate() = BookCandidate(
        title = title,
        authors = authorName.joinToString(", "),
        isbn = isbn.firstNotNullOfOrNull { Isbn.normalize(it) },
        pageCount = pages,
        coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
        subjects = subject.take(15),
        olWorkKey = key,
    )

    @Serializable private data class OlSearch(val docs: List<OlDoc> = emptyList())

    @Serializable private data class OlDoc(
        val key: String,
        val title: String,
        @SerialName("author_name") val authorName: List<String> = emptyList(),
        @SerialName("cover_i") val coverId: Long? = null,
        @SerialName("number_of_pages_median") val pages: Int? = null,
        val isbn: List<String> = emptyList(),
        val subject: List<String> = emptyList(),
    )

    @Serializable private data class OlWork(val description: JsonElement? = null)

    private companion object { const val BASE = "https://openlibrary.org" }
}
```

`app/src/main/java/com/exc33ded/library/metadata/GoogleBooksSource.kt`
```kotlin
package com.exc33ded.library.metadata

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

class GoogleBooksSource(private val client: HttpClient, private val apiKey: String) : BookSource {

    override suspend fun byIsbn(isbn: String): BookCandidate? = fetch("isbn:$isbn", 1).firstOrNull()

    override suspend fun search(query: String, limit: Int): List<BookCandidate> = fetch(query, limit)

    private suspend fun fetch(q: String, limit: Int): List<BookCandidate> {
        val res: GbResponse = client.get("https://www.googleapis.com/books/v1/volumes") {
            parameter("q", q)
            parameter("maxResults", limit.coerceIn(1, 40))
            if (apiKey.isNotBlank()) parameter("key", apiKey)
        }.body()
        return res.items.map { it.volumeInfo.toCandidate() }
    }

    private fun GbVolume.toCandidate() = BookCandidate(
        title = title,
        authors = authors.joinToString(", "),
        isbn = industryIdentifiers.firstOrNull { it.type == "ISBN_13" }?.identifier?.let(Isbn::normalize)
            ?: industryIdentifiers.firstOrNull { it.type == "ISBN_10" }?.identifier?.let(Isbn::normalize),
        description = description,
        pageCount = pageCount,
        coverUrl = imageLinks?.thumbnail?.replace("http://", "https://")?.replace("&edge=curl", ""),
        subjects = categories,
    )

    @Serializable private data class GbResponse(val items: List<GbItem> = emptyList())
    @Serializable private data class GbItem(val volumeInfo: GbVolume)
    @Serializable private data class GbVolume(
        val title: String = "",
        val authors: List<String> = emptyList(),
        val description: String? = null,
        val pageCount: Int? = null,
        val categories: List<String> = emptyList(),
        val imageLinks: GbImages? = null,
        val industryIdentifiers: List<GbId> = emptyList(),
    )
    @Serializable private data class GbImages(val thumbnail: String? = null)
    @Serializable private data class GbId(val type: String, val identifier: String)
}
```

- [ ] **Step 4: Run** `.\gradlew.bat testDebugUnitTest --tests "com.exc33ded.library.metadata.*"` → PASS.

- [ ] **Step 5: Commit**
```powershell
git add app/src
git commit -m "feat(metadata): Open Library and Google Books sources over Ktor"
```

---

### Task 4: MetadataLookup (chain, merge, error tolerance)

**Files:** Create `metadata/MetadataLookup.kt`; Test `metadata/MetadataLookupTest.kt`

- [ ] **Step 1: Failing test**

`app/src/test/java/com/exc33ded/library/metadata/MetadataLookupTest.kt`
```kotlin
package com.exc33ded.library.metadata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

private class FakeSource(
    val isbnResult: BookCandidate? = null,
    val searchResult: List<BookCandidate> = emptyList(),
    val fail: Boolean = false,
    val enrichWith: String? = null,
) : BookSource {
    override suspend fun byIsbn(isbn: String) = if (fail) throw IOException("down") else isbnResult
    override suspend fun search(query: String, limit: Int) = if (fail) throw IOException("down") else searchResult
    override suspend fun enrich(c: BookCandidate) = if (enrichWith != null) c.copy(description = enrichWith) else c
}

class MetadataLookupTest {
    private val ol = BookCandidate("Yellowface", "R. F. Kuang", isbn = "9780063250833", olWorkKey = "/works/1")
    private val gb = BookCandidate("Yellowface", "R. F. Kuang", isbn = "9780063250833", description = "From Google.")

    @Test fun isbnPrefersOpenLibraryAndFillsDescriptionFromWork() = runTest {
        val r = MetadataLookup(FakeSource(ol, enrichWith = "From work."), FakeSource(gb)).lookupIsbn("9780063250833")
        assertEquals("From work.", r?.description)
    }

    @Test fun isbnFallsBackToGoogleDescriptionWhenWorkHasNone() = runTest {
        val r = MetadataLookup(FakeSource(ol), FakeSource(gb)).lookupIsbn("9780063250833")
        assertEquals("From Google.", r?.description)
    }

    @Test fun isbnUsesGoogleWhenOpenLibraryIsDown() = runTest {
        val r = MetadataLookup(FakeSource(fail = true), FakeSource(gb)).lookupIsbn("9780063250833")
        assertEquals("From Google.", r?.description)
    }

    @Test(expected = IOException::class)
    fun isbnThrowsWhenEverySourceFails() = runTest {
        MetadataLookup(FakeSource(fail = true), FakeSource(fail = true)).lookupIsbn("9780063250833")
    }

    @Test fun isbnNullWhenNobodyKnowsIt() = runTest {
        assertNull(MetadataLookup(FakeSource(), FakeSource()).lookupIsbn("9780063250833"))
    }

    @Test fun searchMergesAndDedupes() = runTest {
        val other = BookCandidate("Babel", "R. F. Kuang")
        val r = MetadataLookup(FakeSource(searchResult = listOf(ol)), FakeSource(searchResult = listOf(gb, other)))
            .search("kuang")
        assertEquals(listOf("Yellowface", "Babel"), r.map { it.title })
    }
}
```

- [ ] **Step 2: Run** → FAIL (unresolved `MetadataLookup`).

- [ ] **Step 3: Implement** `app/src/main/java/com/exc33ded/library/metadata/MetadataLookup.kt`

```kotlin
package com.exc33ded.library.metadata

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Open Library first, Google Books as fallback / description filler.
 * One source failing is tolerated; if every source fails the last error is rethrown
 * so the UI can say "offline" instead of "not found".
 */
@Singleton
class MetadataLookup(private val ol: BookSource, private val gb: BookSource) {
    @Inject constructor(ol: OpenLibrarySource, gb: GoogleBooksSource) : this(ol as BookSource, gb as BookSource)

    suspend fun lookupIsbn(isbn: String): BookCandidate? {
        val errors = mutableListOf<Exception>()
        val fromOl = attempt(errors) { ol.byIsbn(isbn) }
        val fromGb = if (fromOl == null) attempt(errors) { gb.byIsbn(isbn) } else null
        val base = fromOl ?: fromGb ?: run { if (errors.size == 2) throw errors.last(); return null }
        return details(base)
    }

    suspend fun search(query: String): List<BookCandidate> {
        val errors = mutableListOf<Exception>()
        val results = (attempt(errors) { ol.search(query) }.orEmpty() + attempt(errors) { gb.search(query) }.orEmpty())
            .distinctBy { it.title.lowercase().trim() to it.authors.lowercase().substringBefore(",").trim() }
        if (results.isEmpty() && errors.size == 2) throw errors.last()
        return results
    }

    /** Description: OL work → Google Books by ISBN. Never throws. */
    suspend fun details(c: BookCandidate): BookCandidate {
        val errors = mutableListOf<Exception>()
        var r = attempt(errors) { ol.enrich(c) } ?: c
        if (r.description == null && r.isbn != null) {
            attempt(errors) { gb.byIsbn(r.isbn!!) }?.let { g ->
                r = r.copy(
                    description = g.description,
                    pageCount = r.pageCount ?: g.pageCount,
                    coverUrl = r.coverUrl ?: g.coverUrl,
                    subjects = r.subjects.ifEmpty { g.subjects },
                )
            }
        }
        return r
    }

    private suspend fun <T> attempt(errors: MutableList<Exception>, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errors += e; null
    }
}
```

- [ ] **Step 4: Run** `.\gradlew.bat testDebugUnitTest` → PASS.

- [ ] **Step 5: Commit**
```powershell
git add app/src
git commit -m "feat(metadata): lookup chain with fallback and dedupe"
```

---

### Task 5: Cover storage and BookCover composable

**Files:** Create `metadata/CoverStore.kt`, `ui/common/BookCover.kt`; Modify `ui/library/LibraryScreen.kt`, `ui/book/BookDetailScreen.kt`

- [ ] **Step 1: `metadata/CoverStore.kt`**

```kotlin
package com.exc33ded.library.metadata

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Covers live in filesDir/covers/{bookId}.jpg; BookEntity.coverPath stores the absolute path. */
@Singleton
class CoverStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: HttpClient,
) {
    /** Returns the saved path, or null if the download failed (book is saved without a cover). */
    suspend fun download(url: String, bookId: String): String? = try {
        val bytes = client.get(url).bodyAsBytes()
        withContext(Dispatchers.IO) {
            val file = File(ctx.filesDir, "covers/$bookId.jpg").apply { parentFile?.mkdirs() }
            file.writeBytes(bytes)
            file.absolutePath
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
```

- [ ] **Step 2: `ui/common/BookCover.kt`**

```kotlin
package com.exc33ded.library.ui.common

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import java.io.File

/** Local file path or remote URL; falls back to the text placeholder while loading / on error. */
@Composable
fun BookCover(title: String, cover: String?, modifier: Modifier = Modifier) {
    if (cover == null) { CoverPlaceholder(title, modifier); return }
    SubcomposeAsyncImage(
        model = if (cover.startsWith("http")) cover else File(cover),
        contentDescription = title,
        contentScale = ContentScale.Crop,
        loading = { CoverPlaceholder(title) },
        error = { CoverPlaceholder(title) },
        modifier = modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
    )
}
```

- [ ] **Step 3: Use it in the library grid and detail**

In `ui/library/LibraryScreen.kt`, inside `BookTile`, replace `CoverPlaceholder(b.title)` with `BookCover(b.title, b.coverPath)`, and replace the import `com.exc33ded.library.ui.common.CoverPlaceholder` with `com.exc33ded.library.ui.common.BookCover`.

In `ui/book/BookDetailScreen.kt`, replace `CoverPlaceholder(b.title, Modifier.width(160.dp))` with `BookCover(b.title, b.coverPath, Modifier.width(160.dp))`, and replace the import `com.exc33ded.library.ui.common.CoverPlaceholder` with `com.exc33ded.library.ui.common.BookCover`.

- [ ] **Step 4: Verify + commit**

Run: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL
```powershell
git add app/src
git commit -m "feat(covers): download covers to app storage and show them with Coil"
```

---

### Task 6: Edit screen accepts a prefill (cover preview, suggested categories)

**Files:** Modify `ui/nav/Keys.kt`, replace `ui/book/BookEditViewModel.kt`, modify `ui/book/BookEditScreen.kt`, modify `ui/nav/AppNav.kt`

- [ ] **Step 1: `ui/nav/Keys.kt`** — replace the `BookEditKey` line and add `FindBookKey`:

```kotlin
@Serializable data class BookEditKey(val id: String? = null, val prefill: BookCandidate? = null) : NavKey
@Serializable data class FindBookKey(val isbn: String? = null) : NavKey
```
Add import `com.exc33ded.library.metadata.BookCandidate`.

- [ ] **Step 2: Replace `ui/book/BookEditViewModel.kt` entirely**

```kotlin
package com.exc33ded.library.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.data.Ids
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.CoverStore
import com.exc33ded.library.metadata.suggestCategories
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditUiState(
    val form: BookForm = BookForm(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategories: Set<String> = emptySet(),
    /** Category names suggested from subjects that the user hasn't added yet. */
    val suggestions: List<String> = emptyList(),
    val coverPreview: String? = null,
    val saving: Boolean = false,
    val error: FormError? = null,
    val savedId: String? = null,
)

@HiltViewModel(assistedFactory = BookEditViewModel.Factory::class)
class BookEditViewModel @AssistedInject constructor(
    private val repo: BookRepository,
    private val covers: CoverStore,
    @Assisted private val bookId: String?,
    @Assisted private val prefill: BookCandidate?,
) : ViewModel() {
    @AssistedFactory interface Factory { fun create(bookId: String?, prefill: BookCandidate?): BookEditViewModel }

    private var original: BookEntity = BookEntity(id = bookId ?: Ids.new(), title = "")
    private var preselectDone = bookId != null // only auto-select suggested categories once, for new books
    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state

    init {
        viewModelScope.launch {
            bookId?.let { id ->
                repo.getBook(id)?.let { original = it }
                _state.update {
                    it.copy(form = BookForm.from(original), selectedCategories = repo.categoryIdsFor(id), coverPreview = original.coverPath)
                }
            }
            prefill?.let { p ->
                original = original.copy(olWorkKey = p.olWorkKey)
                _state.update {
                    it.copy(
                        form = BookForm(
                            title = p.title, authors = p.authors, isbn = p.isbn.orEmpty(),
                            pageCount = p.pageCount?.toString().orEmpty(), description = p.description.orEmpty(),
                        ),
                        coverPreview = p.coverUrl,
                    )
                }
            }
        }
        viewModelScope.launch {
            repo.observeCategories().collect { cats ->
                _state.update { s ->
                    val names = cats.map { it.name }
                    val suggested = prefill?.let { suggestCategories(it.subjects, names) }.orEmpty()
                    val preselect = cats.filter { c -> suggested.any { it.equals(c.name, ignoreCase = true) } }.map { it.id }
                    val selected = if (!preselectDone) s.selectedCategories + preselect else s.selectedCategories
                    preselectDone = true
                    s.copy(
                        categories = cats,
                        selectedCategories = selected,
                        suggestions = suggested.filter { n -> cats.none { it.name.equals(n, ignoreCase = true) } },
                    )
                }
            }
        }
    }

    fun edit(change: (BookForm) -> BookForm) = _state.update { it.copy(form = change(it.form), error = null) }

    fun toggleCategory(id: String) = _state.update {
        it.copy(selectedCategories = if (id in it.selectedCategories) it.selectedCategories - id else it.selectedCategories + id)
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { toggleCategory(repo.saveCategory(name)) }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        s.form.validate()?.let { e -> _state.update { it.copy(error = e) }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val remote = s.coverPreview?.takeIf { it.startsWith("http") }
            val coverPath = remote?.let { covers.download(it, original.id) } ?: original.coverPath
            val id = repo.saveBook(s.form.applyTo(original).copy(coverPath = coverPath), s.selectedCategories)
            _state.update { it.copy(savedId = id, saving = false) }
        }
    }
}
```

- [ ] **Step 3: `ui/book/BookEditScreen.kt`** changes

(a) Signature and ViewModel creation — replace the function header down to `val s by …` with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookEditScreen(
    bookId: String?,
    prefill: BookCandidate?,
    onDone: () -> Unit,
    vm: BookEditViewModel = hiltViewModel<BookEditViewModel, BookEditViewModel.Factory>(
        creationCallback = { it.create(bookId, prefill) },
    ),
) {
    val s by vm.state.collectAsStateWithLifecycle()
```

(b) Save action shows progress — replace `actions = { TextButton(vm::save) { Text("Save") } },` with:

```kotlin
            actions = { TextButton(vm::save, enabled = !s.saving) { Text(if (s.saving) "Saving…" else "Save") } },
```

(c) Cover preview — insert as the first child of the form `Column`, before `val f = s.form`:

```kotlin
            if (s.coverPreview != null) BookCover(s.form.title, s.coverPreview, Modifier.width(120.dp))
```

(d) Suggestion chips — insert directly after the `FlowRow { … }` that lists `s.categories`:

```kotlin
            if (s.suggestions.isNotEmpty()) {
                Text("Suggested", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.suggestions.forEach { name ->
                        AssistChip(onClick = { vm.addCategory(name) }, label = { Text("+ $name") })
                    }
                }
            }
```

(e) Imports to add:

```kotlin
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.ui.common.BookCover
```

- [ ] **Step 4: `ui/nav/AppNav.kt`** — update the `BookEditKey` entry:

```kotlin
                entry<BookEditKey> { key ->
                    BookEditScreen(bookId = key.id, prefill = key.prefill, onDone = { backStack.removeLastOrNull() })
                }
```

- [ ] **Step 5: Verify + commit**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL, tests PASS.
```powershell
git add app/src
git commit -m "feat(edit): prefill from lookup, cover preview, suggested categories"
```

---

### Task 7: Find-book screen (search + ISBN result)

**Files:** Create `ui/find/FindBookViewModel.kt`, `ui/find/FindBookScreen.kt`; Modify `ui/nav/AppNav.kt`

- [ ] **Step 1: `ui/find/FindBookViewModel.kt`**

```kotlin
package com.exc33ded.library.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.MetadataLookup
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class FindUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<BookCandidate> = emptyList(),
    val message: String? = null,
    /** Set when a single result is ready to open in the edit screen. */
    val picked: BookCandidate? = null,
    val notFoundIsbn: String? = null,
)

@HiltViewModel(assistedFactory = FindBookViewModel.Factory::class)
class FindBookViewModel @AssistedInject constructor(
    private val lookup: MetadataLookup,
    @Assisted private val isbn: String?,
) : ViewModel() {
    @AssistedFactory interface Factory { fun create(isbn: String?): FindBookViewModel }

    private val _state = MutableStateFlow(FindUiState())
    val state: StateFlow<FindUiState> = _state
    private var job: Job? = null

    init { isbn?.let { lookupIsbn(it) } }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.length < 2) return
        launchLoading {
            val r = lookup.search(q)
            _state.update { it.copy(results = r, message = if (r.isEmpty()) "No books found" else null) }
        }
    }

    fun pick(c: BookCandidate) = launchLoading {
        val d = lookup.details(c)
        _state.update { it.copy(picked = d) }
    }

    fun consumePicked() = _state.update { it.copy(picked = null) }

    private fun lookupIsbn(isbn: String) = launchLoading {
        val r = lookup.lookupIsbn(isbn)
        _state.update { if (r != null) it.copy(picked = r) else it.copy(notFoundIsbn = isbn, message = "No match for ISBN $isbn") }
    }

    private fun launchLoading(block: suspend () -> Unit) {
        job?.cancel()
        _state.update { it.copy(loading = true, message = null) }
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't reach the book services — check your connection") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
```

- [ ] **Step 2: `ui/find/FindBookScreen.kt`**

```kotlin
package com.exc33ded.library.ui.find

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.ui.common.BookCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindBookScreen(
    isbn: String?,
    onBack: () -> Unit,
    onPicked: (BookCandidate) -> Unit,
    vm: FindBookViewModel = hiltViewModel<FindBookViewModel, FindBookViewModel.Factory>(
        creationCallback = { it.create(isbn) },
    ),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.picked) { s.picked?.let { vm.consumePicked(); onPicked(it) } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (isbn != null) "ISBN $isbn" else "Find a book") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = s.query, onValueChange = vm::setQuery, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Title, author, or both") },
                trailingIcon = { IconButton(vm::search) { Icon(Icons.Filled.Search, "Search") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            )
            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            s.notFoundIsbn?.let { code ->
                OutlinedButton({ onPicked(BookCandidate(title = "", authors = "", isbn = code)) }) { Text("Enter it manually") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.results, key = { it.olWorkKey ?: (it.title + it.authors) }) { c ->
                    Row(
                        Modifier.fillMaxWidth().animateItem().clickable(enabled = !s.loading) { vm.pick(c) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BookCover(c.title, c.coverUrl, Modifier.width(56.dp))
                        Column {
                            Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(c.authors, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Wire in `ui/nav/AppNav.kt`** — add inside `entryProvider { }`:

```kotlin
                entry<FindBookKey> { key ->
                    FindBookScreen(
                        isbn = key.isbn,
                        onBack = { backStack.removeLastOrNull() },
                        onPicked = { c ->
                            backStack.removeLastOrNull() // replace Find with Edit so Back returns to the library
                            backStack.add(BookEditKey(prefill = c))
                        },
                    )
                }
```
Add import `com.exc33ded.library.ui.find.FindBookScreen`.

- [ ] **Step 4: Verify + commit**

Run: `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL
```powershell
git add app/src
git commit -m "feat(find): search Open Library/Google Books and open result in editor"
```

---

### Task 8: Add-book menu with ISBN scanner

**Files:** Modify `ui/nav/AppNav.kt`

Tap on the centre FAB opens a menu: **Scan ISBN**, **Search**, **Enter manually**. (Spec §7 says tap = scan, long-press = menu; a single menu is simpler and Phase 7 replaces it with the Expressive FAB menu.)

- [ ] **Step 1: Replace `BottomBar` and add the scanner helper**

Replace the private `BottomBar` composable and its call. The `AppNav` bottom bar call becomes:

```kotlin
                BottomBar(
                    current = top,
                    onSelect = { backStack.switchTo(it) },
                    onScan = { scanIsbn() },
                    onSearch = { backStack.add(FindBookKey()) },
                    onManual = { backStack.add(BookEditKey()) },
                )
```

and at the top of `AppNav()` (after `val top = …`) add:

```kotlin
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun scanIsbn() = scope.launch {
        val raw = try {
            GmsBarcodeScanning.getClient(
                context,
                GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).enableAutoZoom().build(),
            ).startScan().await().rawValue
        } catch (e: CancellationException) {
            null // user closed the scanner; the scope itself is not cancelled
        } catch (e: Exception) {
            snackbar.showSnackbar("Scanner unavailable — try Search instead"); null
        }
        val isbn = raw?.let(Isbn::normalize)
        when {
            raw == null -> Unit
            isbn == null -> snackbar.showSnackbar("That barcode isn't a book ISBN")
            else -> backStack.add(FindBookKey(isbn))
        }
    }
```

Add `snackbarHost = { SnackbarHost(snackbar) },` to the `Scaffold(...)` parameters.

New `BottomBar`:

```kotlin
@Composable
private fun BottomBar(
    current: NavKey?,
    onSelect: (NavKey) -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onManual: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    NavigationBar {
        Tab(current, LibraryKey, Icons.Filled.AutoStories, "Library", onSelect)
        Tab(current, FeedKey, Icons.Filled.RssFeed, "Feed", onSelect)
        // ponytail: plain FAB + DropdownMenu; Phase 7 swaps in the Expressive FAB menu and cookie shape.
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = { menu = true }) { Icon(Icons.Filled.Add, contentDescription = "Add book") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Scan ISBN") }, leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null) },
                    onClick = { menu = false; onScan() })
                DropdownMenuItem(text = { Text("Search") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    onClick = { menu = false; onSearch() })
                DropdownMenuItem(text = { Text("Enter manually") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menu = false; onManual() })
            }
        }
        Tab(current, StatsKey, Icons.Filled.Insights, "Stats", onSelect)
        Tab(current, ProfileKey, Icons.Filled.Person, "Profile", onSelect)
    }
}
```

- [ ] **Step 2: Imports to add to `AppNav.kt`**

```kotlin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.exc33ded.library.metadata.Isbn
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException
```

- [ ] **Step 3: Verify + commit**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL
```powershell
git add app/src
git commit -m "feat(add): FAB menu with ISBN scanner, search, manual entry"
```

---

### Task 9: Check on emulator + phone, push

- [ ] **Step 1:** Put `googleBooksKey=…` in `local.properties` (never committed), then `.\gradlew.bat installDebug`.
- [ ] **Step 2: Emulator checklist** (search path; the code scanner needs Play services and may not run in the emulator)
  1. + → Search → "yellowface kuang" → results with cover thumbnails appear.
  2. Tap Yellowface → editor opens prefilled (title, author, pages, description) with cover preview; "Suggested" chips show (e.g. Novel, Satire).
  3. Tap a suggestion → it becomes a selected category chip. Save → library tile shows the real cover.
  4. Search "ruskin bond great stories children" → finds "Great Stories for Children".
  5. Airplane mode → search shows "Couldn't reach the book services…"; no crash.
  6. Existing Phase 1 books (no cover) still show the text placeholder.
- [ ] **Step 3: Phone checklist** — + → Scan ISBN on any book with a barcode → editor opens prefilled; scanning a non-book barcode shows "That barcode isn't a book ISBN".
- [ ] **Step 4:** `git push`
