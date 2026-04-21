package com.homevalt.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: UploadRequestEntity)

    @Update
    suspend fun update(request: UploadRequestEntity)

    @Delete
    suspend fun delete(request: UploadRequestEntity)

    @Query("SELECT * FROM upload_entity WHERE status = :status")
    suspend fun getByStatus(status: UploadStatus): List<UploadRequestEntity>

    @Query("SELECT * FROM upload_entity")
    fun getAllFlow(): Flow<List<UploadRequestEntity>>

    @Query("SELECT * FROM upload_entity WHERE id = :id")
    suspend fun getById(id: String): UploadRequestEntity?

    @Query("DELETE FROM upload_entity WHERE status = :status")
    suspend fun deleteAllByStatus(status: UploadStatus)

    @Query("DELETE FROM upload_entity")
    suspend fun deleteAll()
}
