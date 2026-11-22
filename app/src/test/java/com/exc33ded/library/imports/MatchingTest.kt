package com.exc33ded.library.imports

import com.exc33ded.library.metadata.BookCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchingTest {
    private val yellowLines = listOf(OcrLine("YELLOWFACE", 120), OcrLine("REBECCA F. KUANG", 40))

    @Test fun editDistanceBasics() {
        assertEquals(0, editDistance("plague", "plague"))
        assertEquals(1, editDistance("plague", "plaque"))
        assertEquals(2, editDistance("velloweace", "yellowface"))
    }

    @Test fun fuzzyHasToleratesOcrMisreadsOnlyOnLongWords() {
        val ocr = setOf("velloweace", "kuang", "cat")
        assertTrue(fuzzyHas("yellowface", ocr))   // 2 edits, word >= 8 letters
        assertTrue(fuzzyHas("kuang", ocr))        // exact
        assertFalse(fuzzyHas("cut", ocr))         // short words must match exactly
    }

    @Test fun fuzzyHasOneEditForMidLengthWords() {
        assertTrue(fuzzyHas("plague", setOf("plaque")))   // 6 letters -> 1 edit allowed
        assertFalse(fuzzyHas("plague", setOf("plaquez9"))) // too far
    }

    @Test fun exactTitleAndAuthorScoresOne() =
        assertEquals(1.0, scoreCandidate(yellowLines, BookCandidate("Yellowface", "R. F. Kuang")), 1e-9)

    @Test fun titleOnlyScoresPointSeven() =
        assertEquals(0.7, scoreCandidate(listOf(OcrLine("YELLOWFACE", 120)), BookCandidate("Yellowface", "R. F. Kuang")), 1e-9)

    @Test fun stopWordsDoNotCount() =
        assertEquals(1.0, scoreCandidate(listOf(OcrLine("PLAGUE", 100), OcrLine("CAMUS", 40)), BookCandidate("The Plague", "Albert Camus")), 1e-9)

    @Test fun misreadCoverStillScoresAboveThreshold() {
        val lines = listOf(OcrLine("VELLOWEACE", 120), OcrLine("REBECCA F. KUANG", 40))
        assertTrue(scoreCandidate(lines, BookCandidate("Yellowface", "R. F. Kuang")) >= MATCH_THRESHOLD)
    }

    @Test fun wrongBookScoresBelowThreshold() =
        assertTrue(scoreCandidate(yellowLines, BookCandidate("La Peste", "Albert Camus")) < MATCH_THRESHOLD)

    @Test fun queriesAreFullThenTopTwoThenSinglesWithoutDuplicates() = assertEquals(
        listOf(
            "yellowface rebecca f kuang some publisher",
            "yellowface rebecca f kuang",
            "yellowface",
            "rebecca f kuang",
            "some publisher",
        ),
        buildQueries(yellowLines + OcrLine("Some Publisher", 10)),
    )

    @Test fun noReadableTextGivesNoQueries() =
        assertEquals(emptyList<String>(), buildQueries(listOf(OcrLine("#1 NEW YORK TIMES BESTSELLING AUTHOR", 30), OcrLine("@ 7", 50))))
}
