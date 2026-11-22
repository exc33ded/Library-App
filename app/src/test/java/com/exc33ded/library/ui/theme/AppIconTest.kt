package com.exc33ded.library.ui.theme

import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconTest {
    private fun fixed(p: ThemePalette) = AppSettings(dynamicColor = false, palette = p)

    @Test fun eachPaletteHasItsOwnAlias() {
        val aliases = ThemePalette.entries.map { iconAliasFor(fixed(it)) }
        assertEquals(ThemePalette.entries.size, aliases.toSet().size)
        assertTrue(aliases.all { it in ALL_ICON_ALIASES })
    }

    @Test fun wallpaperColoursUseTheDefaultIcon() =
        assertEquals(DEFAULT_ICON, iconAliasFor(AppSettings(dynamicColor = true, palette = ThemePalette.BERRY)))

    @Test fun switchOffAlwaysGivesTheDefaultIcon() {
        for (p in ThemePalette.entries)
            assertEquals(DEFAULT_ICON, iconAliasFor(AppSettings(dynamicColor = false, palette = p, iconFollowsTheme = false)))
    }

    @Test fun inkAmberIsTheDefaultAlias() = assertEquals(DEFAULT_ICON, iconAliasFor(fixed(ThemePalette.INK_AMBER)))

    @Test fun defaultAliasIsInTheList() = assertTrue(DEFAULT_ICON in ALL_ICON_ALIASES)
}
