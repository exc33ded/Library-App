package com.exc33ded.library.imports

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exc33ded.library.data.db.AppDatabase
import com.exc33ded.library.data.db.ImportItemEntity
import com.exc33ded.library.data.db.ImportState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ImportDismissTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private suspend fun seed() = db.importDao().insertAll(
        listOf(
            ImportItemEntity("pending", "s1", ImportState.PENDING, createdAt = 1),
            ImportItemEntity("processing", "s2", ImportState.PROCESSING, createdAt = 2),
            ImportItemEntity("review", "s3", ImportState.REVIEW, createdAt = 3),
            ImportItemEntity("failed", "s4", ImportState.FAILED, createdAt = 4),
        ),
    )

    private suspend fun openIds() = db.importDao().observeOpen().first().map { it.id }

    @Test fun dismissFailedOnlyRemovesFailedRows() = runTest {
        seed()
        db.importDao().dismissFailed()
        assertEquals(listOf("pending", "processing", "review"), openIds())
    }

    @Test fun dismissReviewableRemovesReviewAndFailedButNeverRunningWork() = runTest {
        seed()
        db.importDao().dismissReviewable()
        assertEquals(listOf("pending", "processing"), openIds())
    }

    @Test fun markDoneDismissesOneRow() = runTest {
        seed()
        db.importDao().markDone("review")
        assertEquals(listOf("pending", "processing", "failed"), openIds())
    }
}
