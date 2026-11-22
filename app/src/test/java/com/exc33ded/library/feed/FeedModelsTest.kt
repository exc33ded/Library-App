package com.exc33ded.library.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedModelsTest {
    private fun item(link: String, at: Long) = FeedItem("t-$link", link, "s", at, "src")

    @Test fun defaultFeedsAreHttpsAndUnique() {
        assertEquals(8, DefaultFeeds.all.size)
        assertEquals(DefaultFeeds.all.size, DefaultFeeds.all.map { it.url }.toSet().size)
        assertTrue(DefaultFeeds.all.all { it.url.startsWith("https://") && it.enabled })
    }

    @Test fun mergeDedupesByLinkSortsNewestFirstAndCaps() {
        val a = listOf(item("x", 1), item("y", 5))
        val b = listOf(item("y", 5), item("z", 9), item("old", 0))
        val merged = mergeItems(listOf(a, b), cap = 3)
        assertEquals(listOf("z", "y", "x"), merged.map { it.link })
    }

    @Test fun readSetIsCappedKeepingTheNewestEntries() {
        val set = (1..1200).map { "l$it" }
        val capped = capReadLinks(set, cap = 1000)
        assertEquals(1000, capped.size)
        assertEquals("l1200", capped.last())
        assertEquals("l201", capped.first())
    }

    @Test fun validFeedUrls() {
        assertTrue(isValidFeedUrl("https://example.com/feed"))
        assertTrue(isValidFeedUrl(" http://example.com/rss.xml "))
        assertTrue(!isValidFeedUrl("ftp://example.com/feed"))
        assertTrue(!isValidFeedUrl("not a url"))
        assertTrue(!isValidFeedUrl(""))
    }
}
