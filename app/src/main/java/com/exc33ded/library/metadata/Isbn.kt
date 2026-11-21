package com.exc33ded.library.metadata

object Isbn {
    /** Returns a valid 978/979 ISBN-13 or null. Accepts ISBN-10 (converted), hyphens and spaces. */
    fun normalize(raw: String): String? {
        val s = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
        return when {
            s.length == 13 && s.all(Char::isDigit) && checksum13(s.take(12)) == s[12] - '0' &&
                (s.startsWith("978") || s.startsWith("979")) -> s
            s.length == 10 && isValid10(s) -> "978" + s.take(9) + checksum13("978" + s.take(9))
            else -> null
        }
    }

    private fun checksum13(first12: String): Int =
        (10 - first12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum() % 10) % 10

    private fun isValid10(s: String): Boolean {
        if (!s.take(9).all(Char::isDigit)) return false
        val last = s[9]
        if (!last.isDigit() && last != 'X') return false
        val sum = (0 until 9).sumOf { (s[it] - '0') * (10 - it) } + if (last == 'X') 10 else last - '0'
        return sum % 11 == 0
    }
}
