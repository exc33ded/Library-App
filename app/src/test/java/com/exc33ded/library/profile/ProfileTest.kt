package com.exc33ded.library.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTest {
    @Test fun nameIsTrimmedAndCapped() {
        assertEquals("", clampName("   "))
        assertEquals("Sam", clampName("  Sam "))
        assertEquals(NAME_MAX, clampName("x".repeat(100)).length)
    }

    @Test fun bioIsTrimmedAndCapped() {
        assertEquals("hi", clampBio(" hi "))
        assertEquals(BIO_MAX, clampBio("y".repeat(500)).length)
    }

    @Test fun goalMustBePositiveAndIsCapped() {
        assertNull(clampGoal(null))
        assertNull(clampGoal(0))
        assertNull(clampGoal(-3))
        assertEquals(24, clampGoal(24))
        assertEquals(GOAL_MAX, clampGoal(5000))
    }

    @Test fun goalTextParsing() {
        assertEquals(24, parseGoal("24"))
        assertEquals(24, parseGoal(" 24 "))
        assertNull(parseGoal(""))
        assertNull(parseGoal("abc"))
        assertNull(parseGoal("0"))
    }

    @Test fun initials() {
        assertEquals("SK", initials("Sarim Khan"))
        assertEquals("S", initials("sarim"))
        assertEquals("AB", initials("a b c"))
        assertEquals("?", initials("   "))
    }
}
