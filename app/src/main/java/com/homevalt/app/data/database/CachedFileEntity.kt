package com.homevalt.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_files")
data class CachedFileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val cachedAt: Long = System.currentTimeMillis()
)
