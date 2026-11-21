package com.exc33ded.library.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM book_categories WHERE deleted = 0")
    fun observeLinks(): Flow<List<BookCategoryEntity>>

    @Query("SELECT * FROM book_categories WHERE bookId = :bookId")
    suspend fun linksForBook(bookId: String): List<BookCategoryEntity>

    @Query("SELECT * FROM book_categories WHERE categoryId = :categoryId")
    suspend fun linksForCategory(categoryId: String): List<BookCategoryEntity>

    @Query("SELECT * FROM book_categories")
    suspend fun allLinksIncludingDeleted(): List<BookCategoryEntity>

    @Upsert
    suspend fun upsertLink(link: BookCategoryEntity)
}
