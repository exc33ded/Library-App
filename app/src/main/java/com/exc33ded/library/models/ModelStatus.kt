package com.exc33ded.library.models

import androidx.work.WorkInfo

sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus
    data class Paused(val bytes: Long) : ModelStatus            // a partial file exists but nothing is running
    data class Queued(val bytes: Long) : ModelStatus            // waiting for the network constraint (e.g. Wi-Fi)
    data class Downloading(val bytes: Long) : ModelStatus
    data class Ready(val bytes: Long) : ModelStatus
    data class Failed(val message: String, val bytes: Long) : ModelStatus
}

data class ModelUi(val spec: ModelSpec, val status: ModelStatus)

/** [state] is the WorkManager state of this model's download work (null = none); [doneBytes] its reported progress. */
fun deriveStatus(spec: ModelSpec, installed: Boolean, partBytes: Long, state: WorkInfo.State?, doneBytes: Long?, error: String?): ModelStatus = when {
    installed -> ModelStatus.Ready(spec.sizeBytes)
    state == WorkInfo.State.RUNNING -> ModelStatus.Downloading(doneBytes ?: partBytes)
    state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED -> ModelStatus.Queued(partBytes)
    state == WorkInfo.State.FAILED -> ModelStatus.Failed(error ?: "Download failed", partBytes)
    partBytes > 0 -> ModelStatus.Paused(partBytes)
    else -> ModelStatus.NotDownloaded
}
