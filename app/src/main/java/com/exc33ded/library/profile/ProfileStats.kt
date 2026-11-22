package com.exc33ded.library.profile

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import java.time.Instant
import java.time.ZoneId

data class ProfileStats(
    val read: Int,
    val reading: Int,
    val want: Int,
    val dnf: Int,
    val pagesRead: Int,
    val averageRating: Double?,
    val readThisYear: Int,
)

fun computeStats(books: List<BookEntity>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ProfileStats {
    fun yearOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).year
    val thisYear = yearOf(nowMillis)
    val ratings = books.mapNotNull { it.rating }
    return ProfileStats(
        read = books.count { it.status == ReadingStatus.READ },
        reading = books.count { it.status == ReadingStatus.READING },
        want = books.count { it.status == ReadingStatus.WANT },
        dnf = books.count { it.status == ReadingStatus.DNF },
        pagesRead = books.sumOf {
            when (it.status) {
                ReadingStatus.READ -> it.pageCount ?: 0
                ReadingStatus.READING -> it.currentPage
                else -> 0
            }
        },
        averageRating = ratings.takeIf { it.isNotEmpty() }?.average(),
        readThisYear = books.count { it.status == ReadingStatus.READ && it.finishedAt?.let(::yearOf) == thisYear },
    )
}
