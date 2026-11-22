package com.exc33ded.library.backup

/** Rows to write: new ids, or ids whose incoming updatedAt is strictly newer than the stored one. */
fun <T> mergeNewer(incoming: List<T>, existingUpdatedAt: Map<String, Long>, idOf: (T) -> String, updatedOf: (T) -> Long): List<T> =
    incoming.filter { row -> existingUpdatedAt[idOf(row)].let { it == null || updatedOf(row) > it } }

data class MergeSummary(val added: Int, val updated: Int, val unchanged: Int) {
    val total: Int get() = added + updated + unchanged
}
