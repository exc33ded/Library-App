package com.exc33ded.library.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ImportState { PENDING, PROCESSING, REVIEW, FAILED, DONE }

/** Local-only work queue: one row per imported photo. Not part of backup. */
@Entity(tableName = "import_queue", indices = [Index("state")])
data class ImportItemEntity(
    @PrimaryKey val id: String,
    /** content:// URI from the photo picker, or file path from the camera. */
    val source: String,
    val state: ImportState = ImportState.PENDING,
    /** Downscaled copy in filesDir/imports/{id}.jpg — thumbnail and fallback cover. */
    val photoPath: String? = null,
    /** Query built from OCR text (also offered for manual search on failure). */
    val query: String? = null,
    /** BookCandidate as JSON when state == REVIEW. */
    val resultJson: String? = null,
    val error: String? = null,
    val createdAt: Long = 0,
)
