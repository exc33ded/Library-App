# Model Manager + Storage Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **Workers:** the main session is the head: it hands each task to a worker, reviews the diff, re-runs verification and commits. **Tasks 1–2 → opencode** (`opencode run --auto -m opencode/hy4-flash-free`), treated as an intern: it gets the task text verbatim and must stop and report on the first failure. **Tasks 3–4 → Claude subagent on Sonnet.** Workers never commit. Never give any worker `local.properties` or anything under `docs/Books-Photo/`.

**Goal:** Settings → "Models & storage" works: choose where models are stored, see free space and usage, download optional AI models (free, ungated), cancel/resume/delete them; plus a one-tap ISBN scan button on the Library toolbar.

**Architecture:** Two new `AppSettings` fields (`modelsLocation`, `wifiOnlyDownloads`) in the existing DataStore. Pure helpers (`ModelCatalog`, file/size/hash helpers, location resolver) are unit-tested. `ModelDownloader` (Ktor, HTTP Range resume) is driven by a Hilt `ModelDownloadWorker` (WorkManager, foreground `dataSync` service) and observed through `ModelRepository`. UI is one new screen reached from the Settings row.

**Tech Stack:** Kotlin, WorkManager (already a dependency, Hilt worker factory already configured in `LibraryApp`), Ktor client (OkHttp engine, MockEngine in tests), DataStore, Compose Material 3.

Spec: `docs/superpowers/specs/2026-11-22-model-manager-and-storage-design.md`.

## Toolchain (each PowerShell call)

```powershell
$env:JAVA_HOME="$env:USERPROFILE\scoop\apps\temurin21-jdk\current"
$env:ANDROID_HOME="$env:USERPROFILE\scoop\apps\android-clt\current"
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue   # preset value has no Gradle distribution
cd D:\claude-exp\words-and-wounds
```

Paths: main = `app/src/main/java/com/exc33ded/library/`, test = `app/src/test/java/com/exc33ded/library/`. Baseline before this plan: 103 unit tests pass (1 skipped). Never write the two characters slash-star inside a Kotlin comment (e.g. `dir/*.jpg`) — Kotlin reads it as a nested comment.

---

### Task 1: Two new settings (worker: opencode)

**Files (edit existing):** `settings/AppSettings.kt`, `settings/SettingsRepository.kt`, test `settings/AppSettingsTest.kt`, test `settings/SettingsRepositoryTest.kt`.

- [ ] **Step 1: Failing tests.** In `AppSettingsTest.kt`, inside `defaultsMatchSpec()` append before the closing brace:

```kotlin
        assertEquals(ModelsLocation.PHONE, d.modelsLocation)
        assertEquals(true, d.wifiOnlyDownloads)
```
In `SettingsRepositoryTest.kt` add a new test inside the class (it performs ONE update, which is fine on Windows):

```kotlin
    @Test fun modelsLocationAndWifiOnlyPersist() = runBlocking {
        val r = repo()
        r.update { it.copy(modelsLocation = ModelsLocation.SD_CARD, wifiOnlyDownloads = false) }
        val s = r.settings.first()
        assertEquals(ModelsLocation.SD_CARD, s.modelsLocation)
        assertEquals(false, s.wifiOnlyDownloads)
    }
```
Run `.\gradlew.bat testDebugUnitTest --tests "*.AppSettingsTest" --tests "*.SettingsRepositoryTest"` → expect FAIL (unresolved `ModelsLocation`).

- [ ] **Step 2: `settings/AppSettings.kt`.** Add below the existing `LibraryDisplayMode` enum:

```kotlin
enum class ModelsLocation { PHONE, APP_FOLDER, SD_CARD }
```
and add two fields at the END of the `AppSettings` data class parameter list (after `showStatusBadge`):

```kotlin
    val modelsLocation: ModelsLocation = ModelsLocation.PHONE,
    val wifiOnlyDownloads: Boolean = true,
```

- [ ] **Step 3: `settings/SettingsRepository.kt`.**
  - In `update`, after `p[STATUS] = n.showStatusBadge` add:
    ```kotlin
            p[MODELS_LOC] = n.modelsLocation.name
            p[WIFI_ONLY] = n.wifiOnlyDownloads
    ```
  - In `read`, add to the `AppSettings(...)` constructor call, after `showStatusBadge = ...,`:
    ```kotlin
            modelsLocation = p[MODELS_LOC]?.let { runCatching { ModelsLocation.valueOf(it) }.getOrNull() } ?: d.modelsLocation,
            wifiOnlyDownloads = p[WIFI_ONLY] ?: d.wifiOnlyDownloads,
    ```
  - In the `companion object`, after `val STATUS = ...` add:
    ```kotlin
        val MODELS_LOC = stringPreferencesKey("models_location")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
    ```

- [ ] **Step 4: Run, expect PASS** (same command), then the whole suite `.\gradlew.bat testDebugUnitTest` (all pass). Do NOT commit; main session commits `feat(settings): models location and wifi-only settings`.

---

### Task 2: Catalog and pure helpers (worker: opencode)

**Files (all new):** `models/ModelCatalog.kt`, `models/ModelFiles.kt`, `models/Format.kt`, `storage/StorageRoots.kt` (pure functions only in this task), and tests `models/ModelCatalogTest.kt`, `models/ModelFilesTest.kt`, `storage/StorageRootsTest.kt`.

- [ ] **Step 1: Write the tests.**

`models/ModelCatalogTest.kt`:
```kotlin
package com.exc33ded.library.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test fun idsAreUnique() = assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.id }.toSet().size)

    @Test fun everyEntryIsWellFormed() = ModelCatalog.all.forEach { m ->
        assertTrue(m.id, m.url.startsWith("https://huggingface.co/") && m.url.endsWith(m.fileName))
        assertTrue(m.id, Regex("[0-9a-f]{64}").matches(m.sha256))
        assertTrue(m.id, m.sizeBytes > 100_000_000L)
        assertTrue(m.id, m.fileName.endsWith(".litertlm"))
    }

    @Test fun exactlyOneRecommendedAndItIsText() {
        val rec = ModelCatalog.all.filter { it.recommended }
        assertEquals(1, rec.size)
        assertEquals(ModelKind.TEXT, rec.single().kind)
    }

    @Test fun lookupById() {
        assertEquals("Qwen3 0.6B", ModelCatalog.byId("qwen3-0.6b")?.name)
        assertNull(ModelCatalog.byId("nope"))
    }
}
```

`models/ModelFilesTest.kt`:
```kotlin
package com.exc33ded.library.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val spec = ModelSpec("t", "T", "d", ModelKind.TEXT, "r/x", "f.litertlm", 5L, "0".repeat(64))

    @Test fun sha256OfKnownBytes() {
        val f = File(tmp.root, "abc.bin").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex(f))
    }

    @Test fun installedOnlyWhenSizeMatches() {
        assertFalse(isInstalled(tmp.root, spec))
        finalFile(tmp.root, spec).writeText("1234")        // 4 bytes, expected 5
        assertFalse(isInstalled(tmp.root, spec))
        finalFile(tmp.root, spec).writeText("12345")
        assertTrue(isInstalled(tmp.root, spec))
    }

    @Test fun partFileHasPartSuffix() = assertEquals("f.litertlm.part", partFile(tmp.root, spec).name)

    @Test fun dirSizeSumsNestedFilesAndHandlesMissingDir() {
        File(tmp.root, "a").apply { mkdirs() }.let { File(it, "x").writeText("12") }
        File(tmp.root, "y").writeText("345")
        assertEquals(5L, dirSize(tmp.root))
        assertEquals(0L, dirSize(File(tmp.root, "missing")))
    }

    @Test fun formatBytes() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("12 KB", formatBytes(12_000))
        assertEquals("343 MB", formatBytes(343_490_560))
        assertEquals("2.6 GB", formatBytes(2_588_147_712))
    }
}
```

`storage/StorageRootsTest.kt`:
```kotlin
package com.exc33ded.library.storage

import com.exc33ded.library.settings.ModelsLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class StorageRootsTest {
    private val files = File("/data/files")
    private val shared = File("/storage/emulated/0/Android/data/p/files")
    private val sd = File("/storage/ABCD-1234/Android/data/p/files")

    @Test fun phoneIsAlwaysAvailable() =
        assertEquals(File(files, "models"), resolveModelsDir(ModelsLocation.PHONE, files, emptyList()))

    @Test fun appFolderUsesFirstExternalDir() =
        assertEquals(File(shared, "models"), resolveModelsDir(ModelsLocation.APP_FOLDER, files, listOf(shared, sd)))

    @Test fun sdCardUsesSecondExternalDirOnlyIfPresent() {
        assertEquals(File(sd, "models"), resolveModelsDir(ModelsLocation.SD_CARD, files, listOf(shared, sd)))
        assertNull(resolveModelsDir(ModelsLocation.SD_CARD, files, listOf(shared)))
        assertNull(resolveModelsDir(ModelsLocation.SD_CARD, files, listOf(shared, null)))
    }

    @Test fun availableLocations() {
        assertEquals(listOf(ModelsLocation.PHONE), availableLocations(files, emptyList()))
        assertEquals(listOf(ModelsLocation.PHONE, ModelsLocation.APP_FOLDER), availableLocations(files, listOf(shared)))
        assertEquals(ModelsLocation.entries, availableLocations(files, listOf(shared, sd)))
    }
}
```
Run `.\gradlew.bat testDebugUnitTest --tests "*.ModelCatalogTest" --tests "*.ModelFilesTest" --tests "*.StorageRootsTest"` → expect FAIL (unresolved references).

- [ ] **Step 2: Implement.**

`models/ModelCatalog.kt`:
```kotlin
package com.exc33ded.library.models

enum class ModelKind { TEXT, VISION }

data class ModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val kind: ModelKind,
    val repo: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val recommended: Boolean = false,
) {
    val url: String get() = "https://huggingface.co/$repo/resolve/main/$fileName"
}

/** All Apache-2.0 and not gated on Hugging Face (no account or token needed). Sizes and hashes from the HF API, 2026-11-22. */
object ModelCatalog {
    val all: List<ModelSpec> = listOf(
        ModelSpec(
            "qwen3-0.6b", "Qwen3 0.6B", "Small and fast. Fixes misread titles and writes short summaries.", ModelKind.TEXT,
            "litert-community/Qwen3-0.6B", "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm", 343_490_560L,
            "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0", recommended = true,
        ),
        ModelSpec(
            "qwen3-1.7b", "Qwen3 1.7B", "Better quality than 0.6B, but uses more memory.", ModelKind.TEXT,
            "litert-community/Qwen3-1.7B", "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm", 977_184_032L,
            "2a72330b504a6596d53a2c00923c57173b10f1b83e6a541213ffec08d1fc5de8",
        ),
        ModelSpec(
            "qwen3.5-0.8b-vl", "Qwen3.5 0.8B Vision", "Reads book covers directly, including non-Latin scripts.", ModelKind.VISION,
            "litert-community/Qwen3.5-0.8B", "Qwen3.5-0.8B-VL_int8.litertlm", 1_303_724_032L,
            "7413f51eafb82e7cf0b707fa506eddfc76c10433b26fa8f0dc2f051970d2feb5",
        ),
        ModelSpec(
            "gemma4-e2b", "Gemma 4 E2B", "Best at reading covers, but heavy for a 6 GB phone.", ModelKind.VISION,
            "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm", 2_588_147_712L,
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        ),
    )

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }
}
```

`models/ModelFiles.kt`:
```kotlin
package com.exc33ded.library.models

import java.io.File
import java.security.MessageDigest

fun finalFile(dir: File, spec: ModelSpec): File = File(dir, spec.fileName)
fun partFile(dir: File, spec: ModelSpec): File = File(dir, spec.fileName + ".part")

/** A model counts as installed only when the file exists with exactly the expected size. */
fun isInstalled(dir: File, spec: ModelSpec): Boolean = finalFile(dir, spec).let { it.isFile && it.length() == spec.sizeBytes }

fun sha256Hex(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/** Total size of all files under [dir] (0 if it does not exist). */
fun dirSize(dir: File): Long = if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
```

`models/Format.kt`:
```kotlin
package com.exc33ded.library.models

import java.util.Locale

/** Decimal units, matching how Hugging Face reports sizes. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000L -> String.format(Locale.US, "%.0f MB", bytes / 1e6)
    bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
    else -> "$bytes B"
}
```

`storage/StorageRoots.kt` (pure functions only; the Android wrapper class is added in Task 3):
```kotlin
package com.exc33ded.library.storage

import com.exc33ded.library.settings.ModelsLocation
import java.io.File

/**
 * [externalDirs] = Context.getExternalFilesDirs(null): index 0 is the app folder on shared storage,
 * index 1 (if present) is the SD card. Returns null when that location is not available.
 */
fun resolveModelsDir(location: ModelsLocation, filesDir: File, externalDirs: List<File?>): File? = when (location) {
    ModelsLocation.PHONE -> File(filesDir, "models")
    ModelsLocation.APP_FOLDER -> externalDirs.getOrNull(0)?.let { File(it, "models") }
    ModelsLocation.SD_CARD -> externalDirs.getOrNull(1)?.let { File(it, "models") }
}

fun availableLocations(filesDir: File, externalDirs: List<File?>): List<ModelsLocation> =
    ModelsLocation.entries.filter { resolveModelsDir(it, filesDir, externalDirs) != null }
```

- [ ] **Step 3: Run, expect PASS** (same command), then the whole suite. Do NOT commit; main session commits `feat(models): catalog and file helpers`.

---

### Task 3: Downloader, worker, repository, manifest (worker: Sonnet subagent)

**Files:** create `models/ModelDownloader.kt`, `models/ModelDownloadWorker.kt`, `models/ModelRepository.kt`, `models/ModelStatus.kt`, `storage/StorageInfo.kt`, `di/ModelModule.kt`; modify `storage/StorageRoots.kt` (add the Android class), `app/src/main/AndroidManifest.xml`; tests `models/ModelDownloaderTest.kt`, `models/ModelStatusTest.kt`.

Read first: `ImportWorker.kt` + `ImportRepository.kt` (existing WorkManager/Hilt pattern), `di/NetworkModule.kt` (`USER_AGENT`, `buildHttpClient`), `LibraryApp.kt`.

- [ ] **Step 1: `models/ModelStatus.kt` + its test (pure state derivation).**

```kotlin
package com.exc33ded.library.models

import androidx.work.WorkInfo

sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus
    data class Paused(val bytes: Long) : ModelStatus            // a partial file exists but nothing is running
    data class Queued(val bytes: Long) : ModelStatus            // waiting for the network constraint (e.g. Wi-Fi)
    data class Downloading(val bytes: Long) : ModelStatus
    data class Ready(val bytes: Long) : ModelStatus
    data class Failed(val message: String, val bytes: Long) : ModelStatus
}

data class ModelUi(val spec: ModelSpec, val status: ModelStatus)

/** [state] is the WorkManager state of this model's download work (null = none); [doneBytes] its reported progress. */
fun deriveStatus(spec: ModelSpec, installed: Boolean, partBytes: Long, state: WorkInfo.State?, doneBytes: Long?, error: String?): ModelStatus = when {
    installed -> ModelStatus.Ready(spec.sizeBytes)
    state == WorkInfo.State.RUNNING -> ModelStatus.Downloading(doneBytes ?: partBytes)
    state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED -> ModelStatus.Queued(partBytes)
    state == WorkInfo.State.FAILED -> ModelStatus.Failed(error ?: "Download failed", partBytes)
    partBytes > 0 -> ModelStatus.Paused(partBytes)
    else -> ModelStatus.NotDownloaded
}
```
`ModelStatusTest.kt`: assert (use `ModelCatalog.all.first()` as spec) — installed wins over any state; RUNNING with doneBytes 10 → `Downloading(10)`, with null → part bytes; ENQUEUED → `Queued`; FAILED with message → `Failed(message, part)`, FAILED without → "Download failed"; SUCCEEDED/CANCELLED/null with part 7 → `Paused(7)`; with part 0 → `NotDownloaded`.

- [ ] **Step 2: `models/ModelDownloader.kt`.**

```kotlin
package com.exc33ded.library.models

import com.exc33ded.library.di.USER_AGENT
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Not the lookup client: no overall request timeout (downloads are GBs), but a socket timeout so a dead connection fails. */
fun buildDownloadClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(UserAgent) { agent = USER_AGENT }
    install(HttpTimeout) {
        requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        socketTimeoutMillis = 60_000
        connectTimeoutMillis = 30_000
    }
}

class ModelDownloader(private val client: HttpClient) {
    /**
     * Streams [spec].url into [part], resuming from its current length with an HTTP Range request.
     * If the server ignores Range (answers 200) the file is restarted from zero. Returns when the body is fully written.
     * Throws IOException on a non-success status or a network error; cancellation is honoured between chunks.
     */
    suspend fun download(spec: ModelSpec, part: File, onProgress: suspend (Long) -> Unit) = withContext(Dispatchers.IO) {
        var offset = part.length()
        if (offset > spec.sizeBytes) { part.delete(); offset = 0L }
        if (offset == spec.sizeBytes) return@withContext
        client.prepareGet(spec.url) { if (offset > 0) header(HttpHeaders.Range, "bytes=$offset-") }.execute { resp ->
            if (resp.status != HttpStatusCode.OK && resp.status != HttpStatusCode.PartialContent) throw IOException("HTTP ${resp.status.value}")
            val append = offset > 0 && resp.status == HttpStatusCode.PartialContent
            var done = if (append) offset else 0L
            resp.bodyAsChannel().toInputStream().use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done)
                    }
                }
            }
        }
    }
}
```
`models/ModelDownloaderTest.kt` — uses `io.ktor.client.engine.mock.MockEngine`/`respond` (already used in `OpenLibrarySourceTest`) and `runTest`. A server stub serves `bytes = ByteArray(200_000) { (it % 251).toByte() }`: with a `Range: bytes=N-` header it answers `206` with `bytes.copyOfRange(N, size)`, otherwise `200` with all bytes; a flag `ignoreRange` makes it always answer `200` with everything; another flag makes it answer `404`. The spec is `ModelSpec("t","T","d",ModelKind.TEXT,"r/x","f.bin", bytes.size.toLong(), "0".repeat(64))`. Four tests: (1) fresh download writes exactly `bytes`; (2) a 50 000-byte `.part` prefix + Range server → final content equals `bytes`; (3) `.part` prefix with `ignoreRange` → still ends equal to `bytes` (restart); (4) 404 → `assertThrows(IOException::class.java)`. Also assert `onProgress` is called with a final value equal to `bytes.size` in test 1. Use `TemporaryFolder` for the part file.

- [ ] **Step 3: `storage/StorageRoots.kt` — append the Android wrapper** (keep the two existing functions):

```kotlin
@Singleton
class StorageRoots @Inject constructor(@ApplicationContext private val ctx: Context) {
    private fun externals(): List<File?> = ctx.getExternalFilesDirs(null).toList()
    fun modelsDir(location: ModelsLocation): File? = resolveModelsDir(location, ctx.filesDir, externals())
    fun available(): List<ModelsLocation> = availableLocations(ctx.filesDir, externals())
    fun freeBytes(dir: File): Long { dir.mkdirs(); return dir.usableSpace }
}
```
(imports: `android.content.Context`, `dagger.hilt.android.qualifiers.ApplicationContext`, `javax.inject.Inject`, `javax.inject.Singleton`.)

- [ ] **Step 4: `models/ModelDownloadWorker.kt`.**

```kotlin
package com.exc33ded.library.models

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.exc33ded.library.settings.SettingsRepository
import com.exc33ded.library.storage.StorageRoots
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepo: SettingsRepository,
    private val roots: StorageRoots,
    private val downloader: ModelDownloader,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val spec = inputData.getString(KEY_ID)?.let(ModelCatalog::byId) ?: return Result.failure()
        val dir = roots.modelsDir(settingsRepo.settings.first().modelsLocation) ?: return fail("Storage location not available")
        dir.mkdirs()
        if (isInstalled(dir, spec)) return Result.success()
        val part = partFile(dir, spec)
        val needed = spec.sizeBytes - part.length().coerceAtMost(spec.sizeBytes)
        if (roots.freeBytes(dir) < needed + spec.sizeBytes / 20) return fail("Not enough free space (needs ${formatBytes(needed)} more)")

        setForeground(foregroundInfo(spec, part.length()))
        var lastReport = 0L
        try {
            downloader.download(spec, part) { done ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastReport >= 500) {
                    lastReport = now
                    setProgress(workDataOf(KEY_DONE to done))
                    setForeground(foregroundInfo(spec, done))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return if (runAttemptCount < 3) Result.retry() else fail("Network error: ${e.message}")
        }
        if (sha256Hex(part) != spec.sha256) { part.delete(); return fail("Download was corrupted (checksum mismatch)") }
        if (!part.renameTo(finalFile(dir, spec))) return fail("Could not save the model")
        return Result.success()
    }

    private fun fail(message: String) = Result.failure(workDataOf(KEY_ERROR to message))

    private fun foregroundInfo(spec: ModelSpec, done: Long): ForegroundInfo {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        val percent = (done * 100 / spec.sizeBytes).toInt().coerceIn(0, 100)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${spec.name}")
            .setContentText("${formatBytes(done)} of ${formatBytes(spec.sizeBytes)}")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return ForegroundInfo(spec.id.hashCode(), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val TAG = "model-download"
        const val KEY_ID = "id"
        const val KEY_DONE = "done"
        const val KEY_ERROR = "error"
        private const val CHANNEL = "models"
        fun workName(id: String) = "model-download-$id"
        fun modelTag(id: String) = "model:$id"
    }
}
```

- [ ] **Step 5: `models/ModelRepository.kt`.**

```kotlin
package com.exc33ded.library.models

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.exc33ded.library.settings.ModelsLocation
import com.exc33ded.library.settings.SettingsRepository
import com.exc33ded.library.storage.StorageRoots
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settingsRepo: SettingsRepository,
    private val roots: StorageRoots,
) {
    private val wm get() = WorkManager.getInstance(ctx)
    private val active = setOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)

    fun observe(): Flow<List<ModelUi>> =
        combine(settingsRepo.settings, wm.getWorkInfosByTagFlow(ModelDownloadWorker.TAG)) { s, infos ->
            val dir = roots.modelsDir(s.modelsLocation)
            ModelCatalog.all.map { spec ->
                val mine = infos.filter { ModelDownloadWorker.modelTag(spec.id) in it.tags }
                val info = mine.firstOrNull { it.state in active } ?: mine.firstOrNull { it.state == WorkInfo.State.FAILED }
                val installed = dir != null && isInstalled(dir, spec)
                val part = if (dir != null) partFile(dir, spec).let { if (it.isFile) it.length() else 0L } else 0L
                ModelUi(
                    spec,
                    deriveStatus(
                        spec, installed, part, info?.state,
                        info?.progress?.getLong(ModelDownloadWorker.KEY_DONE, -1L)?.takeIf { it >= 0 },
                        info?.outputData?.getString(ModelDownloadWorker.KEY_ERROR),
                    ),
                )
            }
        }

    suspend fun download(id: String) {
        val wifiOnly = settingsRepo.settings.first().wifiOnlyDownloads
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_ID to id))
            .addTag(ModelDownloadWorker.TAG)
            .addTag(ModelDownloadWorker.modelTag(id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(ModelDownloadWorker.workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    /** Stops the download but keeps the partial file so it can resume. */
    fun cancel(id: String) { wm.cancelUniqueWork(ModelDownloadWorker.workName(id)) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        cancel(id)
        val spec = ModelCatalog.byId(id) ?: return@withContext
        val dir = roots.modelsDir(settingsRepo.settings.first().modelsLocation) ?: return@withContext
        finalFile(dir, spec).delete()
        partFile(dir, spec).delete()
    }

    /** Moves installed models to [to] and saves the setting. Returns an error message, or null on success. */
    suspend fun moveModels(to: ModelsLocation): String? = withContext(Dispatchers.IO) {
        val current = settingsRepo.settings.first().modelsLocation
        val from = roots.modelsDir(current)
        val dest = roots.modelsDir(to) ?: return@withContext "That location is not available on this phone"
        if (from == dest) return@withContext null
        if (wm.getWorkInfosByTag(ModelDownloadWorker.TAG).get().any { it.state in active }) return@withContext "Wait for downloads to finish first"
        dest.mkdirs()
        val files = from?.listFiles()?.filter { it.isFile }.orEmpty()
        val installed = files.filter { !it.name.endsWith(".part") }
        val needed = installed.sumOf { it.length() }
        if (roots.freeBytes(dest) < needed + needed / 20) return@withContext "Not enough free space on the new location"
        files.filter { it.name.endsWith(".part") }.forEach { it.delete() }
        for (f in installed) {
            val target = File(dest, f.name)
            f.copyTo(target, overwrite = true)
            if (target.length() != f.length()) { target.delete(); return@withContext "Copy failed for ${f.name}" }
            f.delete()
        }
        settingsRepo.update { it.copy(modelsLocation = to) }
        null
    }
}
```
(`getWorkInfosByTagFlow` is the WorkManager KTX flow API; if it does not resolve, use `getWorkInfosByTagLiveData(...)` converted with `asFlow()` from `androidx.lifecycle` and report it.)

- [ ] **Step 6: `storage/StorageInfo.kt`** (sizes shown read-only on the screen):

```kotlin
package com.exc33ded.library.storage

import android.content.Context
import com.exc33ded.library.models.dirSize
import com.exc33ded.library.settings.ModelsLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StorageUsage(
    val databaseBytes: Long,
    val coversBytes: Long,
    val photosBytes: Long,
    val modelsBytes: Long,
    val modelsPath: String?,
    val freeBytes: Long,
)

@Singleton
class StorageInfo @Inject constructor(@ApplicationContext private val ctx: Context, private val roots: StorageRoots) {
    suspend fun usage(location: ModelsLocation): StorageUsage = withContext(Dispatchers.IO) {
        val db = ctx.getDatabasePath("library.db")
        val dbBytes = listOf("", "-wal", "-shm").sumOf { File(db.path + it).let { f -> if (f.isFile) f.length() else 0L } }
        val models = roots.modelsDir(location)
        StorageUsage(
            databaseBytes = dbBytes,
            coversBytes = dirSize(File(ctx.filesDir, "covers")),
            photosBytes = dirSize(File(ctx.filesDir, "imports")) + dirSize(File(ctx.filesDir, "camera")),
            modelsBytes = models?.let(::dirSize) ?: 0L,
            modelsPath = models?.absolutePath,
            freeBytes = models?.let(roots::freeBytes) ?: 0L,
        )
    }
}
```

- [ ] **Step 7: `di/ModelModule.kt`**

```kotlin
package com.exc33ded.library.di

import com.exc33ded.library.models.ModelDownloader
import com.exc33ded.library.models.buildDownloadClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelModule {
    @Provides @Singleton
    fun modelDownloader(): ModelDownloader = ModelDownloader(buildDownloadClient(OkHttp.create()))
}
```

- [ ] **Step 8: Manifest** (`app/src/main/AndroidManifest.xml`): next to `INTERNET` add
```xml
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
and inside `<application>` (before the first `<provider>`) add the WorkManager foreground-service type declaration:
```xml
        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync"
            tools:node="merge" />
```

- [ ] **Step 9: Verify** — `.\gradlew.bat testDebugUnitTest` (all pass: 103 + Task 1–2 tests + the new downloader/status tests) and `.\gradlew.bat assembleDebug`. If `toInputStream`, `getWorkInfosByTagFlow` or a Hilt binding does not resolve, report the exact error and the smallest fix you applied. Do NOT commit; main session commits `feat(models): downloader, worker, repository`.

---

### Task 4: Models & storage screen, Settings entry, scan shortcut (worker: Sonnet subagent)

**Files:** create `ui/models/ModelsStorageViewModel.kt`, `ui/models/ModelsStorageScreen.kt`; modify `ui/nav/Keys.kt`, `ui/nav/AppNav.kt`, `ui/settings/SettingsScreen.kt`, `ui/library/LibraryScreen.kt`.

Read first: `SettingsScreen.kt`, `AppNav.kt`, `LibraryScreen.kt` (current), `ui/settings/DisplayOptions.kt` (for `SwitchRow`).

- [ ] **Step 1: Route.** `ui/nav/Keys.kt`: add `@Serializable data object ModelsStorageKey : NavKey`.

- [ ] **Step 2: `ui/models/ModelsStorageViewModel.kt`**

```kotlin
package com.exc33ded.library.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.models.ModelRepository
import com.exc33ded.library.models.ModelUi
import com.exc33ded.library.settings.ModelsLocation
import com.exc33ded.library.settings.SettingsRepository
import com.exc33ded.library.storage.StorageInfo
import com.exc33ded.library.storage.StorageRoots
import com.exc33ded.library.storage.StorageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsStorageViewModel @Inject constructor(
    private val repo: ModelRepository,
    private val settingsRepo: SettingsRepository,
    private val info: StorageInfo,
    roots: StorageRoots,
) : ViewModel() {
    val availableLocations: List<ModelsLocation> = roots.available()
    val message = MutableStateFlow<String?>(null)

    val models: StateFlow<List<ModelUi>> =
        repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recomputed whenever the location changes or the model list changes (a download finished / was deleted). */
    val usage: StateFlow<StorageUsage?> = combine(settingsRepo.settings, models) { s, _ -> s.modelsLocation }
        .flatMapLatest { loc -> flow { emit(info.usage(loc)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun download(id: String) { viewModelScope.launch { repo.download(id) } }
    fun cancel(id: String) = repo.cancel(id)
    fun delete(id: String) { viewModelScope.launch { repo.delete(id) } }
    fun setWifiOnly(v: Boolean) { viewModelScope.launch { settingsRepo.update { it.copy(wifiOnlyDownloads = v) } } }
    fun setLocation(loc: ModelsLocation) { viewModelScope.launch { message.value = repo.moveModels(loc) } }
    fun clearMessage() { message.value = null }
}
```

- [ ] **Step 3: `ui/models/ModelsStorageScreen.kt`.** One screen with a `Scaffold` + `TopAppBar` ("Models & storage", back arrow like `SettingsScreen`) + `SnackbarHost` (show `vm.message` then `vm.clearMessage()`), and a vertically scrolling `Column` (16 dp padding, 16 dp spacing) with these sections, reading the current settings from `LocalAppSettings.current` (do NOT collect settings in this screen):
  1. **Models folder** (title). A radio list (`RadioButton` rows) over `vm.availableLocations` with labels `PHONE → "Phone storage"`, `APP_FOLDER → "App folder (visible in file manager)"`, `SD_CARD → "SD card"`; selecting a different one calls `vm.setLocation(it)`. Under it, from `vm.usage`: `Text(path)` (small, selectable), `"Free: ${formatBytes(freeBytes)}"`, `"Models use ${formatBytes(modelsBytes)}"`.
  2. `SwitchRow("Download on Wi-Fi only", settings.wifiOnlyDownloads) { vm.setWifiOnly(it) }` (the `SwitchRow` from `ui/settings/DisplayOptions.kt`).
  3. **AI models** (title) + a one-line note `"Free, no account needed. On-device AI features that use these models arrive in a later update."`. One `Card` per `ModelUi`: name (+ "Recommended" label when `spec.recommended`), `spec.description`, `"${formatBytes(spec.sizeBytes)} · ${if (kind == VISION) "Vision" else "Text"}"`, then by status: `NotDownloaded` → `Button("Download")`; `Paused(b)` → text `"Paused at ${formatBytes(b)}"` + `Button("Resume")` + `TextButton("Delete")`; `Queued(b)` → `"Waiting for ${if wifi-only "Wi-Fi" else "network"}…"` + `TextButton("Cancel")`; `Downloading(b)` → `LinearProgressIndicator(progress = { b.toFloat() / spec.sizeBytes })` + `"${formatBytes(b)} of ${formatBytes(spec.sizeBytes)}"` + `TextButton("Cancel")`; `Ready` → text `"Downloaded"` + `TextButton("Delete")` (confirm with an `AlertDialog` "Delete <name>? You can download it again later."); `Failed(msg, b)` → `msg` in `MaterialTheme.colorScheme.error` + `Button("Retry")`. The Download/Resume/Retry buttons call `vm.download(spec.id)`; on Android 13+ first request `Manifest.permission.POST_NOTIFICATIONS` once with `rememberLauncherForActivityResult(RequestPermission())` and start the download in the callback regardless of the result.
  4. **Stored on this phone** (title) with rows `"Database"`, `"Covers"`, `"Import photos"` and their `formatBytes` sizes from `vm.usage`, then this exact paragraph: `"These stay in the app's private storage on purpose: a database on removable or shared storage can be lost or corrupted when the card is removed. A backup/export feature is planned."`

- [ ] **Step 4: Settings entry.** In `ui/settings/SettingsScreen.kt` add a parameter `onOpenModels: () -> Unit` and replace the disabled "Models & storage" `ListItem` (with its `ponytail:` comment) by:
```kotlin
            ListItem(
                headlineContent = { Text("Models & storage") },
                supportingContent = { Text("Download AI models, choose where they are stored") },
                modifier = Modifier.clickable(onClick = onOpenModels).padding(bottom = 24.dp),
            )
```
(import `androidx.compose.foundation.clickable`).

- [ ] **Step 5: Nav.** In `ui/nav/AppNav.kt` change the Settings entry to `SettingsScreen(onBack = { backStack.removeLastOrNull() }, onOpenModels = { push(ModelsStorageKey) })` and add `entry<ModelsStorageKey> { ModelsStorageScreen(onBack = { backStack.removeLastOrNull() }) }` (import `com.exc33ded.library.ui.models.ModelsStorageScreen`). `ModelsStorageScreen` signature: `fun ModelsStorageScreen(onBack: () -> Unit, vm: ModelsStorageViewModel = hiltViewModel())`.

- [ ] **Step 6: Scan shortcut.** `ui/library/LibraryScreen.kt`: add parameter `onScanIsbn: () -> Unit` (after `onOpenImports`), and in the search `Row`, between the `OutlinedTextField` and the existing display-options `IconButton`, add:
```kotlin
            IconButton(onClick = onScanIsbn) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan ISBN barcode") }
```
(import `androidx.compose.material.icons.filled.QrCodeScanner`; the same icon is already used in `AppNav`). In `AppNav.kt`, in the `entry<LibraryKey>` call add `onScanIsbn = { scanIsbn() },`.

- [ ] **Step 7: Verify** — `.\gradlew.bat testDebugUnitTest` (all pass) and `.\gradlew.bat assembleDebug`. Do NOT commit; main session commits `feat(models): models & storage screen, library scan shortcut`.

---

### Task 5: Verify on the emulator, build the APK, update docs (main session only)

- [ ] **Step 1:** Install; Settings → Models & storage: radio shows Phone storage (+ App folder, + SD card only if the emulator reports one); free space and usage numbers look sane; Wi-Fi-only switch persists across relaunch.
- [ ] **Step 2:** Real download of **Qwen3 0.6B** (343 MB) over the emulator network: progress moves, notification appears, **Cancel** keeps a partial and shows "Paused at …", **Resume** continues from that offset (watch the byte count not restart), completion shows "Downloaded", the file's size equals 343,490,560 bytes (`adb shell run-as … ls -l files/models`), then **Delete** removes it. Try a download with Wi-Fi-only on and off.
- [ ] **Step 3:** Location switch with a model installed (App folder ↔ Phone): the file moves, setting persists; with no SD card the SD option is absent. Scan button on Library opens the scanner (or shows the "Scanner unavailable" message on the emulator).
- [ ] **Step 4:** Fix defects; update `docs/HANDOFF.md`; build the debug APK (`.\gradlew.bat assembleDebug`), note its path/size; commit.

## Self-review against the spec

- §1 catalog → Task 2 (`ModelCatalog` + test). §2 storage roots, free space, usage, DB-stays-private explanation → Tasks 1, 2, 3 (`StorageRoots`, `StorageInfo`), 4 (screen text). §3 downloader (Range resume, SHA-256, Wi-Fi only, foreground notification, cancel/delete, free-space check, move) → Tasks 1, 3. §4 scan shortcut → Task 4 step 6. §6 testing → unit tests in Tasks 1–3, manual checks in Task 5.
- Names consistent across tasks: `ModelsLocation`, `AppSettings.modelsLocation/wifiOnlyDownloads`, `ModelSpec`, `ModelKind`, `ModelCatalog.all/byId`, `finalFile`, `partFile`, `isInstalled`, `sha256Hex`, `dirSize`, `formatBytes`, `resolveModelsDir`, `availableLocations`, `StorageRoots.modelsDir/available/freeBytes`, `ModelDownloader.download`, `ModelDownloadWorker.TAG/KEY_*`, `ModelStatus`, `ModelUi`, `deriveStatus`, `ModelRepository.observe/download/cancel/delete/moveModels`, `StorageUsage`, `StorageInfo.usage`, `ModelsStorageViewModel`, `ModelsStorageKey`.
