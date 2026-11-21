package com.exc33ded.library.imports

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryBuilderTest {
    @Test fun yellowface() = assertEquals(
        "yellowface rebecca f kuang",
        buildQuery(listOf(
            OcrLine("YELLOWFACE", 120),
            OcrLine("REBECCA F. KUANG", 40),
            OcrLine("Available Exclusively in the Indian Subcontinent", 20),
        )),
    )

    @Test fun murderAtTheBookstoreDropsTaglinesAndOrdersByHeight() = assertEquals(
        "murder bookstore sue minix at the",
        buildQuery(listOf(
            OcrLine("She can write the perfect murder mystery...", 20),
            OcrLine("But can she solve one in real life?", 20),
            OcrLine("Murder", 100),
            OcrLine("at the", 40),
            OcrLine("Bookstore", 100),
            OcrLine("SUE MINIX", 45),
        )),
    )

    @Test fun ruskinBondKeepsFourLines() = assertEquals(
        "ruskin bond great stories for children",
        buildQuery(listOf(
            OcrLine("RUSKIN", 150), OcrLine("BOND", 150),
            OcrLine("GREAT STORIES", 40), OcrLine("FOR CHILDREN", 40),
        )),
    )

    @Test fun noiseAndJunkOnly() = assertEquals(
        "",
        buildQuery(listOf(OcrLine("#1 NEW YORK TIMES BESTSELLING AUTHOR", 30), OcrLine("@ 7", 50))),
    )

    @Test fun capsAtTenWords() = assertEquals(
        10,
        buildQuery(listOf(OcrLine("one two three four five six", 50), OcrLine("seven eight nine ten eleven twelve", 40)))
            .split(' ').size,
    )

    @Test fun lettersCountedForRotationChoice() = assertEquals(8, letterCount(listOf(OcrLine("Dune 2!", 10), OcrLine("Herb", 5))))
}
