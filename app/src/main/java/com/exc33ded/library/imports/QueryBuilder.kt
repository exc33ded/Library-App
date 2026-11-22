package com.exc33ded.library.imports

/** One recognised text line and its on-image height in px (bigger = more likely title/author). */
@kotlinx.serialization.Serializable
data class OcrLine(val text: String, val height: Int)

private val NOISE = Regex(
    "bestsell|best-sell|a novel|available exclusively|subcontinent|author of|winner|edition|" +
        "copyright|www\\.|\\.com|praise|times|international|now a major|shortlisted|longlisted|award",
    RegexOption.IGNORE_CASE,
)

// ponytail: height-ranked heuristic; Phase 3's vision model can replace it where it fails.
fun buildQuery(lines: List<OcrLine>): String = lines
    .asSequence()
    .filter { l -> l.text.count { it.isLetter() } >= 2 }
    .filterNot { NOISE.containsMatchIn(it.text) }
    .filter { it.text.trim().split(Regex("\\s+")).size <= 6 }
    .sortedByDescending { it.height }
    .take(4)
    .map { l -> l.text.lowercase().replace(Regex("[^\\p{L}\\p{N}' ]"), " ") }
    .flatMap { it.split(Regex("\\s+")).asSequence() }
    .filter { it.isNotBlank() }
    .take(10)
    .joinToString(" ")

fun letterCount(lines: List<OcrLine>): Int = lines.sumOf { l -> l.text.count { it.isLetter() } }
