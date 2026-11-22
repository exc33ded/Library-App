package com.exc33ded.library.ui.library

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.progressPercent

fun badgeText(b: BookEntity, s: AppSettings): String? {
    if (s.showProgressBadge && b.status == ReadingStatus.READING) {
        progressPercent(b.currentPage, b.pageCount)?.let { return "$it%" }
    }
    if (!s.showStatusBadge) return null
    return when (b.status) {
        ReadingStatus.READ -> "Read"
        ReadingStatus.DNF -> "DNF"
        ReadingStatus.READING -> "Reading"
        ReadingStatus.WANT -> null
    }
}
