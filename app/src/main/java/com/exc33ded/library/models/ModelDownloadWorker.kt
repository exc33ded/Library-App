package com.exc33ded.library.models

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.exc33ded.library.settings.SettingsRepository
import com.exc33ded.library.storage.StorageRoots
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepo: SettingsRepository,
    private val roots: StorageRoots,
    private val downloader: ModelDownloader,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val spec = inputData.getString(KEY_ID)?.let(ModelCatalog::byId) ?: return Result.failure()
        val dir = roots.modelsDir(settingsRepo.settings.first().modelsLocation) ?: return fail("Storage location not available")
        dir.mkdirs()
        if (isInstalled(dir, spec)) return Result.success()
        val part = partFile(dir, spec)
        val needed = spec.sizeBytes - part.length().coerceAtMost(spec.sizeBytes)
        if (roots.freeBytes(dir) < needed + spec.sizeBytes / 20) return fail("Not enough free space (needs ${formatBytes(needed)} more)")

        setForeground(foregroundInfo(spec, part.length()))
        var lastReport = 0L
        try {
            downloader.download(spec, part) { done ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastReport >= 500) {
                    lastReport = now
                    setProgress(workDataOf(KEY_DONE to done))
                    setForeground(foregroundInfo(spec, done))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return if (runAttemptCount < 3) Result.retry() else fail("Network error: ${e.message}")
        }
        if (sha256Hex(part) != spec.sha256) { part.delete(); return fail("Download was corrupted (checksum mismatch)") }
        if (!part.renameTo(finalFile(dir, spec))) return fail("Could not save the model")
        return Result.success()
    }

    private fun fail(message: String) = Result.failure(workDataOf(KEY_ERROR to message))

    private fun foregroundInfo(spec: ModelSpec, done: Long): ForegroundInfo {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        val percent = (done * 100 / spec.sizeBytes).toInt().coerceIn(0, 100)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${spec.name}")
            .setContentText("${formatBytes(done)} of ${formatBytes(spec.sizeBytes)}")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return ForegroundInfo(spec.id.hashCode(), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val TAG = "model-download"
        const val KEY_ID = "id"
        const val KEY_DONE = "done"
        const val KEY_ERROR = "error"
        private const val CHANNEL = "models"
        fun workName(id: String) = "model-download-$id"
        fun modelTag(id: String) = "model:$id"
    }
}
