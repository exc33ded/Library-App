package com.exc33ded.library.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class BookCandidateTest {
    @Test fun subjectsAreCleanedDedupedAndCapped() {
        val raw = listOf(
            "form:novel", "genre:satire", "Satire", "cultural appropriation",
            "nyt:combined-print-and-e-book-fiction=2023-06-04", "Fiction -- General",
            "A very long subject heading that nobody wants as a category", "Fiction",
        )
        assertEquals(listOf("Novel", "Satire", "Cultural Appropriation", "Fiction"), cleanSubjects(raw))
    }

    @Test fun suggestionsPutExistingCategoriesFirstAndCapAtFive() {
        val subjects = listOf("Mystery", "Cats", "Fiction", "Bookstores", "Cozy", "Women Sleuths", "Extra")
        val existing = listOf("Fiction", "Thriller")
        assertEquals(listOf("Fiction", "Mystery", "Cats", "Bookstores", "Cozy"), suggestCategories(subjects, existing))
    }
}
