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

    // The cover says "Rebecca" but the candidate has initials only, so 2 of the 3 big words are explained.
    @Test fun exactTitleAndSurnameScoresPointNine() =
        assertEquals(0.9, scoreCandidate(yellowLines, BookCandidate("Yellowface", "R. F. Kuang")), 1e-9)

    @Test fun fullCoverExplainedScoresOne() =
        assertEquals(1.0, scoreCandidate(yellowLines, BookCandidate("Yellowface", "Rebecca F. Kuang")), 1e-9)

    @Test fun shortTitleDoesNotBeatTheFullOne() {
        val lines = listOf(OcrLine("100 SELECTED", 100), OcrLine("POEMS", 100))
        assertTrue(scoreCandidate(lines, BookCandidate("100 Selected Poems", "")) > scoreCandidate(lines, BookCandidate("Poems", "")))
    }

    @Test fun titleOnlyScoresPointEight() =
        assertEquals(0.8, scoreCandidate(listOf(OcrLine("YELLOWFACE", 120)), BookCandidate("Yellowface", "R. F. Kuang")), 1e-9)

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
