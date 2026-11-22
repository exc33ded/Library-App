package com.exc33ded.library.models

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.exc33ded.library.settings.ModelsLocation
import com.exc33ded.library.settings.SettingsRepository
import com.exc33ded.library.storage.StorageRoots
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settingsRepo: SettingsRepository,
    private val roots: StorageRoots,
) {
    private val wm get() = WorkManager.getInstance(ctx)
    private val active = setOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)

    fun observe(): Flow<List<ModelUi>> =
        combine(settingsRepo.settings, wm.getWorkInfosByTagFlow(ModelDownloadWorker.TAG)) { s, infos ->
            val dir = roots.modelsDir(s.modelsLocation)
            ModelCatalog.all.map { spec ->
                val mine = infos.filter { ModelDownloadWorker.modelTag(spec.id) in it.tags }
                val info = mine.firstOrNull { it.state in active } ?: mine.firstOrNull { it.state == WorkInfo.State.FAILED }
                val installed = dir != null && isInstalled(dir, spec)
                val part = if (dir != null) partFile(dir, spec).let { if (it.isFile) it.length() else 0L } else 0L
                ModelUi(
                    spec,
                    deriveStatus(
                        spec, installed, part, info?.state,
                        info?.progress?.getLong(ModelDownloadWorker.KEY_DONE, -1L)?.takeIf { it >= 0 },
                        info?.outputData?.getString(ModelDownloadWorker.KEY_ERROR),
                    ),
                )
            }
        }

    suspend fun download(id: String) {
        val wifiOnly = settingsRepo.settings.first().wifiOnlyDownloads
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_ID to id))
            .addTag(ModelDownloadWorker.TAG)
            .addTag(ModelDownloadWorker.modelTag(id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(ModelDownloadWorker.workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    /** Stops the download but keeps the partial file so it can resume. */
    fun cancel(id: String) { wm.cancelUniqueWork(ModelDownloadWorker.workName(id)) }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        cancel(id)
        val spec = ModelCatalog.byId(id) ?: return@withContext
        val dir = roots.modelsDir(settingsRepo.settings.first().modelsLocation) ?: return@withContext
        finalFile(dir, spec).delete()
        partFile(dir, spec).delete()
    }

    /** Moves installed models to [to] and saves the setting. Returns an error message, or null on success. */
    suspend fun moveModels(to: ModelsLocation): String? = withContext(Dispatchers.IO) {
        val current = settingsRepo.settings.first().modelsLocation
        val from = roots.modelsDir(current)
        val dest = roots.modelsDir(to) ?: return@withContext "That location is not available on this phone"
        if (from == dest) return@withContext null
        if (wm.getWorkInfosByTag(ModelDownloadWorker.TAG).get().any { it.state in active }) return@withContext "Wait for downloads to finish first"
        dest.mkdirs()
        val files = from?.listFiles()?.filter { it.isFile }.orEmpty()
        val installed = files.filter { !it.name.endsWith(".part") }
        val needed = installed.sumOf { it.length() }
        if (roots.freeBytes(dest) < needed + needed / 20) return@withContext "Not enough free space on the new location"
        files.filter { it.name.endsWith(".part") }.forEach { it.delete() }
        for (f in installed) {
            val target = File(dest, f.name)
            f.copyTo(target, overwrite = true)
            if (target.length() != f.length()) { target.delete(); return@withContext "Copy failed for ${f.name}" }
            f.delete()
        }
        settingsRepo.update { it.copy(modelsLocation = to) }
        null
    }
}
