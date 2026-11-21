package com.exc33ded.library.data

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus

fun BookEntity.withStatus(newStatus: ReadingStatus, now: Long): BookEntity = when (newStatus) {
    ReadingStatus.WANT -> copy(status = newStatus, finishedAt = null)
    ReadingStatus.READING -> copy(status = newStatus, startedAt = startedAt ?: now, finishedAt = null)
    ReadingStatus.READ -> copy(
        status = newStatus,
        startedAt = startedAt ?: now,
        finishedAt = now,
        currentPage = pageCount ?: currentPage,
    )
    ReadingStatus.DNF -> copy(status = newStatus, finishedAt = now)
}

fun BookEntity.withProgress(page: Int, now: Long = System.currentTimeMillis()): BookEntity {
    val clamped = page.coerceAtLeast(0).let { p -> pageCount?.let { p.coerceAtMost(it) } ?: p }
    val updated = copy(currentPage = clamped)
    return if (pageCount != null && clamped == pageCount && status != ReadingStatus.READ)
        updated.withStatus(ReadingStatus.READ, now) else updated
}
