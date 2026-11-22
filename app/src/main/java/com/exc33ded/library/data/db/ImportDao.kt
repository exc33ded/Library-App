package com.exc33ded.library.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_queue WHERE state != 'DONE' ORDER BY createdAt")
    fun observeOpen(): Flow<List<ImportItemEntity>>

    @Query("SELECT * FROM import_queue WHERE id = :id")
    suspend fun get(id: String): ImportItemEntity?

    @Query("SELECT * FROM import_queue WHERE state = 'PENDING' ORDER BY createdAt LIMIT 1")
    suspend fun nextPending(): ImportItemEntity?

    /** Crash/stop recovery: anything left PROCESSING goes back to the queue. */
    @Query("UPDATE import_queue SET state = 'PENDING' WHERE state = 'PROCESSING'")
    suspend fun resetProcessing()

    @Insert suspend fun insertAll(items: List<ImportItemEntity>)
    @Update suspend fun update(item: ImportItemEntity)

    @Query("UPDATE import_queue SET state = 'DONE' WHERE id = :id")
    suspend fun markDone(id: String)

    /** Dismissing only touches finished-looking rows; PENDING/PROCESSING belong to the running worker. */
    @Query("UPDATE import_queue SET state = 'DONE' WHERE state = 'FAILED'")
    suspend fun dismissFailed()

    @Query("UPDATE import_queue SET state = 'DONE' WHERE state IN ('REVIEW', 'FAILED')")
    suspend fun dismissReviewable()

    @Query("DELETE FROM import_queue WHERE state = 'DONE'")
    suspend fun clearDone()
}
