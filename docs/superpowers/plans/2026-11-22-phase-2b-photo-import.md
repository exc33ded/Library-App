# Phase 2b — Cover Photos & Batch Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Execution model:** each task is handed to a Claude **Sonnet** subagent with the full task text. The subagent may build and run tests (set `JAVA_HOME`/`ANDROID_HOME` as in `CLAUDE.md`) but must not commit. The main session reviews, re-runs verification, and commits.

**Goal:** Add books from a photo of the cover — one at a time with the camera, or many at once from the gallery (the user's 96 test photos). Each photo is read on-device (ML Kit OCR), turned into a search query, matched via `MetadataLookup`, and lands as a review card the user accepts, edits, or retries.

**Architecture:** A local-only Room table `import_queue` (DB v1→v2 auto-migration) holds one row per photo. A Hilt `ImportWorker` (WorkManager) loops over `PENDING` rows: `ImportProcessor` decodes/downscales the photo with `ImageDecoder` (applies EXIF rotation), runs ML Kit text recognition at 0°/90°/270° and keeps the best read, builds a query with the pure `buildQuery()`, searches, and stores the top candidate as JSON (`REVIEW`) or a reason (`FAILED`). The downscaled photo is kept as a thumbnail and as the cover fallback when no online cover exists. The camera path uses the system camera app (`ActivityResultContracts.TakePicture`) — no CameraX needed. Phase 3 will plug on-device AI into `ImportProcessor` behind the same `TextReader`-style seam.

**Tech Stack:** ML Kit Text Recognition v2 bundled `com.google.mlkit:text-recognition:16.0.1` · WorkManager `2.12.0` · `androidx.hilt:hilt-work` + `androidx.hilt:hilt-compiler` `1.4.0` · Photo Picker (`activity-compose` 1.13.0, already present). Versions pinned 2026-11-22.

**Known facts about the test photos** (`docs/Books-Photo/`, 96 JPEGs, 4096×3072, never committed): front covers only, no barcodes, often stored sideways, busy patterned backgrounds, display fonts, author sometimes larger than title, Indian editions. Open Library free-text `q=` search found "ruskin bond great stories children" and "murder at the bookstore sue minix".

---

## File map

```
gradle/libs.versions.toml, app/build.gradle.kts      + mlkit text, work, hilt-work
app/src/main/AndroidManifest.xml                      WorkManager default-initializer removal, FileProvider
app/src/main/res/xml/file_paths.xml                   FileProvider paths (camera output)
app/src/main/java/com/exc33ded/library/
  LibraryApp.kt                                       Configuration.Provider with HiltWorkerFactory
  data/db/ImportItemEntity.kt                         entity + ImportState
  data/db/ImportDao.kt
  data/db/AppDatabase.kt                              v2, AutoMigration(1, 2), importDao()
  imports/QueryBuilder.kt                             OcrLine + buildQuery() (pure)
  imports/PhotoReader.kt                              interfaces PhotoPrep, TextReader + Android impls
  imports/ImportProcessor.kt                          one item -> REVIEW / FAILED (testable with fakes)
  imports/ImportWorker.kt                             loops PENDING rows
  imports/ImportRepository.kt                         enqueue/retry/delete/markDone + schedules the worker
  di/ImportModule.kt                                  binds PhotoPrep/TextReader
  metadata/CoverStore.kt                              + copyLocal()
  ui/imports/ImportViewModel.kt
  ui/imports/ImportScreen.kt                          review cards
  ui/nav/Keys.kt                                      + ImportKey; FindBookKey(query); BookEditKey(importId)
  ui/nav/AppNav.kt                                    menu: Photo of cover / Import from gallery; ImportKey entry
  ui/find/FindBookViewModel.kt, FindBookScreen.kt     accept initial query
  ui/book/BookEditViewModel.kt, BookEditScreen.kt     importId: photo fallback cover + mark DONE
  ui/library/LibraryViewModel.kt, LibraryScreen.kt    "Review imports (N)" chip
app/src/test/java/com/exc33ded/library/
  imports/QueryBuilderTest.kt  imports/ImportProcessorTest.kt
tools/benchmark.py                                    compares imported results with docs/Books-Photo/labels.csv
```

---

### Task 1: Dependencies, WorkManager + Hilt wiring, FileProvider

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `LibraryApp.kt`; Create `app/src/main/res/xml/file_paths.xml`

- [ ] **Step 1: `gradle/libs.versions.toml`** — add under `[versions]`:

```toml
work = "2.12.0"
mlkitText = "16.0.1"
```
and under `[libraries]`:
```toml
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "androidxHilt" }
androidx-hilt-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "androidxHilt" }
mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkitText" }
androidx-work-testing = { module = "androidx.work:work-testing", version.ref = "work" }
```

- [ ] **Step 2: `app/build.gradle.kts` dependencies** — add:

```kotlin
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.mlkit.text.recognition)
```

- [ ] **Step 3: `LibraryApp.kt`** — replace the file:

```kotlin
package com.exc33ded.library

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LibraryApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

- [ ] **Step 4: Manifest** — add `xmlns:tools="http://schemas.android.com/tools"` to the `<manifest>` tag, and inside `<application>` add:

```xml
        <!-- WorkManager is initialised on demand with HiltWorkerFactory (LibraryApp). -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.files"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 5: `app/src/main/res/xml/file_paths.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="camera" path="camera/" />
</paths>
```

- [ ] **Step 6: Verify** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL.

---

### Task 2: Import queue table (DB v2)

**Files:** Create `data/db/ImportItemEntity.kt`, `data/db/ImportDao.kt`; Modify `data/db/AppDatabase.kt`

- [ ] **Step 1: `data/db/ImportItemEntity.kt`**

```kotlin
package com.exc33ded.library.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ImportState { PENDING, PROCESSING, REVIEW, FAILED, DONE }

/** Local-only work queue: one row per imported photo. Not part of backup. */
@Entity(tableName = "import_queue", indices = [Index("state")])
data class ImportItemEntity(
    @PrimaryKey val id: String,
    /** content:// URI from the photo picker, or file path from the camera. */
    val source: String,
    val state: ImportState = ImportState.PENDING,
    /** Downscaled copy in filesDir/imports/{id}.jpg — thumbnail and fallback cover. */
    val photoPath: String? = null,
    /** Query built from OCR text (also offered for manual search on failure). */
    val query: String? = null,
    /** BookCandidate as JSON when state == REVIEW. */
    val resultJson: String? = null,
    val error: String? = null,
    val createdAt: Long = 0,
)
```

- [ ] **Step 2: `data/db/ImportDao.kt`**

```kotlin
package com.exc33ded.library.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_queue WHERE state != 'DONE' ORDER BY createdAt")
    fun observeOpen(): Flow<List<ImportItemEntity>>

    @Query("SELECT * FROM import_queue WHERE id = :id")
    suspend fun get(id: String): ImportItemEntity?

    @Query("SELECT * FROM import_queue WHERE state = 'PENDING' ORDER BY createdAt LIMIT 1")
    suspend fun nextPending(): ImportItemEntity?

    /** Crash/stop recovery: anything left PROCESSING goes back to the queue. */
    @Query("UPDATE import_queue SET state = 'PENDING' WHERE state = 'PROCESSING'")
    suspend fun resetProcessing()

    @Insert suspend fun insertAll(items: List<ImportItemEntity>)
    @Update suspend fun update(item: ImportItemEntity)

    @Query("UPDATE import_queue SET state = 'DONE' WHERE id = :id")
    suspend fun markDone(id: String)

    @Query("DELETE FROM import_queue WHERE state = 'DONE'")
    suspend fun clearDone()
}
```

- [ ] **Step 3: `data/db/AppDatabase.kt`** — replace with:

```kotlin
package com.exc33ded.library.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, CategoryEntity::class, BookCategoryEntity::class, ImportItemEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun categoryDao(): CategoryDao
    abstract fun importDao(): ImportDao
}
```

- [ ] **Step 4: Verify** — `.\gradlew.bat testDebugUnitTest` → all existing tests PASS; `app/schemas/com.exc33ded.library.data.db.AppDatabase/2.json` is generated (commit it).

---

### Task 3: Query builder (pure)

**Files:** Create `imports/QueryBuilder.kt`; Test `imports/QueryBuilderTest.kt`

- [ ] **Step 1: Failing test** `app/src/test/java/com/exc33ded/library/imports/QueryBuilderTest.kt`

```kotlin
package com.exc33ded.library.imports

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryBuilderTest {
    @Test fun yellowface() = assertEquals(
        "yellowface rebecca f kuang",
        buildQuery(listOf(
            OcrLine("YELLOWFACE", 120),
            OcrLine("REBECCA F. KUANG", 40),
            OcrLine("Available Exclusively in the Indian Subcontinent", 20),
        )),
    )

    @Test fun murderAtTheBookstoreDropsTaglinesAndOrdersByHeight() = assertEquals(
        "murder bookstore sue minix at the",
        buildQuery(listOf(
            OcrLine("She can write the perfect murder mystery...", 20),
            OcrLine("But can she solve one in real life?", 20),
            OcrLine("Murder", 100),
            OcrLine("at the", 40),
            OcrLine("Bookstore", 100),
            OcrLine("SUE MINIX", 45),
        )),
    )

    @Test fun ruskinBondKeepsFourLines() = assertEquals(
        "ruskin bond great stories for children",
        buildQuery(listOf(
            OcrLine("RUSKIN", 150), OcrLine("BOND", 150),
            OcrLine("GREAT STORIES", 40), OcrLine("FOR CHILDREN", 40),
        )),
    )

    @Test fun noiseAndJunkOnly() = assertEquals(
        "",
        buildQuery(listOf(OcrLine("#1 NEW YORK TIMES BESTSELLING AUTHOR", 30), OcrLine("@ 7", 50))),
    )

    @Test fun capsAtTenWords() = assertEquals(
        10,
        buildQuery(listOf(OcrLine("one two three four five six", 50), OcrLine("seven eight nine ten eleven twelve", 40)))
            .split(' ').size,
    )

    @Test fun lettersCountedForRotationChoice() = assertEquals(9, letterCount(listOf(OcrLine("Dune 2!", 10), OcrLine("Herb", 5))))
}
```

Rules encoded by these tests: lines are ranked by height (tallest first, stable for ties); lines matching noise patterns or longer than 6 words are dropped; lines with fewer than 2 letters are dropped; top 4 lines are kept; text is lower-cased, everything except letters/digits/spaces/apostrophes removed, whitespace collapsed; capped at 10 words.

- [ ] **Step 2: Run** `.\gradlew.bat testDebugUnitTest --tests "com.exc33ded.library.imports.*"` → FAIL (unresolved).

- [ ] **Step 3: Implement** `app/src/main/java/com/exc33ded/library/imports/QueryBuilder.kt`

```kotlin
package com.exc33ded.library.imports

/** One recognised text line and its on-image height in px (bigger = more likely title/author). */
data class OcrLine(val text: String, val height: Int)

private val NOISE = Regex(
    "bestsell|best-sell|a novel|available exclusively|subcontinent|author of|winner|edition|" +
        "copyright|www\\.|\\.com|praise|times|international|now a major|shortlisted|longlisted|award",
    RegexOption.IGNORE_CASE,
)

// ponytail: height-ranked heuristic; Phase 3's vision model can replace it where it fails.
fun buildQuery(lines: List<OcrLine>): String = lines
    .asSequence()
    .filter { l -> l.text.count { it.isLetter() } >= 2 }
    .filterNot { NOISE.containsMatchIn(it.text) }
    .filter { it.text.trim().split(Regex("\\s+")).size <= 6 }
    .sortedByDescending { it.height }
    .take(4)
    .map { l -> l.text.lowercase().replace(Regex("[^\\p{L}\\p{N}' ]"), " ") }
    .flatMap { it.split(Regex("\\s+")).asSequence() }
    .filter { it.isNotBlank() }
    .take(10)
    .joinToString(" ")

fun letterCount(lines: List<OcrLine>): Int = lines.sumOf { l -> l.text.count { it.isLetter() } }
```

- [ ] **Step 4: Run** the same command → PASS (6 tests).

---

### Task 4: Photo prep + OCR (Android), processor (testable)

**Files:** Create `imports/PhotoReader.kt`, `imports/ImportProcessor.kt`, `di/ImportModule.kt`; Modify `metadata/CoverStore.kt`; Test `imports/ImportProcessorTest.kt`

- [ ] **Step 1: Failing test** `app/src/test/java/com/exc33ded/library/imports/ImportProcessorTest.kt`

```kotlin
package com.exc33ded.library.imports

import com.exc33ded.library.data.db.ImportItemEntity
import com.exc33ded.library.data.db.ImportState
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.BookSource
import com.exc33ded.library.metadata.MetadataLookup
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakePrep : PhotoPrep {
    override suspend fun prepare(source: String, id: String) = "/photos/$id.jpg"
}
private class FakeReader(val lines: List<OcrLine>) : TextReader {
    override suspend fun read(photoPath: String) = lines
}
private class FakeSource(val results: Map<String, List<BookCandidate>> = emptyMap(), val fail: Boolean = false) : BookSource {
    val queries = mutableListOf<String>()
    override suspend fun byIsbn(isbn: String): BookCandidate? = null
    override suspend fun search(query: String, limit: Int): List<BookCandidate> {
        queries += query
        if (fail) throw IOException("offline")
        return results[query].orEmpty()
    }
}

class ImportProcessorTest {
    private val item = ImportItemEntity(id = "i1", source = "content://x", state = ImportState.PROCESSING)
    private val yellowLines = listOf(OcrLine("YELLOWFACE", 120), OcrLine("REBECCA F. KUANG", 40))
    private val yf = BookCandidate("Yellowface", "R. F. Kuang")

    private fun processor(lines: List<OcrLine>, ol: FakeSource, gb: FakeSource = FakeSource()) =
        ImportProcessor(FakePrep(), FakeReader(lines), MetadataLookup(ol, gb))

    @Test fun matchGoesToReviewWithCandidateJson() = runTest {
        val r = processor(yellowLines, FakeSource(mapOf("yellowface rebecca f kuang" to listOf(yf)))).process(item)
        assertEquals(ImportState.REVIEW, r.state)
        assertEquals("/photos/i1.jpg", r.photoPath)
        assertEquals("Yellowface", Json.decodeFromString<BookCandidate>(r.resultJson!!).title)
    }

    @Test fun retriesWithTopTwoLinesWhenFullQueryFindsNothing() = runTest {
        val lines = yellowLines + OcrLine("Some Publisher", 10)
        val ol = FakeSource(mapOf("yellowface rebecca f kuang" to listOf(yf)))
        val r = processor(lines, ol).process(item)
        assertEquals(ImportState.REVIEW, r.state)
        assertEquals(listOf("yellowface rebecca f kuang some publisher", "yellowface rebecca f kuang"), ol.queries)
    }

    @Test fun noTextFails() = runTest {
        val r = processor(emptyList(), FakeSource()).process(item)
        assertEquals(ImportState.FAILED, r.state)
        assertEquals("No readable text on the cover", r.error)
    }

    @Test fun noMatchFailsButKeepsQueryForManualSearch() = runTest {
        val r = processor(yellowLines, FakeSource()).process(item)
        assertEquals(ImportState.FAILED, r.state)
        assertEquals("yellowface rebecca f kuang", r.query)
        assertTrue(r.error!!.startsWith("No match"))
    }

    @Test fun offlineFailsWithRetryableMessage() = runTest {
        val r = processor(yellowLines, FakeSource(fail = true), FakeSource(fail = true)).process(item)
        assertEquals(ImportState.FAILED, r.state)
        assertEquals("Couldn't reach the book services", r.error)
    }
}
```

- [ ] **Step 2: Run** `.\gradlew.bat testDebugUnitTest --tests "com.exc33ded.library.imports.*"` → FAIL (unresolved).

- [ ] **Step 3: `imports/PhotoReader.kt`**

```kotlin
package com.exc33ded.library.imports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max

interface PhotoPrep {
    /** Decode (EXIF-rotated), downscale, save to filesDir/imports/{id}.jpg; returns that path. */
    suspend fun prepare(source: String, id: String): String
}

interface TextReader {
    suspend fun read(photoPath: String): List<OcrLine>
}

class AndroidPhotoPrep @Inject constructor(@ApplicationContext private val ctx: Context) : PhotoPrep {
    override suspend fun prepare(source: String, id: String): String = withContext(Dispatchers.IO) {
        val src = if (source.startsWith("content://")) ImageDecoder.createSource(ctx.contentResolver, Uri.parse(source))
        else ImageDecoder.createSource(File(source))
        // ImageDecoder applies EXIF orientation itself.
        val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_EDGE) {
                val s = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = File(ctx.filesDir, "imports/$id.jpg").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        out.absolutePath
    }

    private companion object { const val MAX_EDGE = 1600 }
}

/** ML Kit Latin OCR. Tries 0°, 90°, 270° (photos are often sideways) and keeps the read with most letters. */
class MlKitTextReader @Inject constructor() : TextReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun read(photoPath: String): List<OcrLine> {
        val bmp = withContext(Dispatchers.IO) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(photoPath))) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        try {
            return listOf(0, 90, 270)
                .map { rot -> recognize(bmp, rot) }
                .maxBy { letterCount(it) }
        } finally {
            bmp.recycle()
        }
    }

    private suspend fun recognize(bmp: Bitmap, rotation: Int): List<OcrLine> {
        val text = recognizer.process(InputImage.fromBitmap(bmp, rotation)).await()
        return text.textBlocks.flatMap { it.lines }.map { line ->
            val box = line.boundingBox
            // Height in the rotated frame = the shorter side of the line's box.
            OcrLine(line.text, box?.let { minOf(it.width(), it.height()) } ?: 0)
        }
    }
}
```

- [ ] **Step 4: `imports/ImportProcessor.kt`**

```kotlin
package com.exc33ded.library.imports

import com.exc33ded.library.data.db.ImportItemEntity
import com.exc33ded.library.data.db.ImportState
import com.exc33ded.library.metadata.MetadataLookup
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Turns one queued photo into a REVIEW (with candidate) or FAILED item. Never throws except on cancellation. */
class ImportProcessor @Inject constructor(
    private val prep: PhotoPrep,
    private val reader: TextReader,
    private val lookup: MetadataLookup,
) {
    suspend fun process(item: ImportItemEntity): ImportItemEntity {
        val photo = try {
            item.photoPath ?: prep.prepare(item.source, item.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return item.copy(state = ImportState.FAILED, error = "Couldn't open the photo")
        }
        val withPhoto = item.copy(photoPath = photo)
        val lines = reader.read(photo)
        val query = buildQuery(lines)
        if (query.isBlank()) return withPhoto.copy(state = ImportState.FAILED, error = "No readable text on the cover")

        val shortQuery = buildQuery(lines.sortedByDescending { it.height }.take(2))
        return try {
            val hit = lookup.search(query).firstOrNull()
                ?: shortQuery.takeIf { it != query }?.let { lookup.search(it).firstOrNull() }
            if (hit == null) {
                withPhoto.copy(state = ImportState.FAILED, query = query, error = "No match for \"$query\"")
            } else {
                withPhoto.copy(state = ImportState.REVIEW, query = query, resultJson = Json.encodeToString(lookup.details(hit)), error = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withPhoto.copy(state = ImportState.FAILED, query = query, error = "Couldn't reach the book services")
        }
    }
}
```

- [ ] **Step 5: `di/ImportModule.kt`**

```kotlin
package com.exc33ded.library.di

import com.exc33ded.library.imports.AndroidPhotoPrep
import com.exc33ded.library.imports.MlKitTextReader
import com.exc33ded.library.imports.PhotoPrep
import com.exc33ded.library.imports.TextReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportModule {
    @Binds abstract fun photoPrep(impl: AndroidPhotoPrep): PhotoPrep
    @Binds abstract fun textReader(impl: MlKitTextReader): TextReader
}
```

- [ ] **Step 6: `metadata/CoverStore.kt`** — add this method to the class:

```kotlin
    /** Use the user's own photo as the cover (no online cover found). */
    suspend fun copyLocal(path: String, bookId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            File(path).copyTo(File(ctx.filesDir, "covers/$bookId.jpg").apply { parentFile?.mkdirs() }, overwrite = true).absolutePath
        }.getOrNull()
    }
```

- [ ] **Step 7: Run** `.\gradlew.bat testDebugUnitTest` → PASS (5 new processor tests, all older tests).

---

### Task 5: Repository + worker

**Files:** Create `imports/ImportRepository.kt`, `imports/ImportWorker.kt`; Modify `di/DatabaseModule.kt`

- [ ] **Step 1: `di/DatabaseModule.kt`** — add the DAO provider and register the migration-safe builder (no change to builder needed for AutoMigration):

```kotlin
    @Provides fun importDao(db: AppDatabase) = db.importDao()
```

- [ ] **Step 2: `imports/ImportRepository.kt`**

```kotlin
package com.exc33ded.library.imports

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.exc33ded.library.data.Ids
import com.exc33ded.library.data.db.ImportDao
import com.exc33ded.library.data.db.ImportItemEntity
import com.exc33ded.library.data.db.ImportState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: ImportDao,
) {
    fun observeOpen() = dao.observeOpen()

    /** Picker URIs: keep read access so the worker can open them later. */
    suspend fun enqueueUris(uris: List<Uri>) {
        uris.forEach { runCatching { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        enqueue(uris.map { it.toString() })
    }

    suspend fun enqueue(sources: List<String>) {
        if (sources.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.insertAll(sources.mapIndexed { i, s -> ImportItemEntity(id = Ids.new(), source = s, createdAt = now + i) })
        schedule()
    }

    suspend fun retry(id: String) {
        dao.get(id)?.let { dao.update(it.copy(state = ImportState.PENDING, error = null, resultJson = null)) }
        schedule()
    }

    suspend fun markDone(id: String) = dao.markDone(id)
    suspend fun get(id: String) = dao.get(id)
    suspend fun clearDone() = dao.clearDone()

    private fun schedule() {
        // APPEND_OR_REPLACE: a run that is finishing won't swallow newly queued items.
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "imports", ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<ImportWorker>().build(),
        )
    }
}
```

- [ ] **Step 3: `imports/ImportWorker.kt`**

```kotlin
package com.exc33ded.library.imports

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.exc33ded.library.data.db.ImportDao
import com.exc33ded.library.data.db.ImportState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Processes PENDING rows one at a time (sequential = natural Open Library rate limiting).
 * ponytail: plain (non-foreground) worker; if the OS stops it at its time limit, the next run
 * resumes from the queue (resetProcessing). Add a foreground notification if that proves annoying.
 */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val dao: ImportDao,
    private val processor: ImportProcessor,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        dao.resetProcessing()
        while (true) {
            val item = dao.nextPending() ?: break
            dao.update(item.copy(state = ImportState.PROCESSING))
            dao.update(processor.process(item))
        }
        return Result.success()
    }
}
```

- [ ] **Step 4: Verify** — `.\gradlew.bat testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL.

---

### Task 6: Review screen

**Files:** Create `ui/imports/ImportViewModel.kt`, `ui/imports/ImportScreen.kt`; Modify `ui/nav/Keys.kt`

- [ ] **Step 1: `ui/nav/Keys.kt`** — add `ImportKey`, a `query` to `FindBookKey`, an `importId` to `BookEditKey`:

```kotlin
@Serializable data class BookEditKey(val id: String? = null, val prefill: BookCandidate? = null, val importId: String? = null) : NavKey
@Serializable data class FindBookKey(val isbn: String? = null, val query: String? = null, val importId: String? = null) : NavKey
@Serializable data object ImportKey : NavKey
```

- [ ] **Step 2: `ui/imports/ImportViewModel.kt`**

```kotlin
package com.exc33ded.library.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.data.Ids
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ImportItemEntity
import com.exc33ded.library.imports.ImportRepository
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.CoverStore
import com.exc33ded.library.metadata.suggestCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ImportRow(val item: ImportItemEntity, val candidate: BookCandidate?)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val imports: ImportRepository,
    private val books: BookRepository,
    private val covers: CoverStore,
) : ViewModel() {
    val rows = imports.observeOpen()
        .map { list -> list.map { ImportRow(it, it.resultJson?.let { j -> runCatching { Json.decodeFromString<BookCandidate>(j) }.getOrNull() }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(id: String) = viewModelScope.launch { imports.retry(id) }
    fun skip(id: String) = viewModelScope.launch { imports.markDone(id) }

    /** One-tap add: online cover, else the user's photo; existing categories that match subjects. */
    fun add(row: ImportRow) = viewModelScope.launch {
        val c = row.candidate ?: return@launch
        val id = Ids.new()
        val cover = c.coverUrl?.let { covers.download(it, id) } ?: row.item.photoPath?.let { covers.copyLocal(it, id) }
        val cats = books.observeCategories().first()
        val names = suggestCategories(c.subjects, cats.map { it.name })
        val catIds = cats.filter { cat -> names.any { it.equals(cat.name, ignoreCase = true) } }.map { it.id }.toSet()
        books.saveBook(
            BookEntity(
                id = id, title = c.title, authors = c.authors, isbn = c.isbn, description = c.description,
                pageCount = c.pageCount, olWorkKey = c.olWorkKey, coverPath = cover,
            ),
            catIds,
        )
        imports.markDone(row.item.id)
    }
}
```

- [ ] **Step 3: `ui/imports/ImportScreen.kt`**

```kotlin
package com.exc33ded.library.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.data.db.ImportState
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.ui.common.BookCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onEdit: (importId: String, BookCandidate) -> Unit,
    onSearch: (importId: String, query: String?) -> Unit,
    vm: ImportViewModel = hiltViewModel(),
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val waiting = rows.count { it.item.state == ImportState.PENDING || it.item.state == ImportState.PROCESSING }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (waiting > 0) "Reading covers… $waiting left" else "Review imports") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        if (rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing to review")
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rows, key = { it.item.id }) { row ->
                ElevatedCard(Modifier.fillMaxWidth().animateItem()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BookCover("", row.item.photoPath, Modifier.width(64.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (row.item.state) {
                                ImportState.PENDING, ImportState.PROCESSING -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("  Reading cover…", style = MaterialTheme.typography.bodyMedium)
                                }
                                ImportState.REVIEW -> row.candidate?.let { c ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (c.coverUrl != null) BookCover(c.title, c.coverUrl, Modifier.width(44.dp))
                                        Column {
                                            Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(c.authors, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button({ vm.add(row) }) { Text("Add") }
                                        OutlinedButton({ onEdit(row.item.id, c) }) { Text("Edit") }
                                        TextButton({ onSearch(row.item.id, row.item.query) }) { Text("Wrong book") }
                                    }
                                }
                                ImportState.FAILED -> {
                                    Text(row.item.error ?: "Failed", style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton({ onSearch(row.item.id, row.item.query) }) { Text("Search") }
                                        TextButton({ vm.retry(row.item.id) }) { Text("Retry") }
                                        TextButton({ vm.skip(row.item.id) }) { Text("Skip") }
                                    }
                                }
                                ImportState.DONE -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Verify** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL.

---

### Task 7: Wire entry points, Find query prefill, edit-from-import

**Files:** Modify `ui/nav/AppNav.kt`, `ui/find/FindBookViewModel.kt`, `ui/find/FindBookScreen.kt`, `ui/book/BookEditViewModel.kt`, `ui/book/BookEditScreen.kt`, `ui/library/LibraryViewModel.kt`, `ui/library/LibraryScreen.kt`

- [ ] **Step 1: Find screen accepts a starting query**

`FindBookViewModel`: change the factory and init:
```kotlin
    @AssistedFactory interface Factory { fun create(isbn: String?, query: String?): FindBookViewModel }
```
constructor gains `@Assisted("query") private val initialQuery: String?` and the existing ISBN param becomes `@Assisted("isbn") private val isbn: String?` (two assisted Strings need distinct identifiers; annotate the factory parameters the same way: `fun create(@Assisted("isbn") isbn: String?, @Assisted("query") query: String?)`). Replace `init { … }` with:
```kotlin
    init {
        when {
            isbn != null -> lookupIsbn(isbn)
            !initialQuery.isNullOrBlank() -> { _state.update { it.copy(query = initialQuery) }; search() }
        }
    }
```
`FindBookScreen`: add parameter `query: String?` after `isbn`, and change the creation callback to `{ it.create(isbn, query) }`.

- [ ] **Step 2: Edit screen knows its import**

`BookEditViewModel`: add `private val imports: ImportRepository` to the constructor, change the factory to
```kotlin
    @AssistedFactory interface Factory { fun create(bookId: String?, prefill: BookCandidate?, importId: String?): BookEditViewModel }
```
with constructor param `@Assisted private val importId: String?` (types differ from the other assisted params except `bookId: String?` — so annotate: `@Assisted("bookId") bookId: String?`, `@Assisted("importId") importId: String?`, and the same identifiers on the factory parameters). In `save()`, replace the cover line and add the done-marking:
```kotlin
            val remote = s.coverPreview?.takeIf { it.startsWith("http") }
            val photo = importId?.let { imports.get(it)?.photoPath }
            val coverPath = remote?.let { covers.download(it, original.id) }
                ?: photo?.let { covers.copyLocal(it, original.id) }
                ?: original.coverPath
            val id = repo.saveBook(s.form.applyTo(original).copy(coverPath = coverPath), s.selectedCategories)
            importId?.let { imports.markDone(it) }
```
Also, if `prefill?.coverUrl == null` and `importId != null`, set `coverPreview` to the import's `photoPath` during init (inside the first `viewModelScope.launch`, after the prefill block):
```kotlin
            if (prefill?.coverUrl == null && importId != null) {
                imports.get(importId)?.photoPath?.let { p -> _state.update { it.copy(coverPreview = p) } }
            }
```
`BookEditScreen`: add parameter `importId: String?` after `prefill`; creation callback `{ it.create(bookId, prefill, importId) }`.

- [ ] **Step 3: `ui/nav/AppNav.kt`**

(a) Above `Scaffold(`, add the two launchers (imports: `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.PickVisualMediaRequest`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.core.content.FileProvider`, `java.io.File`, `com.exc33ded.library.imports.ImportRepository`, `dagger.hilt.android.EntryPointAccessors` is NOT needed — use a tiny Hilt ViewModel instead, see (b)):
```kotlin
    val navVm: AppNavViewModel = hiltViewModel()
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) { navVm.import(uris); backStack.add(ImportKey) }
    }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) { navVm.importFile(f.absolutePath); backStack.add(ImportKey) }
    }
    fun launchCamera() {
        val f = File(context.filesDir, "camera/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        cameraFile = f
        takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", f))
    }
```
(b) Create `ui/nav/AppNavViewModel.kt`:
```kotlin
package com.exc33ded.library.ui.nav

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.imports.ImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppNavViewModel @Inject constructor(private val imports: ImportRepository) : ViewModel() {
    fun import(uris: List<Uri>) = viewModelScope.launch { imports.enqueueUris(uris) }
    fun importFile(path: String) = viewModelScope.launch { imports.enqueue(listOf(path)) }
}
```
(c) `BottomBar` gets two more callbacks `onPhoto: () -> Unit, onGallery: () -> Unit` and two more `DropdownMenuItem`s placed first in the menu:
```kotlin
                DropdownMenuItem(text = { Text("Photo of cover") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                    onClick = { menu = false; onPhoto() })
                DropdownMenuItem(text = { Text("Import from gallery") }, leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) },
                    onClick = { menu = false; onGallery() })
```
and the call site passes `onPhoto = { launchCamera() }, onGallery = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }`.

(d) Entries — update/add inside `entryProvider { }`:
```kotlin
                entry<BookEditKey> { key ->
                    BookEditScreen(bookId = key.id, prefill = key.prefill, importId = key.importId, onDone = { backStack.removeLastOrNull() })
                }
                entry<FindBookKey> { key ->
                    FindBookScreen(
                        isbn = key.isbn,
                        query = key.query,
                        onBack = { backStack.removeLastOrNull() },
                        onPicked = { c ->
                            backStack.removeLastOrNull()
                            backStack.add(BookEditKey(prefill = c, importId = key.importId))
                        },
                    )
                }
                entry<ImportKey> {
                    ImportScreen(
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { id, c -> backStack.add(BookEditKey(prefill = c, importId = id)) },
                        onSearch = { id, q -> backStack.add(FindBookKey(query = q, importId = id)) },
                    )
                }
```
Extra imports: `androidx.compose.material.icons.filled.PhotoCamera`, `androidx.compose.material.icons.filled.PhotoLibrary`, `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`, `com.exc33ded.library.ui.imports.ImportScreen`.

- [ ] **Step 4: Library shows open imports**

`LibraryViewModel`: inject `imports: ImportRepository`; add
```kotlin
    val openImports: StateFlow<Int> = imports.observeOpen().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
```
(import `kotlinx.coroutines.flow.map`). `LibraryScreen`: add parameter `onOpenImports: () -> Unit`; collect `val pending by vm.openImports.collectAsStateWithLifecycle()`; as the first item of the category `LazyRow` (before "All"):
```kotlin
            if (pending > 0) item { AssistChip(onClick = onOpenImports, label = { Text("Review imports ($pending)") }) }
```
(import `androidx.compose.material3.AssistChip`). In `AppNav`, pass `onOpenImports = { backStack.add(ImportKey) }` to `LibraryScreen`.

- [ ] **Step 5: Verify** — `.\gradlew.bat testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL, all tests PASS.

---

### Task 8: Benchmark on the 96 photos (emulator) and push

**Files:** Create `tools/benchmark.py`; uses `docs/Books-Photo/labels.csv` (confirmed by the user; gitignored with the photos)

- [ ] **Step 1: `tools/benchmark.py`**

```python
"""Compare imported results with ground-truth labels.

Usage: python -I tools/benchmark.py <pulled library.db> docs/Books-Photo/labels.csv
Pull the DB first:  adb exec-out run-as com.exc33ded.library cat databases/library.db > library.db
(also pull library.db-wal if present, or close the app first so the WAL is checkpointed)
"""
import csv, json, re, sqlite3, sys
from urllib.parse import unquote

def norm(s):
    return re.sub(r"[^a-z0-9 ]", " ", (s or "").lower()).split()

def title_ok(found, truth):
    f, t = set(norm(found)), norm(truth)
    return bool(t) and sum(w in f for w in t) / len(t) >= 0.6

def main(db_path, labels_path):
    labels = {r["file"]: r for r in csv.DictReader(open(labels_path, encoding="utf-8"))}
    rows = sqlite3.connect(db_path).execute("SELECT source, state, resultJson, error FROM import_queue").fetchall()
    ok = review = failed = 0
    misses = []
    for source, state, result, error in rows:
        name = unquote(source).rsplit("/", 1)[-1]
        truth = next((v for k, v in labels.items() if k.split(".")[0] in name), None)
        if state == "FAILED":
            failed += 1; misses.append((name, "FAILED", error)); continue
        review += 1
        cand = json.loads(result) if result else {}
        if truth and title_ok(cand.get("title"), truth["title"]):
            ok += 1
        else:
            misses.append((name, cand.get("title"), truth and truth["title"]))
    total = len(rows)
    print(f"photos={total} matched={ok} ({ok/total:.0%}) wrong={review-ok} failed={failed}")
    for m in misses:
        print("  MISS", *m, sep=" | ")

if __name__ == "__main__":
    main(*sys.argv[1:3])
```

Note: picker URIs don't always carry the original file name; if `name` matching fails for most rows, push the photos with their names to `/sdcard/Pictures/books/` and compare by import order instead (picker returns them in gallery order).

- [ ] **Step 2: Run on the emulator**

```powershell
adb push docs/Books-Photo/. /sdcard/Pictures/books/
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/books/
```
In the app: + → Import from gallery → select all 96 → wait until "Reading covers… 0 left". Then:
```powershell
adb shell am force-stop com.exc33ded.library
adb exec-out run-as com.exc33ded.library cat databases/library.db > $env:TEMP\library.db
python -I tools/benchmark.py $env:TEMP\library.db docs/Books-Photo/labels.csv
```
Expected: prints `photos=96 matched=… failed=…` and the miss list. Record the numbers in the spec §11 de-risk note; they decide how much Phase 3 AI is needed.

- [ ] **Step 3: Checklist**
  1. + → Photo of cover (emulator camera shows a virtual scene; use the phone for real covers) → Review screen shows the item processing, then a card.
  2. Review card: **Add** saves with cover; **Edit** opens prefilled editor and, on save, the card disappears; **Wrong book** opens Find with the OCR query already searched.
  3. A failed card offers Search / Retry / Skip.
  4. Library shows "Review imports (N)" while items are open.
  5. Kill the app mid-import → reopen → the queue continues (worker resumes PENDING/PROCESSING items).

- [ ] **Step 4: Push** — `git push`.
