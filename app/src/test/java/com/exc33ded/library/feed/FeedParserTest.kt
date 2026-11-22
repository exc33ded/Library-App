package com.exc33ded.library.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserTest {
    private val rss = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:dc="http://purl.org/dc/elements/1.1/">
 <channel><title>Site</title>
  <item>
   <title>First &amp; Best</title>
   <link>https://ex.com/a</link>
   <description><![CDATA[<p>Hello <b>world</b> &amp; friends</p>]]></description>
   <pubDate>Sun, 22 Nov 2026 10:00:00 +0000</pubDate>
  </item>
  <item>
   <title>Second</title>
   <link>https://ex.com/b</link>
   <content:encoded><![CDATA[<div>Long body</div>]]></content:encoded>
   <dc:date>2026-11-21T08:30:00Z</dc:date>
  </item>
  <item><title>No link here</title></item>
 </channel></rss>"""

    private val atom = """<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom"><title>A</title>
 <entry>
  <title>Atom one</title>
  <link rel="self" href="https://ex.com/self"/>
  <link rel="alternate" href="https://ex.com/atom1"/>
  <summary>Plain summary</summary>
  <updated>2026-11-20T12:00:00+01:00</updated>
 </entry>
</feed>"""

    @Test fun parsesRssItemsAndSkipsItemsWithoutLink() {
        val items = parseFeed(rss, "Site")
        assertEquals(2, items.size)
        assertEquals("First & Best", items[0].title)
        assertEquals("https://ex.com/a", items[0].link)
        assertEquals("Hello world & friends", items[0].summary)
        assertEquals("Site", items[0].source)
        assertEquals(1_795_341_600_000L, items[0].publishedAt)      // 2026-11-22T10:00:00Z
        assertEquals("Long body", items[1].summary)                  // content:encoded fallback
        assertEquals(1_795_249_800_000L, items[1].publishedAt)      // 2026-11-21T08:30:00Z via dc:date
    }

    @Test fun parsesAtomAndPrefersTheAlternateLink() {
        val items = parseFeed(atom, "A")
        assertEquals(1, items.size)
        assertEquals("https://ex.com/atom1", items[0].link)
        assertEquals("Plain summary", items[0].summary)
        assertEquals(1_795_172_400_000L, items[0].publishedAt)      // 2026-11-20T11:00:00Z
    }

    @Test fun badXmlGivesEmptyListNotAnException() {
        assertEquals(emptyList<FeedItem>(), parseFeed("<rss><channel><item>", "x"))
        assertEquals(emptyList<FeedItem>(), parseFeed("", "x"))
    }

    @Test fun doctypeFeedsAreRejectedSafely() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&x;</title><link>https://a</link></item></channel></rss>"""
        val items = parseFeed(xxe, "x")
        assertTrue(items.isEmpty() || items.none { it.title.contains("root:") })
    }

    @Test fun stripHtmlRemovesTagsDecodesEntitiesAndTruncates() {
        assertEquals("a & b < c", stripHtml("<p>a &amp; b &lt; c</p>"))
        assertEquals("it's", stripHtml("it&#39;s"))
        val long = stripHtml("x".repeat(500), max = 240)
        assertEquals(241, long.length)               // 240 chars + the ellipsis
        assertTrue(long.endsWith("…"))
    }

    @Test fun dateParsing() {
        assertEquals(0L, parseFeedDate(null))
        assertEquals(0L, parseFeedDate("garbage"))
        assertEquals(1_795_341_600_000L, parseFeedDate("Sun, 22 Nov 2026 10:00:00 GMT"))
        assertEquals(1_795_341_600_000L, parseFeedDate("2026-11-22T10:00:00Z"))
    }
}
