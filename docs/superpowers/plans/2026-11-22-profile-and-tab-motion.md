# Profile + Tab Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **Workers (user decision 2026-11-22):** the main session is the head: it writes this plan, hands each task to a worker, then reviews the diff, re-runs verification and commits. **Tasks 1–3 → opencode** (`opencode run --auto -m opencode/hy4-flash-free "<prompt>"`, free model, treat as an intern: give it the task text verbatim). **Tasks 4–5 → Claude subagent on Sonnet.** Workers never commit. Never give any worker `local.properties` or anything under `docs/Books-Photo/`.

**Goal:** A real Profile tab (name + photo, yearly reading goal, stats summary, bio) and a slide + fade when switching between Library · Feed · Stats · Profile.

**Architecture:** `ProfileRepository` stores profile fields in the *existing* settings DataStore (distinct `profile_*` keys; deviation from the spec's separate file — one store is simpler). `MainActivity` collects the profile once and exposes it as `LocalProfile` (same reason as `LocalAppSettings`: tab switches clear ViewModels). Stats are a pure function over the books list. Tab motion lives in `AppNav`'s `NavDisplay.transitionSpec`, driven by a `tabDirection` state set just before a tab switch.

**Tech Stack:** Kotlin, Compose Material 3 (Expressive), DataStore Preferences, Hilt, Coil 3 (`coil3.compose.AsyncImage`), Navigation 3.

Spec: `docs/superpowers/specs/2026-11-22-profile-and-tab-motion-design.md`.

## Toolchain (each PowerShell call)

```powershell
$env:JAVA_HOME="$env:USERPROFILE\scoop\apps\temurin21-jdk\current"
$env:ANDROID_HOME="$env:USERPROFILE\scoop\apps\android-clt\current"
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue   # preset value has no Gradle distribution
cd D:\claude-exp\words-and-wounds
```

Paths: main = `app/src/main/java/com/exc33ded/library/`, test = `app/src/test/java/com/exc33ded/library/`. Baseline before this plan: 87 unit tests pass.

---

### Task 1: Profile model, pure helpers, repository (worker: opencode)

**Files:**
- Create: `profile/Profile.kt`, `profile/ProfileRepository.kt` (main)
- Test: `profile/ProfileTest.kt`, `profile/ProfileRepositoryTest.kt` (test)

- [ ] **Step 1: Write the tests.** `profile/ProfileTest.kt`:

```kotlin
package com.exc33ded.library.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTest {
    @Test fun nameIsTrimmedAndCapped() {
        assertEquals("", clampName("   "))
        assertEquals("Sam", clampName("  Sam "))
        assertEquals(NAME_MAX, clampName("x".repeat(100)).length)
    }

    @Test fun bioIsTrimmedAndCapped() {
        assertEquals("hi", clampBio(" hi "))
        assertEquals(BIO_MAX, clampBio("y".repeat(500)).length)
    }

    @Test fun goalMustBePositiveAndIsCapped() {
        assertNull(clampGoal(null))
        assertNull(clampGoal(0))
        assertNull(clampGoal(-3))
        assertEquals(24, clampGoal(24))
        assertEquals(GOAL_MAX, clampGoal(5000))
    }

    @Test fun goalTextParsing() {
        assertEquals(24, parseGoal("24"))
        assertEquals(24, parseGoal(" 24 "))
        assertNull(parseGoal(""))
        assertNull(parseGoal("abc"))
        assertNull(parseGoal("0"))
    }

    @Test fun initials() {
        assertEquals("SK", initials("Sarim Khan"))
        assertEquals("S", initials("sarim"))
        assertEquals("AB", initials("a b c"))
        assertEquals("?", initials("   "))
    }
}
```

`profile/ProfileRepositoryTest.kt`:

```kotlin
package com.exc33ded.library.profile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo() = ProfileRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            File(tmp.root, "settings.preferences_pb")
        },
    )

    @Test fun emptyStoreGivesDefaults() = runBlocking {
        assertEquals(Profile(), repo().profile.first())
    }

    @Test fun updatePersistsAllFields() = runBlocking {
        val r = repo()
        r.update { it.copy(name = "Sam", bio = "Read more", yearlyGoal = 24, avatarPath = "/x/avatar.jpg") }
        assertEquals(Profile("Sam", "Read more", 24, "/x/avatar.jpg"), r.profile.first())
    }

    @Test fun nullGoalAndAvatarRemoveTheirKeys() = runBlocking {
        val r = repo()
        r.update { it.copy(yearlyGoal = 12, avatarPath = "/x/a.jpg") }
        r.update { it.copy(yearlyGoal = null, avatarPath = null) }
        val p = r.profile.first()
        assertNull(p.yearlyGoal)
        assertNull(p.avatarPath)
    }

    @Test fun fieldsAreClampedOnWrite() = runBlocking {
        val r = repo()
        r.update { it.copy(name = "n".repeat(100), bio = "b".repeat(500), yearlyGoal = 99999) }
        val p = r.profile.first()
        assertEquals(NAME_MAX, p.name.length)
        assertEquals(BIO_MAX, p.bio.length)
        assertEquals(GOAL_MAX, p.yearlyGoal)
    }
}
```

- [ ] **Step 2: Run, expect FAIL (unresolved references):** `.\gradlew.bat testDebugUnitTest --tests "*.ProfileTest" --tests "*.ProfileRepositoryTest"`

- [ ] **Step 3: Implement.** `profile/Profile.kt`:

```kotlin
package com.exc33ded.library.profile

const val NAME_MAX = 40
const val BIO_MAX = 140
const val GOAL_MAX = 1000

data class Profile(
    val name: String = "",
    val bio: String = "",
    val yearlyGoal: Int? = null,
    val avatarPath: String? = null,
)

fun clampName(s: String): String = s.trim().take(NAME_MAX)
fun clampBio(s: String): String = s.trim().take(BIO_MAX)

/** null (no goal) unless the value is a positive number; capped at [GOAL_MAX]. */
fun clampGoal(n: Int?): Int? = n?.takeIf { it >= 1 }?.coerceAtMost(GOAL_MAX)

/** Goal text field -> goal: blank, non-numeric or non-positive clears the goal. */
fun parseGoal(text: String): Int? = clampGoal(text.trim().toIntOrNull())

/** Up to two initials for the avatar placeholder; "?" for a blank name. */
fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (parts.isEmpty()) "?" else parts.take(2).joinToString("") { it.first().uppercase() }
}
```

`profile/ProfileRepository.kt`:

```kotlin
package com.exc33ded.library.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Profile fields live in the same DataStore as the app settings, under `profile_*` keys. */
@Singleton
class ProfileRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val profile: Flow<Profile> = store.data.map(::read)

    suspend fun update(transform: (Profile) -> Profile) {
        store.edit { p ->
            val n = transform(read(p))
            p[NAME] = clampName(n.name)
            p[BIO] = clampBio(n.bio)
            val goal = clampGoal(n.yearlyGoal)
            if (goal != null) p[GOAL] = goal else p.remove(GOAL)
            val avatar = n.avatarPath
            if (avatar != null) p[AVATAR] = avatar else p.remove(AVATAR)
        }
    }

    private fun read(p: Preferences) = Profile(p[NAME] ?: "", p[BIO] ?: "", p[GOAL], p[AVATAR])

    private companion object {
        val NAME = stringPreferencesKey("profile_name")
        val BIO = stringPreferencesKey("profile_bio")
        val GOAL = intPreferencesKey("profile_goal")
        val AVATAR = stringPreferencesKey("profile_avatar")
    }
}
```

- [ ] **Step 4: Run, expect PASS** (same command as step 2), then `.\gradlew.bat testDebugUnitTest` — all tests pass (87 + 9 new). Do NOT commit; the main session commits `feat(profile): profile model and repository`.

---

### Task 2: Profile stats (worker: opencode)

**Files:**
- Create: `profile/ProfileStats.kt` (main)
- Test: `profile/ProfileStatsTest.kt` (test)

- [ ] **Step 1: Write the test** — `profile/ProfileStatsTest.kt`:

```kotlin
package com.exc33ded.library.profile

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProfileStatsTest {
    private fun millis(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private val now = millis(2026, 6, 15)

    private fun book(id: String, status: ReadingStatus, pages: Int? = null, page: Int = 0, rating: Int? = null, finished: Long? = null) =
        BookEntity(id = id, title = id, status = status, pageCount = pages, currentPage = page, rating = rating, finishedAt = finished)

    @Test fun emptyLibrary() {
        val s = computeStats(emptyList(), now, ZoneOffset.UTC)
        assertEquals(ProfileStats(0, 0, 0, 0, 0, null, 0), s)
    }

    @Test fun countsPagesAndRating() {
        val books = listOf(
            book("a", ReadingStatus.READ, pages = 300, rating = 5, finished = millis(2026, 2, 1)),
            book("b", ReadingStatus.READ, pages = 200, rating = 3, finished = millis(2026, 3, 1)),
            book("c", ReadingStatus.READING, pages = 400, page = 120),
            book("d", ReadingStatus.WANT, pages = 999),
            book("e", ReadingStatus.DNF, pages = 500, page = 50),
        )
        val s = computeStats(books, now, ZoneOffset.UTC)
        assertEquals(2, s.read); assertEquals(1, s.reading); assertEquals(1, s.want); assertEquals(1, s.dnf)
        assertEquals(300 + 200 + 120, s.pagesRead) // READ = page count, READING = current page, others 0
        assertEquals(4.0, s.averageRating!!, 1e-9)
        assertEquals(2, s.readThisYear)
    }

    @Test fun readThisYearUsesTheCalendarYearBoundary() {
        val books = listOf(
            book("new", ReadingStatus.READ, finished = millis(2026, 1, 1)),                 // first instant of 2026: counts
            book("old", ReadingStatus.READ, finished = millis(2026, 1, 1) - 1),             // last millisecond of 2025: does not
            book("unknown", ReadingStatus.READ, finished = null),                           // no date: does not
            book("notRead", ReadingStatus.READING, finished = millis(2026, 3, 3)),          // wrong status: does not
        )
        assertEquals(1, computeStats(books, now, ZoneOffset.UTC).readThisYear)
    }

    @Test fun noRatingsMeansNullAverage() =
        assertNull(computeStats(listOf(book("a", ReadingStatus.READ)), now, ZoneOffset.UTC).averageRating)
}
```

- [ ] **Step 2: Run, expect FAIL:** `.\gradlew.bat testDebugUnitTest --tests "*.ProfileStatsTest"`

- [ ] **Step 3: Implement** — `profile/ProfileStats.kt`:

```kotlin
package com.exc33ded.library.profile

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import java.time.Instant
import java.time.ZoneId

data class ProfileStats(
    val read: Int,
    val reading: Int,
    val want: Int,
    val dnf: Int,
    val pagesRead: Int,
    val averageRating: Double?,
    val readThisYear: Int,
)

fun computeStats(books: List<BookEntity>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ProfileStats {
    fun yearOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).year
    val thisYear = yearOf(nowMillis)
    val ratings = books.mapNotNull { it.rating }
    return ProfileStats(
        read = books.count { it.status == ReadingStatus.READ },
        reading = books.count { it.status == ReadingStatus.READING },
        want = books.count { it.status == ReadingStatus.WANT },
        dnf = books.count { it.status == ReadingStatus.DNF },
        pagesRead = books.sumOf {
            when (it.status) {
                ReadingStatus.READ -> it.pageCount ?: 0
                ReadingStatus.READING -> it.currentPage
                else -> 0
            }
        },
        averageRating = ratings.takeIf { it.isNotEmpty() }?.average(),
        readThisYear = books.count { it.status == ReadingStatus.READ && it.finishedAt?.let(::yearOf) == thisYear },
    )
}
```

- [ ] **Step 4: Run, expect PASS**, then the whole suite. Do NOT commit; main session commits `feat(profile): stats summary`.

---

### Task 3: Avatar storage (worker: opencode)

**Files:**
- Create: `profile/AvatarStore.kt` (main)
- Test: `profile/AvatarStoreTest.kt` (test) — only the pure crop maths is unit-tested; the rest is verified by building and by the main session on the emulator.

- [ ] **Step 1: Test** — `profile/AvatarStoreTest.kt`:

```kotlin
package com.exc33ded.library.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarStoreTest {
    @Test fun landscapeCropsTheSides() = assertEquals(Triple(100, 0, 200), centerSquare(400, 200))
    @Test fun portraitCropsTopAndBottom() = assertEquals(Triple(0, 150, 200), centerSquare(200, 500))
    @Test fun squareIsUntouched() = assertEquals(Triple(0, 0, 300), centerSquare(300, 300))
}
```

- [ ] **Step 2: Run, expect FAIL:** `.\gradlew.bat testDebugUnitTest --tests "*.AvatarStoreTest"`

- [ ] **Step 3: Implement** — `profile/AvatarStore.kt`:

```kotlin
package com.exc33ded.library.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Largest centred square inside a w x h image, as (left, top, size). */
fun centerSquare(w: Int, h: Int): Triple<Int, Int, Int> {
    val s = minOf(w, h)
    return Triple((w - s) / 2, (h - s) / 2, s)
}

@Singleton
class AvatarStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val dir: File get() = File(ctx.filesDir, "profile").apply { mkdirs() }

    /**
     * Decodes [uri] (EXIF-rotated), centre-crops to a square, scales to at most 512 px and saves it under a
     * new file name (so Coil never serves a stale image), deleting any older avatar. Returns the new path.
     */
    suspend fun save(uri: Uri): String = withContext(Dispatchers.IO) {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, _, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val (x, y, s) = centerSquare(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, x, y, s, s)
        val size = minOf(s, 512)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        val out = File(dir, "avatar-${System.currentTimeMillis()}.jpg")
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        dir.listFiles()?.filter { it != out }?.forEach { it.delete() }
        listOf(decoded, square, scaled).distinct().forEach { it.recycle() }
        out.absolutePath
    }

    fun remove() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
```

- [ ] **Step 4: Run, expect PASS**, then `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleDebug` (Hilt graph compiles). Do NOT commit; main session commits `feat(profile): avatar storage`.

---

### Task 4: Profile UI, LocalProfile, Library greeting (worker: Sonnet subagent)

**Files:**
- Create: `ui/profile/LocalProfile.kt`, `ui/profile/ProfileViewModel.kt`, `ui/profile/EditProfileSheet.kt`
- Modify (rewrite): `ui/profile/ProfileScreen.kt`
- Modify: `MainActivity.kt`, `ui/library/LibraryScreen.kt`, `ui/nav/AppNav.kt` (only the `ProfileScreen(...)` call stays as it is — signature `ProfileScreen(onOpenSettings = ...)` is unchanged, so AppNav needs no edit in this task)

- [ ] **Step 1: `ui/profile/LocalProfile.kt`**

```kotlin
package com.exc33ded.library.ui.profile

import androidx.compose.runtime.compositionLocalOf
import com.exc33ded.library.profile.Profile

/** Collected once in MainActivity: tab switches clear ViewModels, so screens must not collect their own copy. */
val LocalProfile = compositionLocalOf { Profile() }
```

- [ ] **Step 2: `MainActivity.kt`** — add `@Inject lateinit var profileRepo: ProfileRepository` next to `settingsRepo`; in `setContent` add `val profile by profileRepo.profile.collectAsStateWithLifecycle(Profile())` and change the content line to:

```kotlin
LibraryTheme(settings) { CompositionLocalProvider(LocalProfile provides profile) { AppNav() } }
```
(imports: `androidx.compose.runtime.CompositionLocalProvider`, `com.exc33ded.library.profile.Profile`, `com.exc33ded.library.profile.ProfileRepository`, `com.exc33ded.library.ui.profile.LocalProfile`.)

- [ ] **Step 3: `ui/profile/ProfileViewModel.kt`**

```kotlin
package com.exc33ded.library.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.profile.AvatarStore
import com.exc33ded.library.profile.ProfileRepository
import com.exc33ded.library.profile.ProfileStats
import com.exc33ded.library.profile.computeStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: ProfileRepository,
    books: BookRepository,
    private val avatars: AvatarStore,
) : ViewModel() {
    val stats: StateFlow<ProfileStats> = books.observeBooks()
        .map { computeStats(it, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), computeStats(emptyList(), System.currentTimeMillis()))

    fun save(name: String, bio: String, goal: Int?) {
        viewModelScope.launch { repo.update { it.copy(name = name, bio = bio, yearlyGoal = goal) } }
    }

    fun setAvatar(uri: Uri) {
        viewModelScope.launch {
            val path = avatars.save(uri)
            repo.update { it.copy(avatarPath = path) }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            avatars.remove()
            repo.update { it.copy(avatarPath = null) }
        }
    }
}
```

- [ ] **Step 4: `ui/profile/ProfileScreen.kt`** (replace the whole file)

```kotlin
package com.exc33ded.library.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.exc33ded.library.profile.Profile
import com.exc33ded.library.profile.ProfileStats
import com.exc33ded.library.profile.initials
import java.io.File

@Composable
fun ProfileScreen(onOpenSettings: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val profile = LocalProfile.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(profile, Modifier.size(88.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name.ifBlank { "Your name" },
                    style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (profile.bio.isNotBlank())
                    Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FilledTonalButton(onClick = { editing = true }) { Text("Edit profile") }
        GoalCard(stats.readThisYear, profile.yearlyGoal) { editing = true }
        StatsCard(stats)
        ListItem(
            headlineContent = { Text("Settings") },
            leadingContent = { Icon(Icons.Filled.Settings, null) },
            modifier = Modifier.clickable(onClick = onOpenSettings),
        )
    }
    if (editing) EditProfileSheet(
        profile = profile,
        onDismiss = { editing = false },
        onSave = { n, b, g -> vm.save(n, b, g); editing = false },
        onPickPhoto = vm::setAvatar,
        onRemovePhoto = vm::removeAvatar,
    )
}

@Composable
fun Avatar(profile: Profile, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        val path = profile.avatarPath
        if (path != null) {
            AsyncImage(File(path), "Profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(initials(profile.name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun GoalCard(readThisYear: Int, goal: Int?, onSetGoal: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (goal == null) {
            Column(Modifier.weight(1f)) {
                Text("Reading goal", style = MaterialTheme.typography.titleMedium)
                Text("Set a goal for this year", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onSetGoal) { Text("Set goal") }
        } else {
            val fraction = (readThisYear.toFloat() / goal).coerceIn(0f, 1f)
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            Column {
                Text("$readThisYear of $goal books this year", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (readThisYear >= goal) "Goal reached!" else "${goal - readThisYear} to go",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(s: ProfileStats) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Stats", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(s.read, "Read"); Stat(s.reading, "Reading"); Stat(s.want, "Want"); Stat(s.dnf, "DNF")
        }
        Text(
            "${s.pagesRead} pages read" + (s.averageRating?.let { " · average rating ${"%.1f".format(it)}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Stat(n: Int, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("$n", style = MaterialTheme.typography.headlineSmall)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

- [ ] **Step 5: `ui/profile/EditProfileSheet.kt`**

```kotlin
package com.exc33ded.library.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.exc33ded.library.profile.BIO_MAX
import com.exc33ded.library.profile.NAME_MAX
import com.exc33ded.library.profile.Profile
import com.exc33ded.library.profile.parseGoal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
    profile: Profile,
    onDismiss: () -> Unit,
    onSave: (name: String, bio: String, goal: Int?) -> Unit,
    onPickPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var bio by remember { mutableStateOf(profile.bio) }
    var goal by remember { mutableStateOf(profile.yearlyGoal?.toString() ?: "") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPickPhoto(uri) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(profile, Modifier.size(64.dp))
                OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text("Choose photo")
                }
                if (profile.avatarPath != null) TextButton(onClick = onRemovePhoto) { Text("Remove") }
            }
            OutlinedTextField(
                name, { name = it.take(NAME_MAX) }, Modifier.fillMaxWidth(),
                label = { Text("Name") }, singleLine = true,
            )
            OutlinedTextField(
                bio, { bio = it.take(BIO_MAX) }, Modifier.fillMaxWidth(),
                label = { Text("Bio or favourite quote") }, supportingText = { Text("${bio.length}/$BIO_MAX") },
            )
            OutlinedTextField(
                goal, { goal = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(),
                label = { Text("Books to read this year") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(name, bio, parseGoal(goal)) }) { Text("Save") }
            }
        }
    }
}
```

- [ ] **Step 6: Library greeting** — in `ui/library/LibraryScreen.kt`, inside the top-level `Column`, immediately before the `Row` that holds the search field, add:

```kotlin
        val profile = LocalProfile.current
        if (profile.name.isNotBlank())
            Text("Hi, ${profile.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
```
and add imports `androidx.compose.material3.MaterialTheme` (it was removed earlier) and `com.exc33ded.library.ui.profile.LocalProfile`. Change the `Row`'s `Modifier.padding(top = 8.dp)` to `Modifier` only when the greeting is shown is NOT required — leave the Row as is.

- [ ] **Step 7: Verify** — `.\gradlew.bat testDebugUnitTest` (all pass, no new tests in this task) and `.\gradlew.bat assembleDebug`. If an icon, `Card`, `FilledTonalButton` or `AsyncImage` import does not resolve, use the nearest equivalent already used in the project and report it. Do NOT commit; main session commits `feat(profile): profile screen, edit sheet, library greeting`.

---

### Task 5: Tab slide + fade in AppNav (worker: Sonnet subagent)

**Files:**
- Modify: `ui/nav/AppNav.kt`

Background (from the Navigation 3 docs): `NavDisplay(transitionSpec = {...})` is the transition used when the back stack grows or is replaced (forward); `popTransitionSpec` is used when it shrinks (back). A tab switch calls `backStack.switchTo(key)` = `clear(); add(key)` — a replace, so `transitionSpec` applies to it. The lambda cannot know which tab we are coming from, so `AppNav` keeps a `tabDirection` state (−1 / +1 for a tab switch, 0 for a push) that is set *immediately before* the stack changes.

- [ ] **Step 1: Add imports** to `AppNav.kt`: `androidx.compose.animation.fadeIn`, `androidx.compose.animation.fadeOut`, `androidx.compose.animation.slideInHorizontally`, `androidx.compose.animation.slideOutHorizontally`, `androidx.compose.animation.togetherWith`, `androidx.compose.material3.ExperimentalMaterial3ExpressiveApi`, `androidx.compose.material3.MaterialTheme`, `androidx.compose.runtime.mutableIntStateOf`, `androidx.compose.runtime.getValue`/`setValue` (already present), `androidx.compose.ui.unit.IntOffset`.

- [ ] **Step 2: State, `push` helper and motion specs.** Add `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` above `fun AppNav()`. Immediately after `val backStack = rememberNavBackStack(LibraryKey)` add:

```kotlin
    // -1 / +1 while a tab switch is animating (direction of travel), 0 for ordinary pushes.
    var tabDirection by remember { mutableIntStateOf(0) }
    fun push(key: NavKey) { tabDirection = 0; backStack.add(key) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
```

- [ ] **Step 3: Replace every `backStack.add(` in the file with `push(`** — in `scanIsbn`, the `pickPhotos` and `takePhoto` callbacks, the `BottomBar` callbacks (`onSearch`, `onManual`), and every `entry<...>` lambda. Do **not** touch `backStack.switchTo(...)` (that is the tab switch) or the extension function body `clear(); add(key)` inside `switchTo` (it is on `NavBackStack` itself, not `backStack.`).

- [ ] **Step 4: Tab switch sets the direction.** Replace `onSelect = { backStack.switchTo(it) },` with:

```kotlin
                    onSelect = { key ->
                        tabDirection = Integer.signum(topLevelKeys.indexOf(key) - topLevelKeys.indexOf(top))
                        backStack.switchTo(key)
                    },
```

- [ ] **Step 5: Transitions on `NavDisplay`.** Add these two parameters to the `NavDisplay(...)` call (after `entryDecorators`, before `entryProvider`):

```kotlin
            transitionSpec = {
                val d = tabDirection
                if (d == 0) {
                    // ordinary push: standard horizontal slide
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    // tab switch: a nudge toward the direction of travel plus a fade; timing from the Expressive motion scheme
                    (slideInHorizontally(spatial) { d * it / 3 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -d * it / 3 } + fadeOut(effects))
                }
            },
            popTransitionSpec = { slideInHorizontally { -it } togetherWith slideOutHorizontally { it } },
```
Leave `predictivePopTransitionSpec` unset (default).

- [ ] **Step 6: Verify** — `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleDebug` both succeed. If `defaultSpatialSpec`/`defaultEffectsSpec` do not resolve or have a different type signature, report the exact compiler error instead of guessing. Do NOT commit; main session commits `feat(nav): slide + fade between tabs`.

---

### Task 6: Verify on the emulator, update docs (main session only)

- [ ] **Step 1:** Install and launch. Check: Profile shows "Your name" + initials "?"; Edit profile → set name, bio, goal 5, pick a photo; Save; relaunch (force-stop + start) → values persist; Library shows "Hi, <name>"; goal ring shows readThisYear/5 (mark a book Read to see it move); remove photo → initials return; light, dark, AMOLED, dynamic on/off all look right.
- [ ] **Step 2: Tab motion.** Slow animations to see them: `adb shell settings put global animator_duration_scale 10`, then tap Library → Profile and screenshot mid-transition (slides from the right, fading); Profile → Library (from the left); a book detail push still slides as before; Back from a detail still pops. **Restore** with `adb shell settings put global animator_duration_scale 1`.
- [ ] **Step 3:** Fix defects (small ones inline, larger ones through a worker). Update `docs/HANDOFF.md` (sub-project 5 done; what was checked), keep the spec's one deviation noted (same DataStore file). Commit.

## Self-review against the spec

- §1 profile data → Task 1 (+ avatar Task 3); `LocalProfile` → Task 4 step 1–2.
- §2 stats → Task 2. §3 Profile screen, edit sheet, library greeting → Task 4.
- §4 tab motion (slide + fade, direction, motion-scheme timing, pushes keep a standard slide) → Task 5; the spike question (does `transitionSpec` fire for `clear(); add()`) is answered by the Navigation 3 docs and then confirmed visually in Task 6 step 2.
- §6 testing → unit tests in Tasks 1–3; manual checks in Task 6.
- Names used across tasks: `Profile`, `clampName`, `clampBio`, `clampGoal`, `parseGoal`, `initials`, `NAME_MAX`, `BIO_MAX`, `GOAL_MAX`, `ProfileRepository.profile/update`, `ProfileStats`, `computeStats`, `AvatarStore.save/remove`, `centerSquare`, `LocalProfile`, `ProfileViewModel.stats/save/setAvatar/removeAvatar`, `Avatar`, `EditProfileSheet`, `tabDirection`, `push` — consistent.
