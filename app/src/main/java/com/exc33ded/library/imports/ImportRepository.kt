package com.exc33ded.library.imports

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.exc33ded.library.data.Ids
import com.exc33ded.library.data.db.ImportDao
import com.exc33ded.library.data.db.ImportItemEntity
import com.exc33ded.library.data.db.ImportState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: ImportDao,
) {
    fun observeOpen() = dao.observeOpen()

    /** Picker URIs: keep read access so the worker can open them later. */
    suspend fun enqueueUris(uris: List<Uri>) {
        uris.forEach { runCatching { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        enqueue(uris.map { it.toString() })
    }

    suspend fun enqueue(sources: List<String>) {
        if (sources.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.insertAll(sources.mapIndexed { i, s -> ImportItemEntity(id = Ids.new(), source = s, createdAt = now + i) })
        schedule()
    }

    suspend fun retry(id: String) {
        dao.get(id)?.let { dao.update(it.copy(state = ImportState.PENDING, error = null, resultJson = null)) }
        schedule()
    }

    suspend fun markDone(id: String) = dao.markDone(id)
    suspend fun dismissFailed() = dao.dismissFailed()
    suspend fun dismissReviewable() = dao.dismissReviewable()
    suspend fun get(id: String) = dao.get(id)
    suspend fun clearDone() = dao.clearDone()

    private fun schedule() {
        // APPEND_OR_REPLACE: a run that is finishing won't swallow newly queued items.
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "imports", ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<ImportWorker>().build(),
        )
    }
}
