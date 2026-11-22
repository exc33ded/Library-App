package com.exc33ded.library.models

import java.util.Locale

/** Decimal units, matching how Hugging Face reports sizes. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000L -> String.format(Locale.US, "%.0f MB", bytes / 1e6)
    bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
    else -> "$bytes B"
}
