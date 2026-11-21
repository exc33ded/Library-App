package com.exc33ded.library.imports

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.exc33ded.library.data.db.ImportDao
import com.exc33ded.library.data.db.ImportState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Processes PENDING rows one at a time (sequential = natural Open Library rate limiting).
 * ponytail: plain (non-foreground) worker; if the OS stops it at its time limit, the next run
 * resumes from the queue (resetProcessing). Add a foreground notification if that proves annoying.
 */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val dao: ImportDao,
    private val processor: ImportProcessor,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        dao.resetProcessing()
        while (true) {
            val item = dao.nextPending() ?: break
            dao.update(item.copy(state = ImportState.PROCESSING))
            dao.update(processor.process(item))
        }
        return Result.success()
    }
}
