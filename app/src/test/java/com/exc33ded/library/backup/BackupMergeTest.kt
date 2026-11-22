package com.exc33ded.library.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMergeTest {
    private data class Row(val id: String, val updatedAt: Long)

    @Test fun newRowsAreWritten() =
        assertEquals(listOf(Row("a", 1)), mergeNewer(listOf(Row("a", 1)), emptyMap(), { it.id }, { it.updatedAt }))

    @Test fun newerWinsOlderAndEqualAreSkipped() {
        val existing = mapOf("a" to 5L, "b" to 5L, "c" to 5L)
        val incoming = listOf(Row("a", 6), Row("b", 5), Row("c", 4), Row("d", 1))
        assertEquals(listOf(Row("a", 6), Row("d", 1)), mergeNewer(incoming, existing, { it.id }, { it.updatedAt }))
    }

    @Test fun importingTheSameRowsTwiceWritesNothingTheSecondTime() {
        val rows = listOf(Row("a", 3), Row("b", 4))
        val afterFirst = mergeNewer(rows, emptyMap(), { it.id }, { it.updatedAt }).associate { it.id to it.updatedAt }
        assertEquals(emptyList<Row>(), mergeNewer(rows, afterFirst, { it.id }, { it.updatedAt }))
    }

    @Test fun summaryCounts() {
        val s = MergeSummary(added = 2, updated = 1, unchanged = 3)
        assertEquals(6, s.total)
    }
}
