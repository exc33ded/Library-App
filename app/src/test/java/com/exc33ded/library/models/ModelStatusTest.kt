package com.exc33ded.library.models

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelStatusTest {
    private val spec = ModelCatalog.all.first()

    @Test fun installedWinsOverAnyState() {
        for (s in listOf(null, WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.FAILED)) {
            assertEquals(ModelStatus.Ready(spec.sizeBytes), deriveStatus(spec, true, 5, s, 10, "x"))
        }
    }

    @Test fun runningUsesReportedBytesOrPartBytes() {
        assertEquals(ModelStatus.Downloading(10), deriveStatus(spec, false, 3, WorkInfo.State.RUNNING, 10, null))
        assertEquals(ModelStatus.Downloading(3), deriveStatus(spec, false, 3, WorkInfo.State.RUNNING, null, null))
    }

    @Test fun enqueuedIsQueued() {
        assertEquals(ModelStatus.Queued(3), deriveStatus(spec, false, 3, WorkInfo.State.ENQUEUED, null, null))
    }

    @Test fun failedCarriesMessageOrDefault() {
        assertEquals(ModelStatus.Failed("boom", 4), deriveStatus(spec, false, 4, WorkInfo.State.FAILED, null, "boom"))
        assertEquals(ModelStatus.Failed("Download failed", 4), deriveStatus(spec, false, 4, WorkInfo.State.FAILED, null, null))
    }

    @Test fun otherStatesFallBackToPausedOrNotDownloaded() {
        for (s in listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED, null)) {
            assertEquals(ModelStatus.Paused(7), deriveStatus(spec, false, 7, s, null, null))
            assertEquals(ModelStatus.NotDownloaded, deriveStatus(spec, false, 0, s, null, null))
        }
    }
}
