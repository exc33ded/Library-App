package com.exc33ded.library.ui.nav

import androidx.navigation3.runtime.NavKey
import com.exc33ded.library.metadata.BookCandidate
import kotlinx.serialization.Serializable

@Serializable data object LibraryKey : NavKey
@Serializable data object FeedKey : NavKey
@Serializable data object StatsKey : NavKey
@Serializable data object ProfileKey : NavKey
@Serializable data class BookDetailKey(val id: String) : NavKey
@Serializable data class BookEditKey(val id: String? = null, val prefill: BookCandidate? = null, val importId: String? = null) : NavKey
@Serializable data class FindBookKey(val isbn: String? = null, val query: String? = null, val importId: String? = null) : NavKey
@Serializable data object CategoriesKey : NavKey
@Serializable data object ImportKey : NavKey
@Serializable data object SettingsKey : NavKey

val topLevelKeys: List<NavKey> = listOf(LibraryKey, FeedKey, StatsKey, ProfileKey)
