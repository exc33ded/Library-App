# Phase 1 — Library Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Execution model for this project:** routine tasks are delegated to `opencode run` (each task section
> is written to be self-contained — paste the whole task as the prompt). Claude reviews every diff,
> runs the verify commands, and commits. Never pass `local.properties` or any key to opencode.

**Goal:** A runnable Android app where the user can add (manually), view, edit, delete books, set reading status (Want/Reading/Read/DNF) and progress, and organise books into categories — stored offline in Room, with the Material 3 Expressive theme and navigation shell in place.

**Architecture:** Single Gradle `app` module, packages per area (`data`, `di`, `ui.*`). Room is the single source of truth; a `BookRepository` performs all writes and stamps every write with `updatedAt` + `dirty = true` + soft-delete so Phase 4 sync can be added without schema changes. UI is Jetpack Compose with Navigation 3 and Hilt ViewModels.

**Tech Stack:** AGP 9.5.2 (built-in Kotlin) · Kotlin 2.4.21 · Gradle 9.9.2 · Compose BOM 2026.11.01 (material3 1.5.0) · Navigation 3 1.2.2 · Room 2.8.5 · Hilt 2.60.1 · KSP 2.3.14 · kotlinx-serialization 1.13.0 · JUnit4 + Robolectric 4.18.1 + Turbine 1.2.1. Versions pinned from Maven on 2026-11-21.

Spec: `docs/superpowers/specs/2026-11-21-book-library-app-design.md`

---

## File map

```
.gitignore
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml
app/src/main/java/com/exc33ded/library/
  LibraryApp.kt                 Hilt application
  MainActivity.kt               edge-to-edge, theme, AppNav
  data/Ids.kt                   UUID helpers (random + deterministic pair id)
  data/db/Entities.kt           BookEntity, CategoryEntity, BookCategoryEntity, ReadingStatus
  data/db/BookDao.kt
  data/db/CategoryDao.kt
  data/db/AppDatabase.kt
  data/BookRules.kt             pure status/progress transitions
  data/BookRepository.kt        all writes; dirty/updatedAt/soft-delete
  di/DatabaseModule.kt
  ui/theme/Theme.kt             MaterialExpressiveTheme + MotionScheme.expressive + dynamic color
  ui/nav/Keys.kt                Navigation 3 keys
  ui/nav/AppNav.kt              NavDisplay + bottom bar + centre add button
  ui/common/PlaceholderScreen.kt
  ui/common/CoverPlaceholder.kt
  ui/common/StatusSelector.kt
  ui/library/LibraryFilter.kt   pure filtering
  ui/library/LibraryViewModel.kt
  ui/library/LibraryScreen.kt
  ui/book/BookForm.kt           pure form state + validation
  ui/book/BookEditViewModel.kt
  ui/book/BookEditScreen.kt
  ui/book/BookDetailViewModel.kt
  ui/book/BookDetailScreen.kt
  ui/categories/CategoriesViewModel.kt
  ui/categories/CategoriesScreen.kt
app/src/test/java/com/exc33ded/library/
  data/IdsTest.kt
  data/BookRulesTest.kt
  data/BookRepositoryTest.kt    Robolectric + in-memory Room
  ui/library/LibraryFilterTest.kt
  ui/book/BookFormTest.kt
```

---

### Task 0: Toolchain (one-off, on the dev PC)

Current state: Java 8 only, no Android SDK, no Gradle. `scoop` is available.

- [ ] **Step 1: Install JDK 21, Gradle, Android command-line tools**

```powershell
scoop bucket add java
scoop bucket add extras
scoop install java/temurin21-jdk gradle extras/android-clt
```

- [ ] **Step 2: Point ANDROID_HOME at the SDK and install platform packages**

```powershell
$sdk = "$env:USERPROFILE\scoop\apps\android-clt\current"
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdk, "User")
$env:ANDROID_HOME = $sdk
sdkmanager --licenses   # answer y to all
sdkmanager "platform-tools" "platforms;android-37" "platforms;android-36"
```

- [ ] **Step 3: Verify**

Run (new terminal): `java -version; gradle --version; adb version`
Expected: Java 21.x, Gradle 9.x, Android Debug Bridge version printed.
If `scoop` put the SDK elsewhere, run `scoop prefix android-clt` and use that path for `ANDROID_HOME`.

---

### Task 1: Project skeleton, git, first build

**Files:** Create `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/java/com/exc33ded/library/LibraryApp.kt`, `app/src/main/java/com/exc33ded/library/MainActivity.kt`

- [ ] **Step 1: `.gitignore`** (must exist before the first `git add`)

```gitignore
# Build
.gradle/
build/
app/build/
.kotlin/
captures/
*.apk
*.aab
# IDE
.idea/
*.iml
.DS_Store
# Secrets — NEVER commit
local.properties
*.jks
*.keystore
*.env
secrets.properties
# Private / large
docs/Books-Photo/
graft/
```

- [ ] **Step 2: `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.5.2"
kotlin = "2.4.21"
ksp = "2.3.14"
hilt = "2.60.1"
androidxHilt = "1.4.0"
room = "2.8.5"
composeBom = "2026.11.01"
activityCompose = "1.13.0"
coreKtx = "1.19.1"
lifecycle = "2.11.1"
navigation3 = "1.2.2"
serialization = "1.13.0"
coroutines = "1.11.0"
junit = "4.13.2"
androidxTestExt = "1.3.0"
robolectric = "4.18.1"
turbine = "1.2.1"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-viewmodel-compose = { module = "androidx.hilt:hilt-lifecycle-viewmodel-compose", version.ref = "androidxHilt" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 3: `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Library-App"
include(":app")
```

- [ ] **Step 4: root `build.gradle.kts`**

```kotlin
// AGP 9 has built-in Kotlin; this classpath entry pins KGP to 2.4.21
// (LiteRT-LM in Phase 3 needs Kotlin >= 2.3).
buildscript {
    dependencies { classpath(libs.kotlin.gradle.plugin) }
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
```

- [ ] **Step 5: `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
android.useAndroidX=true
```

- [ ] **Step 6: `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.exc33ded.library"
    // Compose 1.13 requires compileSdk >= 37.1 (install "platforms;android-37.2")
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        applicationId = "com.exc33ded.library"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
```

- [ ] **Step 7: Manifest, strings, Application, Activity (minimal)**

`app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".LibraryApp"
        android:label="@string/app_name"
        android:enableOnBackInvokedCallback="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`
```xml
<resources>
    <string name="app_name">Library</string>
</resources>
```

`app/src/main/java/com/exc33ded/library/LibraryApp.kt`
```kotlin
package com.exc33ded.library

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LibraryApp : Application()
```

`app/src/main/java/com/exc33ded/library/MainActivity.kt` (temporary body; replaced in Task 7)
```kotlin
package com.exc33ded.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Text("Library") }
    }
}
```

- [ ] **Step 8: Gradle wrapper + build**

```powershell
gradle wrapper --gradle-version 9.9.2
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`. If plugin resolution fails, the usual causes are (a) KSP/Kotlin mismatch — check https://github.com/google/ksp/releases for the release matching Kotlin 2.4.21; (b) AGP 9 rejecting a `kotlin-android` plugin — none is applied here, keep it that way.

- [ ] **Step 9: git init, first commit, push to the existing empty repo**

```powershell
git init -b main
git status --short   # confirm: no local.properties, no docs/Books-Photo
git add .
git commit -m "chore: project skeleton (AGP 9, Compose, Hilt, Room, Nav3)"
git remote add origin https://github.com/exc33ded/Library-App.git
git push -u origin main
```

---

### Task 2: IDs and pure book rules

**Files:** Create `app/src/main/java/com/exc33ded/library/data/Ids.kt`, `app/src/main/java/com/exc33ded/library/data/db/Entities.kt`, `app/src/main/java/com/exc33ded/library/data/BookRules.kt`; Test `app/src/test/java/com/exc33ded/library/data/IdsTest.kt`, `app/src/test/java/com/exc33ded/library/data/BookRulesTest.kt`

- [ ] **Step 1: Write failing tests**

`IdsTest.kt`
```kotlin
package com.exc33ded.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdsTest {
    @Test fun pairIdIsDeterministic() =
        assertEquals(Ids.pair("book1", "cat1"), Ids.pair("book1", "cat1"))

    @Test fun pairIdDependsOnOrderAndValues() {
        assertNotEquals(Ids.pair("book1", "cat1"), Ids.pair("cat1", "book1"))
        assertNotEquals(Ids.pair("book1", "cat1"), Ids.pair("book1", "cat2"))
    }

    @Test fun newIdsAreUnique() = assertNotEquals(Ids.new(), Ids.new())
}
```

`BookRulesTest.kt`
```kotlin
package com.exc33ded.library.data

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookRulesTest {
    private val book = BookEntity(id = "b", title = "Yellowface", authors = "Rebecca F. Kuang", pageCount = 320)

    @Test fun startingReadingSetsStartedAtOnce() {
        val r = book.withStatus(ReadingStatus.READING, now = 100)
        assertEquals(100L, r.startedAt)
        assertEquals(100L, r.withStatus(ReadingStatus.READING, now = 200).startedAt)
    }

    @Test fun finishingSetsFinishedAtAndLastPage() {
        val r = book.withStatus(ReadingStatus.READ, now = 500)
        assertEquals(500L, r.finishedAt)
        assertEquals(320, r.currentPage)
        assertEquals(500L, r.startedAt) // never started explicitly -> same moment
    }

    @Test fun movingBackToWantClearsFinishedAt() {
        val r = book.withStatus(ReadingStatus.READ, 500).withStatus(ReadingStatus.WANT, 600)
        assertNull(r.finishedAt)
    }

    @Test fun progressIsClampedToPageCount() {
        assertEquals(320, book.withProgress(999).currentPage)
        assertEquals(0, book.withProgress(-4).currentPage)
    }

    @Test fun progressOnUnknownPageCountOnlyClampsNegative() =
        assertEquals(50, book.copy(pageCount = null).withProgress(50).currentPage)

    @Test fun reachingLastPageMarksRead() =
        assertEquals(ReadingStatus.READ, book.copy(status = ReadingStatus.READING).withProgress(320, now = 9).status)
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.exc33ded.library.data.*"`
Expected: FAIL — compilation errors (`Ids`, `BookEntity`, `withStatus` unresolved).

- [ ] **Step 3: Implement**

`data/Ids.kt`
```kotlin
package com.exc33ded.library.data

import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()

    /** Same (a, b) -> same id on every device, so offline link edits don't duplicate after sync. */
    fun pair(a: String, b: String): String = UUID.nameUUIDFromBytes("$a|$b".toByteArray()).toString()
}
```

`data/db/Entities.kt`
```kotlin
package com.exc33ded.library.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReadingStatus { WANT, READING, READ, DNF }

enum class SummarySource { DESCRIPTION, COVER, USER }

/** Every synced table carries updatedAt / deleted / dirty (spec §5). */
@Entity(tableName = "books", indices = [Index("status"), Index("isbn")])
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: String = "",
    val isbn: String? = null,
    val description: String? = null,
    val aiSummary: String? = null,
    val aiSummarySource: SummarySource? = null,
    val coverPath: String? = null,
    val coverRemotePath: String? = null,
    val pageCount: Int? = null,
    val status: ReadingStatus = ReadingStatus.WANT,
    val currentPage: Int = 0,
    val rating: Int? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val olWorkKey: String? = null,
    val notes: String? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** id = Ids.pair(bookId, categoryId). */
@Entity(tableName = "book_categories", indices = [Index("bookId"), Index("categoryId")])
data class BookCategoryEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val categoryId: String,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)
```

`data/BookRules.kt`
```kotlin
package com.exc33ded.library.data

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus

fun BookEntity.withStatus(newStatus: ReadingStatus, now: Long): BookEntity = when (newStatus) {
    ReadingStatus.WANT -> copy(status = newStatus, finishedAt = null)
    ReadingStatus.READING -> copy(status = newStatus, startedAt = startedAt ?: now, finishedAt = null)
    ReadingStatus.READ -> copy(
        status = newStatus,
        startedAt = startedAt ?: now,
        finishedAt = now,
        currentPage = pageCount ?: currentPage,
    )
    ReadingStatus.DNF -> copy(status = newStatus, finishedAt = now)
}

fun BookEntity.withProgress(page: Int, now: Long = System.currentTimeMillis()): BookEntity {
    val clamped = page.coerceAtLeast(0).let { p -> pageCount?.let { p.coerceAtMost(it) } ?: p }
    val updated = copy(currentPage = clamped)
    return if (pageCount != null && clamped == pageCount && status != ReadingStatus.READ)
        updated.withStatus(ReadingStatus.READ, now) else updated
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.exc33ded.library.data.*"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```powershell
git add app/src
git commit -m "feat(data): entities, ids, status/progress rules"
```

---

### Task 3: Room DAOs, database, repository

**Files:** Create `data/db/BookDao.kt`, `data/db/CategoryDao.kt`, `data/db/AppDatabase.kt`, `data/BookRepository.kt`, `di/DatabaseModule.kt` (all under `app/src/main/java/com/exc33ded/library/`); Test `app/src/test/java/com/exc33ded/library/data/BookRepositoryTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.exc33ded.library.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exc33ded.library.data.db.AppDatabase
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BookRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: BookRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = BookRepository(db, clock = { 1_000L })
    }

    @After fun tearDown() = db.close()

    @Test fun savedBookIsDirtyAndStamped() = runTest {
        val id = repo.saveBook(BookEntity(id = "b1", title = "Dune", dirty = false), emptySet())
        val saved = db.bookDao().get(id)!!
        assertTrue(saved.dirty)
        assertEquals(1_000L, saved.updatedAt)
    }

    @Test fun deleteIsSoftAndHiddenFromList() = runTest {
        repo.saveBook(BookEntity(id = "b1", title = "Dune"), emptySet())
        repo.deleteBook("b1")
        assertTrue(repo.observeBooks().first().isEmpty())
        assertTrue(db.bookDao().get("b1")!!.deleted)
    }

    @Test fun categoryLinksAreReplacedNotDuplicated() = runTest {
        val sf = repo.saveCategory("Sci-Fi")
        val classic = repo.saveCategory("Classic")
        repo.saveBook(BookEntity(id = "b1", title = "Dune"), setOf(sf, classic))
        repo.saveBook(BookEntity(id = "b1", title = "Dune"), setOf(sf))
        repo.saveBook(BookEntity(id = "b1", title = "Dune"), setOf(sf))
        assertEquals(setOf(sf), repo.categoryIdsFor("b1"))
        assertEquals(2, db.categoryDao().allLinksIncludingDeleted().size)
    }

    @Test fun setStatusAppliesRules() = runTest {
        repo.saveBook(BookEntity(id = "b1", title = "Dune", pageCount = 600), emptySet())
        repo.setStatus("b1", ReadingStatus.READ)
        val b = db.bookDao().get("b1")!!
        assertEquals(600, b.currentPage)
        assertEquals(1_000L, b.finishedAt)
    }

    @Test fun deletingCategoryRemovesItsLinks() = runTest {
        val sf = repo.saveCategory("Sci-Fi")
        repo.saveBook(BookEntity(id = "b1", title = "Dune"), setOf(sf))
        repo.deleteCategory(sf)
        assertTrue(repo.observeCategories().first().isEmpty())
        assertTrue(repo.categoryIdsFor("b1").isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.exc33ded.library.data.BookRepositoryTest"`
Expected: FAIL — `AppDatabase`, `BookRepository` unresolved.

- [ ] **Step 3: Implement DAOs, database, repository, Hilt module**

`data/db/BookDao.kt`
```kotlin
package com.exc33ded.library.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE deleted = 0 ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id AND deleted = 0")
    fun observe(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Upsert
    suspend fun upsert(book: BookEntity)
}
```

`data/db/CategoryDao.kt`
```kotlin
package com.exc33ded.library.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM book_categories WHERE deleted = 0")
    fun observeLinks(): Flow<List<BookCategoryEntity>>

    @Query("SELECT * FROM book_categories WHERE bookId = :bookId")
    suspend fun linksForBook(bookId: String): List<BookCategoryEntity>

    @Query("SELECT * FROM book_categories WHERE categoryId = :categoryId")
    suspend fun linksForCategory(categoryId: String): List<BookCategoryEntity>

    @Query("SELECT * FROM book_categories")
    suspend fun allLinksIncludingDeleted(): List<BookCategoryEntity>

    @Upsert
    suspend fun upsertLink(link: BookCategoryEntity)
}
```

`data/db/AppDatabase.kt`
```kotlin
package com.exc33ded.library.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, CategoryEntity::class, BookCategoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun categoryDao(): CategoryDao
}
```

`data/BookRepository.kt`
```kotlin
package com.exc33ded.library.data

import androidx.room.withTransaction
import com.exc33ded.library.data.db.AppDatabase
import com.exc33ded.library.data.db.BookCategoryEntity
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import com.exc33ded.library.data.db.ReadingStatus
import javax.inject.Inject
import javax.inject.Singleton

/** All writes go through here: stamps updatedAt, sets dirty, soft-deletes (spec §5/§6). */
@Singleton
class BookRepository(
    private val db: AppDatabase,
    private val clock: () -> Long,
) {
    @Inject constructor(db: AppDatabase) : this(db, System::currentTimeMillis)

    private val books = db.bookDao()
    private val cats = db.categoryDao()

    fun observeBooks() = books.observeAll()
    fun observeBook(id: String) = books.observe(id)
    fun observeCategories() = cats.observeAll()
    fun observeLinks() = cats.observeLinks()

    suspend fun getBook(id: String): BookEntity? = books.get(id)?.takeUnless { it.deleted }

    suspend fun categoryIdsFor(bookId: String): Set<String> =
        cats.linksForBook(bookId).filterNot { it.deleted }.map { it.categoryId }.toSet()

    suspend fun saveBook(book: BookEntity, categoryIds: Set<String>): String = db.withTransaction {
        val now = clock()
        books.upsert(book.copy(updatedAt = now, dirty = true, deleted = false))
        val existing = cats.linksForBook(book.id)
        existing.filter { !it.deleted && it.categoryId !in categoryIds }
            .forEach { cats.upsertLink(it.copy(deleted = true, dirty = true, updatedAt = now)) }
        categoryIds.forEach { catId ->
            val link = existing.firstOrNull { it.categoryId == catId }
            if (link == null || link.deleted) cats.upsertLink(
                BookCategoryEntity(Ids.pair(book.id, catId), book.id, catId, updatedAt = now),
            )
        }
        book.id
    }

    suspend fun setStatus(id: String, status: ReadingStatus) = update(id) { it.withStatus(status, clock()) }

    suspend fun setProgress(id: String, page: Int) = update(id) { it.withProgress(page, clock()) }

    suspend fun deleteBook(id: String) = update(id) { it.copy(deleted = true) }

    suspend fun saveCategory(name: String, id: String = Ids.new()): String {
        cats.upsert(CategoryEntity(id = id, name = name.trim(), updatedAt = clock()))
        return id
    }

    suspend fun deleteCategory(id: String) = db.withTransaction {
        val now = clock()
        val c = cats.get(id) ?: return@withTransaction
        cats.upsert(c.copy(deleted = true, dirty = true, updatedAt = now))
        cats.linksForCategory(id).filterNot { it.deleted }
            .forEach { cats.upsertLink(it.copy(deleted = true, dirty = true, updatedAt = now)) }
    }

    private suspend fun update(id: String, change: (BookEntity) -> BookEntity) {
        val b = books.get(id) ?: return
        books.upsert(change(b).copy(updatedAt = clock(), dirty = true))
    }
}
```

`di/DatabaseModule.kt`
```kotlin
package com.exc33ded.library.di

import android.content.Context
import androidx.room.Room
import com.exc33ded.library.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "library.db").build()
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (all data tests). `app/schemas/com.exc33ded.library.data.db.AppDatabase/1.json` is generated — commit it (needed for future auto-migrations).

- [ ] **Step 5: Commit**

```powershell
git add app/src app/schemas
git commit -m "feat(data): Room DAOs, database, repository with soft delete + dirty flags"
```

---

### Task 4: Pure library filter and book form

**Files:** Create `ui/library/LibraryFilter.kt`, `ui/book/BookForm.kt`; Test `app/src/test/java/com/exc33ded/library/ui/library/LibraryFilterTest.kt`, `app/src/test/java/com/exc33ded/library/ui/book/BookFormTest.kt`

- [ ] **Step 1: Write failing tests**

`LibraryFilterTest.kt`
```kotlin
package com.exc33ded.library.ui.library

import com.exc33ded.library.data.db.BookCategoryEntity
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private val dune = BookEntity("1", "Dune", "Frank Herbert", status = ReadingStatus.READ)
    private val yf = BookEntity("2", "Yellowface", "Rebecca F. Kuang", status = ReadingStatus.READING)
    private val bond = BookEntity("3", "Great Stories for Children", "Ruskin Bond")
    private val links = listOf(BookCategoryEntity("l1", "1", "scifi"))
    private val all = listOf(dune, yf, bond)

    @Test fun noFilterReturnsAll() = assertEquals(all, filterBooks(all, links, LibraryFilter()))

    @Test fun byStatus() =
        assertEquals(listOf(yf), filterBooks(all, links, LibraryFilter(status = ReadingStatus.READING)))

    @Test fun byCategory() =
        assertEquals(listOf(dune), filterBooks(all, links, LibraryFilter(categoryId = "scifi")))

    @Test fun queryMatchesTitleOrAuthorCaseInsensitive() {
        assertEquals(listOf(bond), filterBooks(all, links, LibraryFilter(query = "ruskin")))
        assertEquals(listOf(yf), filterBooks(all, links, LibraryFilter(query = "YELLOW")))
    }

    @Test fun filtersCombine() =
        assertEquals(emptyList<BookEntity>(),
            filterBooks(all, links, LibraryFilter(status = ReadingStatus.READING, categoryId = "scifi")))
}
```

`BookFormTest.kt`
```kotlin
package com.exc33ded.library.ui.book

import com.exc33ded.library.data.db.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookFormTest {
    @Test fun titleIsRequired() =
        assertEquals(FormError.TitleRequired, BookForm(title = "  ").validate())

    @Test fun pageCountMustBePositiveNumber() {
        assertEquals(FormError.BadPageCount, BookForm(title = "A", pageCount = "abc").validate())
        assertEquals(FormError.BadPageCount, BookForm(title = "A", pageCount = "0").validate())
        assertNull(BookForm(title = "A", pageCount = "").validate())
    }

    @Test fun isbnAcceptsDigitsAndHyphensOnly() {
        assertEquals(FormError.BadIsbn, BookForm(title = "A", isbn = "97A").validate())
        assertNull(BookForm(title = "A", isbn = "978-0-14-311774-2").validate())
    }

    @Test fun roundTripsThroughEntityAndTrims() {
        val form = BookForm(title = " Dune ", authors = "Frank Herbert", pageCount = "600", isbn = "978-0441013593")
        val e = form.applyTo(BookEntity(id = "x", title = ""))
        assertEquals("Dune", e.title)
        assertEquals(600, e.pageCount)
        assertEquals("9780441013593", e.isbn)
        assertEquals(form.copy(title = "Dune", isbn = "9780441013593"), BookForm.from(e))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.exc33ded.library.ui.*"`
Expected: FAIL — unresolved `filterBooks`, `BookForm`.

- [ ] **Step 3: Implement**

`ui/library/LibraryFilter.kt`
```kotlin
package com.exc33ded.library.ui.library

import com.exc33ded.library.data.db.BookCategoryEntity
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus

data class LibraryFilter(
    val status: ReadingStatus? = null,
    val categoryId: String? = null,
    val query: String = "",
)

fun filterBooks(books: List<BookEntity>, links: List<BookCategoryEntity>, f: LibraryFilter): List<BookEntity> {
    val inCategory = f.categoryId?.let { c -> links.filter { it.categoryId == c }.map { it.bookId }.toSet() }
    val q = f.query.trim()
    return books.filter { b ->
        (f.status == null || b.status == f.status) &&
            (inCategory == null || b.id in inCategory) &&
            (q.isEmpty() || b.title.contains(q, ignoreCase = true) || b.authors.contains(q, ignoreCase = true))
    }
}
```

`ui/book/BookForm.kt`
```kotlin
package com.exc33ded.library.ui.book

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus

enum class FormError { TitleRequired, BadPageCount, BadIsbn }

data class BookForm(
    val title: String = "",
    val authors: String = "",
    val isbn: String = "",
    val pageCount: String = "",
    val description: String = "",
    val notes: String = "",
    val status: ReadingStatus = ReadingStatus.WANT,
) {
    fun validate(): FormError? = when {
        title.isBlank() -> FormError.TitleRequired
        pageCount.isNotBlank() && (pageCount.trim().toIntOrNull() ?: 0) <= 0 -> FormError.BadPageCount
        isbn.isNotBlank() && !isbn.trim().matches(Regex("[0-9Xx-]+")) -> FormError.BadIsbn
        else -> null
    }

    fun applyTo(e: BookEntity): BookEntity = e.copy(
        title = title.trim(),
        authors = authors.trim(),
        isbn = isbn.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase().ifEmpty { null },
        pageCount = pageCount.trim().toIntOrNull(),
        description = description.trim().ifEmpty { null },
        notes = notes.trim().ifEmpty { null },
        status = status,
    )

    companion object {
        fun from(e: BookEntity) = BookForm(
            title = e.title,
            authors = e.authors,
            isbn = e.isbn.orEmpty(),
            pageCount = e.pageCount?.toString().orEmpty(),
            description = e.description.orEmpty(),
            notes = e.notes.orEmpty(),
            status = e.status,
        )
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src
git commit -m "feat(ui): library filter and book form validation"
```

---

### Task 5: Theme with Expressive motion

**Files:** Create `ui/theme/Theme.kt`

No unit test (pure UI wiring); verified visually in Task 10.

- [ ] **Step 1: Implement**

```kotlin
package com.exc33ded.library.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Spec §10: every animation pulls its spec from MaterialTheme.motionScheme.
 * minSdk 31 => dynamic color always available.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If `ExperimentalMaterial3ExpressiveApi` opt-in is reported as unnecessary, delete the `@OptIn` line.)

- [ ] **Step 3: Commit**

```powershell
git add app/src
git commit -m "feat(ui): expressive theme with dynamic color"
```

---

### Task 6: Shared UI pieces

**Files:** Create `ui/common/PlaceholderScreen.kt`, `ui/common/CoverPlaceholder.kt`, `ui/common/StatusSelector.kt`

- [ ] **Step 1: Implement**

`ui/common/PlaceholderScreen.kt`
```kotlin
package com.exc33ded.library.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// ponytail: stand-in until the phase that builds this tab
@Composable
fun PlaceholderScreen(text: String, modifier: Modifier = Modifier) =
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
```

`ui/common/CoverPlaceholder.kt`
```kotlin
package com.exc33ded.library.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Text "cover" used until real covers arrive in Phase 2. Colour is stable per title. */
@Composable
fun CoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )
    val (bg, fg) = palette[Math.floorMod(title.hashCode(), palette.size)]
    Box(
        modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(bg).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title, color = fg, style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
    }
}
```

`ui/common/StatusSelector.kt`
```kotlin
package com.exc33ded.library.ui.common

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.exc33ded.library.data.db.ReadingStatus

fun ReadingStatus.label() = when (this) {
    ReadingStatus.WANT -> "Want"
    ReadingStatus.READING -> "Reading"
    ReadingStatus.READ -> "Read"
    ReadingStatus.DNF -> "DNF"
}

/**
 * ponytail: SegmentedButton is stable and known-good; swap for M3 Expressive ButtonGroup in Phase 7.
 * `allowNone` adds an "All" segment (library filter); null = All.
 */
@Composable
fun StatusSelector(
    selected: ReadingStatus?,
    onSelect: (ReadingStatus?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
) {
    val options: List<ReadingStatus?> = (if (allowNone) listOf(null) else emptyList()) + ReadingStatus.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, s ->
            SegmentedButton(
                selected = s == selected,
                onClick = { onSelect(s) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(s?.label() ?: "All") }
        }
    }
}
```

- [ ] **Step 2: Compile and commit**

Run: `./gradlew compileDebugKotlin` → BUILD SUCCESSFUL
```powershell
git add app/src
git commit -m "feat(ui): placeholder cover, status selector"
```

---

### Task 7: Navigation shell (Nav3 + bottom bar + centre add button)

**Files:** Create `ui/nav/Keys.kt`, `ui/nav/AppNav.kt`; Modify `MainActivity.kt`

Screens referenced here are created in Tasks 8–9; to keep this task compiling on its own, it uses `PlaceholderScreen` for every destination. Tasks 8–9 replace the placeholders.

- [ ] **Step 1: `ui/nav/Keys.kt`**

```kotlin
package com.exc33ded.library.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object LibraryKey : NavKey
@Serializable data object FeedKey : NavKey
@Serializable data object StatsKey : NavKey
@Serializable data object ProfileKey : NavKey
@Serializable data class BookDetailKey(val id: String) : NavKey
@Serializable data class BookEditKey(val id: String? = null) : NavKey
@Serializable data object CategoriesKey : NavKey

val topLevelKeys: List<NavKey> = listOf(LibraryKey, FeedKey, StatsKey, ProfileKey)
```

- [ ] **Step 2: `ui/nav/AppNav.kt`**

```kotlin
package com.exc33ded.library.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.exc33ded.library.ui.common.PlaceholderScreen

@Composable
fun AppNav() {
    val backStack = rememberNavBackStack(LibraryKey)
    val top = backStack.lastOrNull()
    Scaffold(
        bottomBar = {
            AnimatedVisibility(visible = top in topLevelKeys) {
                BottomBar(current = top, onSelect = { backStack.switchTo(it) }, onAdd = { backStack.add(BookEditKey()) })
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<LibraryKey> { PlaceholderScreen("Library") }
                entry<FeedKey> { PlaceholderScreen("Feed — Phase 6") }
                entry<StatsKey> { PlaceholderScreen("Stats — Phase 5") }
                entry<ProfileKey> { PlaceholderScreen("Profile — Phase 5") }
                entry<BookDetailKey> { PlaceholderScreen("Book ${it.id}") }
                entry<BookEditKey> { PlaceholderScreen("Edit") }
                entry<CategoriesKey> { PlaceholderScreen("Categories") }
            },
        )
    }
}

/** Top-level tabs reset the stack to a single destination. */
private fun NavBackStack<NavKey>.switchTo(key: NavKey) {
    if (lastOrNull() == key) return
    clear(); add(key)
}

@Composable
private fun BottomBar(current: NavKey?, onSelect: (NavKey) -> Unit, onAdd: () -> Unit) {
    NavigationBar {
        Tab(current, LibraryKey, Icons.Filled.AutoStories, "Library", onSelect)
        Tab(current, FeedKey, Icons.Filled.RssFeed, "Feed", onSelect)
        // ponytail: plain FAB opening manual entry; Phase 2 makes it the scan button + FAB menu,
        // Phase 7 gives it the MaterialShapes cookie shape and spring press.
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add book") }
        }
        Tab(current, StatsKey, Icons.Filled.Insights, "Stats", onSelect)
        Tab(current, ProfileKey, Icons.Filled.Person, "Profile", onSelect)
    }
}

@Composable
private fun RowScope.Tab(current: NavKey?, key: NavKey, icon: ImageVector, label: String, onSelect: (NavKey) -> Unit) =
    NavigationBarItem(
        selected = current == key,
        onClick = { onSelect(key) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
```

- [ ] **Step 3: `MainActivity.kt` uses theme + nav**

Replace the `setContent { Text("Library") }` line and the `Text` import:
```kotlin
package com.exc33ded.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.exc33ded.library.ui.nav.AppNav
import com.exc33ded.library.ui.theme.LibraryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LibraryTheme { AppNav() } }
    }
}
```

- [ ] **Step 4: Build and commit**

Run: `./gradlew assembleDebug` → BUILD SUCCESSFUL. If a Nav3 import fails to resolve, check the 1.2.x API reference (developer.android.com/reference/kotlin/androidx/navigation3/ui/package-summary) — names above match Navigation 3 1.0+.
```powershell
git add app/src
git commit -m "feat(nav): Navigation 3 shell with bottom bar and add button"
```

---

### Task 8: Library screen

**Files:** Create `ui/library/LibraryViewModel.kt`, `ui/library/LibraryScreen.kt`; Modify `ui/nav/AppNav.kt` (LibraryKey entry)

- [ ] **Step 1: ViewModel**

```kotlin
package com.exc33ded.library.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import com.exc33ded.library.data.db.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val filter: LibraryFilter = LibraryFilter(),
    val totalBooks: Int = 0,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(repo: BookRepository) : ViewModel() {
    private val filter = MutableStateFlow(LibraryFilter())

    val state: StateFlow<LibraryUiState> = combine(
        repo.observeBooks(), repo.observeCategories(), repo.observeLinks(), filter,
    ) { books, cats, links, f ->
        LibraryUiState(filterBooks(books, links, f), cats, f, books.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setStatus(s: ReadingStatus?) = filter.update { it.copy(status = s) }
    fun setCategory(id: String?) = filter.update { it.copy(categoryId = id) }
    fun setQuery(q: String) = filter.update { it.copy(query = q) }
}
```

- [ ] **Step 2: Screen**

```kotlin
package com.exc33ded.library.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.ui.common.CoverPlaceholder
import com.exc33ded.library.ui.common.PlaceholderScreen
import com.exc33ded.library.ui.common.StatusSelector

@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onManageCategories: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = s.filter.query, onValueChange = vm::setQuery, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search title or author") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        StatusSelector(s.filter.status, vm::setStatus, Modifier.fillMaxWidth(), allowNone = true)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(s.filter.categoryId == null, { vm.setCategory(null) }, { Text("All") }) }
            items(s.categories, key = { it.id }) { c ->
                FilterChip(s.filter.categoryId == c.id, { vm.setCategory(c.id) }, { Text(c.name) })
            }
            item { FilterChip(false, onManageCategories, { Text("Edit…") }) }
        }
        when {
            s.totalBooks == 0 -> PlaceholderScreen("No books yet — tap + to add one")
            s.books.isEmpty() -> PlaceholderScreen("No books match")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(s.books, key = { it.id }) { b ->
                    BookTile(b, Modifier.animateItem().clickable { onOpenBook(b.id) })
                }
            }
        }
    }
}

@Composable
private fun BookTile(b: BookEntity, modifier: Modifier) = Column(modifier) {
    CoverPlaceholder(b.title)
    Text(b.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (b.authors.isNotBlank())
        Text(b.authors, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
```

- [ ] **Step 3: Wire into AppNav**

In `ui/nav/AppNav.kt` replace `entry<LibraryKey> { PlaceholderScreen("Library") }` with:
```kotlin
                entry<LibraryKey> {
                    LibraryScreen(
                        onOpenBook = { id -> backStack.add(BookDetailKey(id)) },
                        onManageCategories = { backStack.add(CategoriesKey) },
                    )
                }
```
and add `import com.exc33ded.library.ui.library.LibraryScreen`.

- [ ] **Step 4: Build, run tests, commit**

Run: `./gradlew testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL
```powershell
git add app/src
git commit -m "feat(library): grid with search, status and category filters"
```

---

### Task 9: Add/edit, detail, and categories screens

**Files:** Create `ui/book/BookEditViewModel.kt`, `ui/book/BookEditScreen.kt`, `ui/book/BookDetailViewModel.kt`, `ui/book/BookDetailScreen.kt`, `ui/categories/CategoriesViewModel.kt`, `ui/categories/CategoriesScreen.kt`; Modify `ui/nav/AppNav.kt`

- [ ] **Step 1: `ui/book/BookEditViewModel.kt`**

```kotlin
package com.exc33ded.library.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.data.Ids
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
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
    val error: FormError? = null,
    val savedId: String? = null,
)

@HiltViewModel(assistedFactory = BookEditViewModel.Factory::class)
class BookEditViewModel @AssistedInject constructor(
    private val repo: BookRepository,
    @Assisted private val bookId: String?,
) : ViewModel() {
    @AssistedFactory interface Factory { fun create(bookId: String?): BookEditViewModel }

    private var original: BookEntity = BookEntity(id = bookId ?: Ids.new(), title = "")
    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state

    init {
        viewModelScope.launch {
            bookId?.let { id ->
                repo.getBook(id)?.let { original = it }
                _state.update { it.copy(form = BookForm.from(original), selectedCategories = repo.categoryIdsFor(id)) }
            }
        }
        viewModelScope.launch { repo.observeCategories().collect { c -> _state.update { it.copy(categories = c) } } }
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
        s.form.validate()?.let { e -> _state.update { it.copy(error = e) }; return }
        viewModelScope.launch {
            val id = repo.saveBook(s.form.applyTo(original), s.selectedCategories)
            _state.update { it.copy(savedId = id) }
        }
    }
}
```

- [ ] **Step 2: `ui/book/BookEditScreen.kt`**

```kotlin
package com.exc33ded.library.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.ui.common.StatusSelector

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookEditScreen(
    bookId: String?,
    onDone: () -> Unit,
    vm: BookEditViewModel = hiltViewModel<BookEditViewModel, BookEditViewModel.Factory>(
        creationCallback = { it.create(bookId) },
    ),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.savedId) { if (s.savedId != null) onDone() }
    var newCategory by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (bookId == null) "Add book" else "Edit book") },
            navigationIcon = { IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { TextButton(vm::save) { Text("Save") } },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val f = s.form
            OutlinedTextField(f.title, { v -> vm.edit { it.copy(title = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Title *") }, isError = s.error == FormError.TitleRequired, singleLine = true)
            OutlinedTextField(f.authors, { v -> vm.edit { it.copy(authors = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Author(s)") }, singleLine = true)
            OutlinedTextField(f.isbn, { v -> vm.edit { it.copy(isbn = v) } }, Modifier.fillMaxWidth(),
                label = { Text("ISBN") }, isError = s.error == FormError.BadIsbn, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(f.pageCount, { v -> vm.edit { it.copy(pageCount = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Pages") }, isError = s.error == FormError.BadPageCount, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("Status", style = MaterialTheme.typography.labelLarge)
            StatusSelector(f.status, { st -> if (st != null) vm.edit { it.copy(status = st) } }, Modifier.fillMaxWidth())
            Text("Categories", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.categories.forEach { c ->
                    FilterChip(c.id in s.selectedCategories, { vm.toggleCategory(c.id) }, { Text(c.name) })
                }
            }
            OutlinedTextField(newCategory, { newCategory = it }, Modifier.fillMaxWidth(),
                label = { Text("New category") }, singleLine = true,
                trailingIcon = { TextButton({ vm.addCategory(newCategory); newCategory = "" }) { Text("Add") } })
            OutlinedTextField(f.description, { v -> vm.edit { it.copy(description = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Description") }, minLines = 3)
            OutlinedTextField(f.notes, { v -> vm.edit { it.copy(notes = v) } }, Modifier.fillMaxWidth(),
                label = { Text("My notes") }, minLines = 2)
        }
    }
}
```

- [ ] **Step 3: `ui/book/BookDetailViewModel.kt`**

```kotlin
package com.exc33ded.library.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.data.db.ReadingStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = BookDetailViewModel.Factory::class)
class BookDetailViewModel @AssistedInject constructor(
    private val repo: BookRepository,
    @Assisted private val bookId: String,
) : ViewModel() {
    @AssistedFactory interface Factory { fun create(bookId: String): BookDetailViewModel }

    val book = repo.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setStatus(s: ReadingStatus) = viewModelScope.launch { repo.setStatus(bookId, s) }
    fun setProgress(page: Int) = viewModelScope.launch { repo.setProgress(bookId, page) }
    fun delete(onDeleted: () -> Unit) = viewModelScope.launch { repo.deleteBook(bookId); onDeleted() }
}
```

- [ ] **Step 4: `ui/book/BookDetailScreen.kt`**

```kotlin
package com.exc33ded.library.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.ui.common.CoverPlaceholder
import com.exc33ded.library.ui.common.StatusSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    vm: BookDetailViewModel = hiltViewModel<BookDetailViewModel, BookDetailViewModel.Factory>(
        creationCallback = { it.create(bookId) },
    ),
) {
    val book by vm.book.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onEdit) { Icon(Icons.Filled.Edit, "Edit") }
                IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
            },
        )
    }) { pad ->
        val b = book ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverPlaceholder(b.title, Modifier.width(160.dp))
            Text(b.title, style = MaterialTheme.typography.headlineSmall)
            if (b.authors.isNotBlank()) Text(b.authors, style = MaterialTheme.typography.titleMedium)
            StatusSelector(b.status, { if (it != null) vm.setStatus(it) }, Modifier.fillMaxWidth())
            val pages = b.pageCount
            if (b.status == ReadingStatus.READING && pages != null) {
                var page by remember(b.currentPage) { mutableFloatStateOf(b.currentPage.toFloat()) }
                Text("Page ${page.toInt()} of $pages", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = page, onValueChange = { page = it },
                    onValueChangeFinished = { vm.setProgress(page.toInt()) },
                    valueRange = 0f..pages.toFloat(),
                )
            }
            b.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            b.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this book?") },
        confirmButton = { TextButton({ confirmDelete = false; vm.delete(onBack) }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}
```

- [ ] **Step 5: `ui/categories/CategoriesViewModel.kt`**

```kotlin
package com.exc33ded.library.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val repo: BookRepository) : ViewModel() {
    val categories = repo.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String) { if (name.isNotBlank()) viewModelScope.launch { repo.saveCategory(name) } }
    fun rename(id: String, name: String) { if (name.isNotBlank()) viewModelScope.launch { repo.saveCategory(name, id) } }
    fun delete(id: String) = viewModelScope.launch { repo.deleteCategory(id) }
}
```

- [ ] **Step 6: `ui/categories/CategoriesScreen.kt`**

```kotlin
package com.exc33ded.library.ui.categories

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    var newName by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Categories") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            item {
                OutlinedTextField(newName, { newName = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("New category") },
                    trailingIcon = { TextButton({ vm.add(newName); newName = "" }) { Text("Add") } })
            }
            items(cats, key = { it.id }) { c ->
                var name by remember(c.name) { mutableStateOf(c.name) }
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth().animateItem().padding(top = 8.dp), singleLine = true,
                    trailingIcon = {
                        if (name != c.name) TextButton({ vm.rename(c.id, name) }) { Text("Save") }
                        else IconButton({ vm.delete(c.id) }) { Icon(Icons.Filled.Delete, "Delete ${c.name}") }
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 7: Wire destinations in `ui/nav/AppNav.kt`**

Replace the three placeholder entries for `BookDetailKey`, `BookEditKey`, `CategoriesKey` with:
```kotlin
                entry<BookDetailKey> { key ->
                    BookDetailScreen(
                        bookId = key.id,
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { backStack.add(BookEditKey(key.id)) },
                    )
                }
                entry<BookEditKey> { key -> BookEditScreen(bookId = key.id, onDone = { backStack.removeLastOrNull() }) }
                entry<CategoriesKey> { CategoriesScreen(onBack = { backStack.removeLastOrNull() }) }
```
Add imports:
```kotlin
import com.exc33ded.library.ui.book.BookDetailScreen
import com.exc33ded.library.ui.book.BookEditScreen
import com.exc33ded.library.ui.categories.CategoriesScreen
```

- [ ] **Step 8: Build, test, commit**

Run: `./gradlew testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL, all tests PASS.
```powershell
git add app/src
git commit -m "feat(book): add/edit form, detail with status+progress, categories management"
```

---

### Task 10: On-device check and push

- [ ] **Step 1: Install on the phone**

Enable Developer options + USB debugging on the Galaxy A07, connect USB, then:
```powershell
adb devices          # phone listed as "device"
./gradlew installDebug
```

- [ ] **Step 2: Manual acceptance checklist** (all must pass)

1. App opens on Library with "No books yet — tap + to add one", dynamic colours from wallpaper.
2. + → add "Yellowface" / "Rebecca F. Kuang" / 320 pages / category "Fiction" (created inline) → Save → tile appears with placeholder cover (animated insertion).
3. Saving with empty title shows the title field in error and does not leave the screen.
4. Tap tile → detail → set "Reading" → slider appears → drag to 320 → status flips to "Read".
5. Library status filter "Read" shows it; "Want" hides it; search "kuang" finds it; category chip "Fiction" filters.
6. Edit → change title → Save → detail shows new title.
7. Delete → confirm → back on Library, book gone.
8. Categories "Edit…" → rename and delete a category; its chip disappears from the library.
9. Predictive back: start a back swipe on detail — the system back preview animates (no root BackHandler).
10. Kill and reopen app — data persists.

- [ ] **Step 3: Push**

```powershell
git push
```

---

## Later phases (separate plans, written when Phase 1 is done)

| Phase | Plan file | Contents |
|---|---|---|
| 2 | `2026-xx-xx-phase-2-capture-and-metadata.md` | Code Scanner, CameraX cover capture, Photo Picker batch import + `import_queue` + WorkManager, EXIF/rotation-aware ML Kit OCR, Open Library + Google Books (`intitle:`/`inauthor:`) lookup, Coil covers, review cards; labels.csv for `docs/Books-Photo` |
| 3 | `…-phase-3-on-device-ai.md` | LiteRT-LM 0.19.1, model allowlist + download worker, Qwen3-0.6B summary, optional LFM2.5-VL / Gemma 4 E2B cover reader, timing probe, benchmark on the 96 photos |
| 4 | `…-phase-4-supabase-sync.md` | Supabase schema + RLS + triggers, silent sign-in, LWW push/pull worker, cover storage, export/import zip |
| 5 | `…-phase-5-profile.md` | reading_log, goal ring, Vico stats, streaks, badges |
| 6 | `…-phase-6-feed.md` | RSS-Parser feeds, followed authors poller |
| 7 | `…-phase-7-motion-polish.md` | shared-element cover→detail, pull tension, swipe-to-status, ButtonGroup, cookie camera button, haptics, baseline profile |
