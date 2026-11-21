package com.exc33ded.library.imports

import com.exc33ded.library.data.db.ImportItemEntity
import com.exc33ded.library.data.db.ImportState
import com.exc33ded.library.metadata.MetadataLookup
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Turns one queued photo into a REVIEW (with candidate) or FAILED item. Never throws except on cancellation. */
class ImportProcessor @Inject constructor(
    private val prep: PhotoPrep,
    private val reader: TextReader,
    private val lookup: MetadataLookup,
) {
    suspend fun process(item: ImportItemEntity): ImportItemEntity {
        val photo = try {
            item.photoPath ?: prep.prepare(item.source, item.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return item.copy(state = ImportState.FAILED, error = "Couldn't open the photo")
        }
        val withPhoto = item.copy(photoPath = photo)
        val lines = try {
            reader.read(photo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return withPhoto.copy(state = ImportState.FAILED, error = "Couldn't read the cover text")
        }
        val query = buildQuery(lines)
        if (query.isBlank()) return withPhoto.copy(state = ImportState.FAILED, error = "No readable text on the cover")

        val shortQuery = buildQuery(lines.sortedByDescending { it.height }.take(2))
        return try {
            val hit = lookup.search(query).firstOrNull()
                ?: shortQuery.takeIf { it != query }?.let { lookup.search(it).firstOrNull() }
            if (hit == null) {
                withPhoto.copy(state = ImportState.FAILED, query = query, error = "No match for \"$query\"")
            } else {
                withPhoto.copy(state = ImportState.REVIEW, query = query, resultJson = Json.encodeToString(lookup.details(hit)), error = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withPhoto.copy(state = ImportState.FAILED, query = query, error = "Couldn't reach the book services")
        }
    }
}
