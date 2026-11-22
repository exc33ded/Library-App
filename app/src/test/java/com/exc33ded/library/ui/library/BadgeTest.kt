package com.exc33ded.library.ui.library

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgeTest {
    private val reading = BookEntity(id = "b", title = "T", status = ReadingStatus.READING, currentPage = 160, pageCount = 320)

    @Test fun readingShowsPercent() = assertEquals("50%", badgeText(reading, AppSettings()))

    @Test fun readingWithoutPageCountFallsBackToStatus() =
        assertEquals("Reading", badgeText(reading.copy(pageCount = null), AppSettings()))

    @Test fun progressOffFallsBackToStatus() =
        assertEquals("Reading", badgeText(reading, AppSettings(showProgressBadge = false)))

    @Test fun bothOffIsNull() =
        assertNull(badgeText(reading, AppSettings(showProgressBadge = false, showStatusBadge = false)))

    @Test fun wantHasNoBadge() = assertNull(badgeText(reading.copy(status = ReadingStatus.WANT), AppSettings()))

    @Test fun readAndDnfLabels() {
        assertEquals("Read", badgeText(reading.copy(status = ReadingStatus.READ), AppSettings()))
        assertEquals("DNF", badgeText(reading.copy(status = ReadingStatus.DNF), AppSettings()))
    }
}
