package com.exc33ded.library.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exc33ded.library.data.db.AppDatabase
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BookRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: BookRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = BookRepository(db, clock = { 1_000L })
    }

    @After fun tearDown() = db.close()

    @Test fun savedBookIsDirtyAndStamped() = runTest {
        val id = repo.saveBook(BookEntity(id = "b1", title = "Dune", dirty = false), emptySet())
        val saved = db.bookDao().get(id)!!
        assertTrue(saved.dirty)
        assertEquals(1_000L, saved.updatedAt)
    }

    @Test fun deleteIsSoftAndHiddenFromList() = runTest {
        repo.saveBook(BookEntity(id = "b1", title = "Dune"), emptySet())
        repo.deleteBook("b1")
        assertTrue(repo.observeBooks().first().isEmpty())
        assertTrue(db.bookDao().get("b1")!!.deleted)
    }

    @Test fun categoryLinksAreReplacedNotDuplicated() = runTest {
        val sf = repo.saveCategory("Sci-Fi")
        val classic = repo.saveCategory("Classic")
        repo.saveBook(BookEntity(id = "b1", title = "Dune"), setOf(sf, classic))
        repo.saveBook(BookEntity(id = "b1", title = "Dune"), setOf(sf))
        repo.saveBook(BookEntity(id = "b1", title = "Dune"), setOf(sf))
        assertEquals(setOf(sf), repo.categoryIdsFor("b1"))
        assertEquals(2, db.categoryDao().allLinksIncludingDeleted().size)
    }

    @Test fun setStatusAppliesRules() = runTest {
        repo.saveBook(BookEntity(id = "b1", title = "Dune", pageCount = 600), emptySet())
        repo.setStatus("b1", ReadingStatus.READ)
        val b = db.bookDao().get("b1")!!
        assertEquals(600, b.currentPage)
        assertEquals(1_000L, b.finishedAt)
    }

    @Test fun deletingCategoryRemovesItsLinks() = runTest {
        val sf = repo.saveCategory("Sci-Fi")
        repo.saveBook(BookEntity(id = "b1", title = "Dune"), setOf(sf))
        repo.deleteCategory(sf)
        assertTrue(repo.observeCategories().first().isEmpty())
        assertTrue(repo.categoryIdsFor("b1").isEmpty())
    }
}
