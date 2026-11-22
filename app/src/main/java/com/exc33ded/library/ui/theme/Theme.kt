package com.exc33ded.library.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.isDark

/**
 * Spec §10: every animation pulls its spec from MaterialTheme.motionScheme.
 * minSdk 31 => dynamic color always available.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isDark(settings.themeMode, isSystemInDarkTheme())
    var colors = when {
        settings.dynamicColor -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> InkDark
        else -> InkLight
    }
    if (dark && settings.amoled) {
        colors = colors.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
    }
    // Status/nav bar icon contrast must follow the *chosen* theme, not the system one.
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive()) {
        CompositionLocalProvider(LocalAppSettings provides settings, content = content)
    }
}

/**
 * Settings collected once in MainActivity for the app's lifetime. Screens read this instead of
 * collecting their own copy: top-level tab switches clear the back stack (and the ViewModel), so a
 * per-screen collector would flash the defaults on every return to Library.
 */
val LocalAppSettings = compositionLocalOf { AppSettings() }
