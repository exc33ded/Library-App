package com.exc33ded.library.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReadingStatus { WANT, READING, READ, DNF }

enum class SummarySource { DESCRIPTION, COVER, USER }

/** Every synced table carries updatedAt / deleted / dirty (spec §5). */
@Entity(tableName = "books", indices = [Index("status"), Index("isbn")])
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: String = "",
    val isbn: String? = null,
    val description: String? = null,
    val aiSummary: String? = null,
    val aiSummarySource: SummarySource? = null,
    val coverPath: String? = null,
    val coverRemotePath: String? = null,
    val pageCount: Int? = null,
    val status: ReadingStatus = ReadingStatus.WANT,
    val currentPage: Int = 0,
    val rating: Int? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val olWorkKey: String? = null,
    val notes: String? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long? = null,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** id = Ids.pair(bookId, categoryId). */
@Entity(tableName = "book_categories", indices = [Index("bookId"), Index("categoryId")])
data class BookCategoryEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val categoryId: String,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)
