package com.homevalt.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "upload_entity")
data class UploadRequestEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val localUri: String,
    val originalName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val remoteId: String? = null,
    val status: UploadStatus = UploadStatus.PENDING,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
