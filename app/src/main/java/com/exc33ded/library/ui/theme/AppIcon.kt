package com.exc33ded.library.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.ThemePalette

/** Launcher aliases declared in AndroidManifest.xml, one per theme icon. [DEFAULT_ICON] is enabled in the manifest. */
const val DEFAULT_ICON = "IconInk"
val ALL_ICON_ALIASES = listOf("IconInk", "IconTeal", "IconForest", "IconOcean", "IconBerry", "IconGraphite")

/** Which alias should be the visible launcher icon: the theme's own, or Ink & amber (also used for Wallpaper colours). */
fun iconAliasFor(settings: AppSettings): String = when {
    !settings.iconFollowsTheme || settings.dynamicColor -> DEFAULT_ICON
    else -> when (settings.palette) {
        ThemePalette.INK_AMBER -> "IconInk"
        ThemePalette.DEEP_TEAL -> "IconTeal"
        ThemePalette.FOREST -> "IconForest"
        ThemePalette.OCEAN -> "IconOcean"
        ThemePalette.BERRY -> "IconBerry"
        ThemePalette.GRAPHITE -> "IconGraphite"
    }
}

/**
 * Makes [iconAliasFor] the enabled launcher alias and disables the others. The target is enabled FIRST so the app can never be
 * left without a launcher entry. Does nothing when the right alias is already the only one enabled (avoids needless launcher refreshes).
 */
fun applyAppIcon(context: Context, settings: AppSettings) {
    val pm = context.packageManager
    val target = iconAliasFor(settings)
    fun component(alias: String) = ComponentName(context.packageName, "${context.packageName}.$alias")
    fun isEnabled(alias: String): Boolean = when (pm.getComponentEnabledSetting(component(alias))) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
        else -> alias == DEFAULT_ICON // DEFAULT = whatever the manifest says
    }
    if (isEnabled(target) && ALL_ICON_ALIASES.all { it == target || !isEnabled(it) }) return
    pm.setComponentEnabledSetting(component(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    ALL_ICON_ALIASES.filter { it != target }.forEach {
        pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
