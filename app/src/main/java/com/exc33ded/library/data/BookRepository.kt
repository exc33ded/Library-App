package com.exc33ded.library.data

import androidx.room.withTransaction
import com.exc33ded.library.data.db.AppDatabase
import com.exc33ded.library.data.db.BookCategoryEntity
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import com.exc33ded.library.data.db.ReadingStatus
import javax.inject.Inject
import javax.inject.Singleton

/** All writes go through here: stamps updatedAt, sets dirty, soft-deletes (spec §5/§6). */
@Singleton
class BookRepository(
    private val db: AppDatabase,
    private val clock: () -> Long,
) {
    @Inject constructor(db: AppDatabase) : this(db, System::currentTimeMillis)

    private val books = db.bookDao()
    private val cats = db.categoryDao()

    fun observeBooks() = books.observeAll()
    fun observeBook(id: String) = books.observe(id)
    fun observeCategories() = cats.observeAll()
    fun observeLinks() = cats.observeLinks()

    suspend fun getBook(id: String): BookEntity? = books.get(id)?.takeUnless { it.deleted }

    suspend fun categoryIdsFor(bookId: String): Set<String> =
        cats.linksForBook(bookId).filterNot { it.deleted }.map { it.categoryId }.toSet()

    suspend fun saveBook(book: BookEntity, categoryIds: Set<String>): String = db.withTransaction {
        val now = clock()
        books.upsert(book.copy(updatedAt = now, dirty = true, deleted = false))
        val existing = cats.linksForBook(book.id)
        existing.filter { !it.deleted && it.categoryId !in categoryIds }
            .forEach { cats.upsertLink(it.copy(deleted = true, dirty = true, updatedAt = now)) }
        categoryIds.forEach { catId ->
            val link = existing.firstOrNull { it.categoryId == catId }
            if (link == null || link.deleted) cats.upsertLink(
                BookCategoryEntity(Ids.pair(book.id, catId), book.id, catId, updatedAt = now),
            )
        }
        book.id
    }

    suspend fun setStatus(id: String, status: ReadingStatus) = update(id) { it.withStatus(status, clock()) }

    suspend fun setProgress(id: String, page: Int) = update(id) { it.withProgress(page, clock()) }

    suspend fun deleteBook(id: String) = update(id) { it.copy(deleted = true) }

    suspend fun saveCategory(name: String, id: String = Ids.new()): String {
        cats.upsert(CategoryEntity(id = id, name = name.trim(), updatedAt = clock()))
        return id
    }

    suspend fun deleteCategory(id: String) = db.withTransaction {
        val now = clock()
        val c = cats.get(id) ?: return@withTransaction
        cats.upsert(c.copy(deleted = true, dirty = true, updatedAt = now))
        cats.linksForCategory(id).filterNot { it.deleted }
            .forEach { cats.upsertLink(it.copy(deleted = true, dirty = true, updatedAt = now)) }
    }

    private suspend fun update(id: String, change: (BookEntity) -> BookEntity) {
        val b = books.get(id) ?: return
        books.upsert(change(b).copy(updatedAt = clock(), dirty = true))
    }
}
