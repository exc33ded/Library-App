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
