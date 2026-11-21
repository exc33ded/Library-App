package com.exc33ded.library.metadata

import kotlinx.serialization.Serializable

/** Source-agnostic lookup result; serializable so it can ride in a Navigation 3 key. */
@Serializable
data class BookCandidate(
    val title: String,
    val authors: String,
    val isbn: String? = null,
    val description: String? = null,
    val pageCount: Int? = null,
    val coverUrl: String? = null,
    val subjects: List<String> = emptyList(),
    val olWorkKey: String? = null,
)

/** Turn raw OL/Google subject strings into short, human category names. */
fun cleanSubjects(raw: List<String>): List<String> = raw.asSequence()
    .filterNot { ':' in it.substringAfter("form:").substringAfter("genre:") || '=' in it }
    .map { it.removePrefix("form:").removePrefix("genre:").substringBefore(" -- ").substringBefore(" / ").trim() }
    .filter { it.length in 3..30 }
    .map { s -> s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } } }
    .distinctBy { it.lowercase() }
    .toList()

/** Existing category names that match come first (with the existing spelling), then new ones; max 5. */
fun suggestCategories(subjects: List<String>, existing: List<String>): List<String> {
    val cleaned = cleanSubjects(subjects)
    val byLower = existing.associateBy { it.lowercase() }
    val matched = cleaned.mapNotNull { byLower[it.lowercase()] }
    val fresh = cleaned.filter { it.lowercase() !in byLower }
    return (matched + fresh).take(5)
}
