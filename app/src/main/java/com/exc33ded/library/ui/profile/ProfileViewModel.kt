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
