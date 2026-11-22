package com.exc33ded.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.profile.Profile
import com.exc33ded.library.profile.ProfileRepository
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.SettingsRepository
import com.exc33ded.library.ui.nav.AppNav
import com.exc33ded.library.ui.profile.LocalProfile
import com.exc33ded.library.ui.theme.LibraryTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var profileRepo: ProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ponytail: first frame uses defaults until DataStore emits; add a splash hold if the flash is visible.
            val settings by settingsRepo.settings.collectAsStateWithLifecycle(AppSettings())
            val profile by profileRepo.profile.collectAsStateWithLifecycle(Profile())
            LibraryTheme(settings) { CompositionLocalProvider(LocalProfile provides profile) { AppNav() } }
        }
    }
}
