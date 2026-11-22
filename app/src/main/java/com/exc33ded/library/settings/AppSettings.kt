package com.exc33ded.library.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class LibraryDisplayMode { OVERLAY_GRID, CAPTION_GRID, LIST }

enum class ThemePalette { INK_AMBER, DEEP_TEAL, FOREST, OCEAN, BERRY, GRAPHITE }

enum class ModelsLocation { PHONE, APP_FOLDER, SD_CARD }

const val MIN_COLUMNS = 2
const val MAX_COLUMNS = 6

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.OVERLAY_GRID,
    val columns: Int = 3,
    val showProgressBadge: Boolean = true,
    val showStatusBadge: Boolean = true,
    val modelsLocation: ModelsLocation = ModelsLocation.PHONE,
    val wifiOnlyDownloads: Boolean = true,
    val lastExportAt: Long = 0L,
    val palette: ThemePalette = ThemePalette.INK_AMBER,
    val iconFollowsTheme: Boolean = true,
)

fun clampColumns(n: Int): Int = n.coerceIn(MIN_COLUMNS, MAX_COLUMNS)

fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** 0..100, or null when the page count is unknown. */
fun progressPercent(currentPage: Int, pageCount: Int?): Int? =
    pageCount?.takeIf { it > 0 }?.let { (currentPage * 100 / it).coerceIn(0, 100) }
