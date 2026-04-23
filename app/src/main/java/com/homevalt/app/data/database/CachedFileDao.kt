package com.homevalt.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedFileDao {
    @Query("SELECT * FROM cached_files ORDER BY name ASC")
    suspend fun getAll(): List<CachedFileEntity>

    @Query("SELECT * FROM cached_files WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CachedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<CachedFileEntity>)

    @Query("DELETE FROM cached_files")
    suspend fun deleteAll()
}
