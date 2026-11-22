package com.exc33ded.library.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSettingsTest {
    @Test fun columnsClampToRange() {
        assertEquals(2, clampColumns(0))
        assertEquals(2, clampColumns(2))
        assertEquals(4, clampColumns(4))
        assertEquals(6, clampColumns(99))
    }

    @Test fun darkResolution() {
        assertEquals(true, isDark(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(false, isDark(ThemeMode.SYSTEM, systemDark = false))
        assertEquals(false, isDark(ThemeMode.LIGHT, systemDark = true))
        assertEquals(true, isDark(ThemeMode.DARK, systemDark = false))
    }

    @Test fun progressPercent() {
        assertEquals(50, progressPercent(160, 320))
        assertEquals(100, progressPercent(500, 320)) // clamped
        assertNull(progressPercent(10, null))
        assertNull(progressPercent(10, 0))
    }

    @Test fun defaultsMatchSpec() {
        val d = AppSettings()
        assertEquals(ThemeMode.SYSTEM, d.themeMode)
        assertEquals(true, d.dynamicColor)
        assertEquals(false, d.amoled)
        assertEquals(LibraryDisplayMode.OVERLAY_GRID, d.displayMode)
        assertEquals(3, d.columns)
    }
}
