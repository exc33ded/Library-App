package com.exc33ded.library.feed

import kotlinx.serialization.Serializable

@Serializable
data class FeedSource(val url: String, val title: String, val enabled: Boolean = true)

@Serializable
data class FeedItem(val title: String, val link: String, val summary: String, val publishedAt: Long, val source: String)

object DefaultFeeds {
    val all: List<FeedSource> = listOf(
        FeedSource("https://bookriot.com/feed/", "Book Riot"),
        FeedSource("https://lithub.com/feed/", "Literary Hub"),
        FeedSource("https://reactormag.com/feed/", "Reactor"),
        FeedSource("https://rss.nytimes.com/services/xml/rss/nyt/Books.xml", "NYT Books"),
        FeedSource("https://www.theguardian.com/books/rss", "The Guardian Books"),
        FeedSource("https://www.kirkusreviews.com/feeds/rss/", "Kirkus Reviews"),
        FeedSource("https://electricliterature.com/feed/", "Electric Literature"),
        FeedSource("https://www.npr.org/rss/rss.php?id=1032", "NPR Books"),
    )
}

/** Union of all lists, one entry per link, newest first (undated items last), at most [cap]. */
fun mergeItems(lists: List<List<FeedItem>>, cap: Int = 200): List<FeedItem> =
    lists.flatten().distinctBy { it.link }.sortedByDescending { it.publishedAt }.take(cap)

/** Keeps the most recently added [cap] links (the list is in insertion order, newest last). */
fun capReadLinks(links: List<String>, cap: Int = 1000): List<String> = if (links.size <= cap) links else links.takeLast(cap)

fun isValidFeedUrl(raw: String): Boolean = raw.trim().let { it.startsWith("https://") || it.startsWith("http://") } &&
    runCatching { java.net.URI(raw.trim()).host != null }.getOrDefault(false)
