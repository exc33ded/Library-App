package com.exc33ded.library.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * In-memory DataStore: the real file-backed one cannot write twice in a JVM test on Windows
 * (File.renameTo will not overwrite an existing file; Android is fine).
 */
private class FakeStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class ProfileRepositoryTest {
    private fun repo() = ProfileRepository(FakeStore())

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
