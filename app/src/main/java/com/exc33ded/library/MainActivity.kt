package com.exc33ded.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.SettingsRepository
import com.exc33ded.library.ui.nav.AppNav
import com.exc33ded.library.ui.theme.LibraryTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ponytail: first frame uses defaults until DataStore emits; add a splash hold if the flash is visible.
            val settings by settingsRepo.settings.collectAsStateWithLifecycle(AppSettings())
            LibraryTheme(settings) { AppNav() }
        }
    }
}
