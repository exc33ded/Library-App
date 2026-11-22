package com.exc33ded.library.feed

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

private fun NodeList.elements(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }

private fun Element.child(name: String): Element? = childNodes.elements().firstOrNull { it.tagName == name }

private fun Element.childText(vararg names: String): String? =
    names.firstNotNullOfOrNull { n -> child(n)?.textContent?.trim()?.takeIf { it.isNotEmpty() } }

/** Never throws: malformed XML, documents with a DOCTYPE (XXE) and anything else unreadable give an empty list. */
fun parseFeed(xml: String, sourceTitle: String): List<FeedItem> {
    val root = try {
        val factory = DocumentBuilderFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.isExpandEntityReferences = false }
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t')))).documentElement
    } catch (e: Exception) {
        return emptyList()
    }
    val entries = root.getElementsByTagName("item").elements().ifEmpty { root.getElementsByTagName("entry").elements() }
    return entries.mapNotNull { e ->
        val link = itemLink(e) ?: return@mapNotNull null
        val title = stripHtml(e.childText("title") ?: return@mapNotNull null, max = 300)
        FeedItem(
            title = title,
            link = link,
            summary = stripHtml(e.childText("description", "summary", "content:encoded", "content").orEmpty()),
            publishedAt = parseFeedDate(e.childText("pubDate", "published", "updated", "dc:date")),
            source = sourceTitle,
        )
    }
}

private fun itemLink(e: Element): String? {
    val links = e.childNodes.elements().filter { it.tagName == "link" }
    val atom = links.filter { it.hasAttribute("href") }
    val chosen = atom.firstOrNull { it.getAttribute("rel").let { r -> r.isEmpty() || r == "alternate" } }?.getAttribute("href")
        ?: links.firstNotNullOfOrNull { it.textContent?.trim()?.takeIf { t -> t.isNotEmpty() } }
    return chosen?.takeIf { it.startsWith("http") }
}

fun stripHtml(s: String, max: Int = 240): String {
    val noTags = s.replace(Regex("(?s)<[^>]*>"), " ")
    val decoded = noTags
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.takeIf { it in 32..0xFFFF }?.toChar()?.toString() ?: " " }
    val clean = decoded.replace(Regex("\\s+"), " ").trim()
    return if (clean.length > max) clean.take(max).trimEnd() + "…" else clean
}

/** RFC 1123 (RSS pubDate) or ISO 8601 (Atom / dc:date) to epoch millis; 0 when missing or unreadable. */
fun parseFeedDate(s: String?): Long {
    val t = s?.trim().orEmpty()
    if (t.isEmpty()) return 0L
    return runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
        ?: 0L
}
