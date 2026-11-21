package com.exc33ded.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdsTest {
    @Test fun pairIdIsDeterministic() =
        assertEquals(Ids.pair("book1", "cat1"), Ids.pair("book1", "cat1"))

    @Test fun pairIdDependsOnOrderAndValues() {
        assertNotEquals(Ids.pair("book1", "cat1"), Ids.pair("cat1", "book1"))
        assertNotEquals(Ids.pair("book1", "cat1"), Ids.pair("book1", "cat2"))
    }

    @Test fun newIdsAreUnique() = assertNotEquals(Ids.new(), Ids.new())
}
