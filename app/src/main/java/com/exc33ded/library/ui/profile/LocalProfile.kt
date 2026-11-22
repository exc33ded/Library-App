package com.exc33ded.library.ui.profile

import androidx.compose.runtime.compositionLocalOf
import com.exc33ded.library.profile.Profile

/** Collected once in MainActivity: tab switches clear ViewModels, so screens must not collect their own copy. */
val LocalProfile = compositionLocalOf { Profile() }
