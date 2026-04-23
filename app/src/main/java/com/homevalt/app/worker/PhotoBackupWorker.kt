package com.homevalt.app.worker

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.homevalt.app.data.database.HomeVaultDatabase
import com.homevalt.app.data.database.UploadRequestEntity
import com.homevalt.app.data.database.UploadStatus
import com.homevalt.app.data.preferences.EncryptedPrefs
import java.util.UUID
import java.util.concurrent.TimeUnit

class PhotoBackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = EncryptedPrefs(applicationContext)
        if (!prefs.isAutoPhotoBackupEnabled()) return Result.success()
        if (prefs.getToken() == null) return Result.success()

        val lastTs = prefs.getLastPhotoBackupTs()
        val scanStartTs = System.currentTimeMillis()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )
        // DATE_ADDED is in seconds; lastTs is millis
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf((lastTs / 1000).toString())

        val cursor = applicationContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} ASC"
        ) ?: return Result.retry()

        val db = HomeVaultDatabase.getDatabase(applicationContext)
        val wm = WorkManager.getInstance(applicationContext)

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (it.moveToNext()) {
                val mediaId = it.getLong(idCol)
                val name = it.getString(nameCol) ?: "photo_$mediaId.jpg"
                val size = it.getLong(sizeCol)
                val mime = it.getString(mimeCol) ?: "image/jpeg"
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId
                )

                val entity = UploadRequestEntity(
                    id = UUID.randomUUID().toString(),
                    localUri = uri.toString(),
                    originalName = name,
                    sizeBytes = size,
                    mimeType = mime,
                    status = UploadStatus.PENDING
                )
                db.uploadRequestDao().insert(entity)

                val request = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .setInputData(workDataOf("uploadId" to entity.id))
                    .addTag(entity.id)
                    .build()

                wm.enqueueUniqueWork("upload_${entity.id}", ExistingWorkPolicy.KEEP, request)
            }
        }

        prefs.saveLastPhotoBackupTs(scanStartTs)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "homevault_photo_backup"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<PhotoBackupWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
