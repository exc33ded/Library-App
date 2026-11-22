package com.exc33ded.library.backup

import com.exc33ded.library.data.db.BookCategoryEntity
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.data.db.SummarySource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BACKUP_VERSION = 1

val backupJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

@Serializable
data class BackupFile(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long,
    val books: List<BookDto>,
    val categories: List<CategoryDto>,
    val bookCategories: List<LinkDto>,
    val profile: ProfileDto? = null,
)

@Serializable
data class BookDto(
    val id: String,
    val title: String,
    val authors: String = "",
    val isbn: String? = null,
    val description: String? = null,
    val aiSummary: String? = null,
    val aiSummarySource: String? = null,
    /** Zip entry name of the cover image, e.g. "covers/<id>.jpg"; null when the book has no local cover. */
    val coverFile: String? = null,
    val coverRemotePath: String? = null,
    val pageCount: Int? = null,
    val status: String,
    val currentPage: Int = 0,
    val rating: Int? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val olWorkKey: String? = null,
    val notes: String? = null,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class CategoryDto(val id: String, val name: String, val color: Long? = null, val updatedAt: Long, val deleted: Boolean = false)

@Serializable
data class LinkDto(val id: String, val bookId: String, val categoryId: String, val updatedAt: Long, val deleted: Boolean = false)

@Serializable
data class ProfileDto(val name: String = "", val bio: String = "", val yearlyGoal: Int? = null)

fun BookEntity.toDto(coverFile: String?) = BookDto(
    id, title, authors, isbn, description, aiSummary, aiSummarySource?.name, coverFile, coverRemotePath, pageCount,
    status.name, currentPage, rating, startedAt, finishedAt, olWorkKey, notes, updatedAt, deleted,
)

/** Imported rows are marked dirty (kept sync-ready, like every other write). */
fun BookDto.toEntity(coverPath: String?) = BookEntity(
    id = id, title = title, authors = authors, isbn = isbn, description = description, aiSummary = aiSummary,
    aiSummarySource = aiSummarySource?.let { runCatching { SummarySource.valueOf(it) }.getOrNull() },
    coverPath = coverPath, coverRemotePath = coverRemotePath, pageCount = pageCount,
    status = runCatching { ReadingStatus.valueOf(status) }.getOrDefault(ReadingStatus.WANT),
    currentPage = currentPage, rating = rating, startedAt = startedAt, finishedAt = finishedAt, olWorkKey = olWorkKey,
    notes = notes, updatedAt = updatedAt, deleted = deleted, dirty = true,
)

fun CategoryEntity.toDto() = CategoryDto(id, name, color, updatedAt, deleted)
fun CategoryDto.toEntity() = CategoryEntity(id, name, color, updatedAt, deleted, dirty = true)
fun BookCategoryEntity.toDto() = LinkDto(id, bookId, categoryId, updatedAt, deleted)
fun LinkDto.toEntity() = BookCategoryEntity(id, bookId, categoryId, updatedAt, deleted, dirty = true)
