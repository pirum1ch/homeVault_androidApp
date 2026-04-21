package com.homevalt.app.worker

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.homevalt.app.data.network.JwtInterceptor
import com.homevalt.app.data.network.RetrofitClient
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.util.Constants
import com.homevalt.app.util.NotificationHelper
import java.io.IOException

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo() =
        NotificationHelper.downloadForegroundInfo(applicationContext, "Downloading...")

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        val fileId = inputData.getString("fileId") ?: return Result.failure()
        val fileName = inputData.getString("fileName") ?: return Result.failure()
        val mimeType = inputData.getString("mimeType") ?: return Result.failure()

        val prefs = EncryptedPrefs(applicationContext)
        if (prefs.getToken() == null) {
            NotificationHelper.showNotification(
                applicationContext, Constants.SESSION_EXPIRED_NOTIFICATION_ID,
                NotificationHelper.buildErrorNotification(applicationContext, "Session expired", "Please log in again")
            )
            return Result.failure()
        }

        val api = RetrofitClient.create(prefs.getPublicUrl(), JwtInterceptor(prefs))

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = applicationContext.contentResolver
        val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return Result.failure()

        return try {
            val response = api.downloadFile(fileId)

            if (!response.isSuccessful) {
                resolver.delete(targetUri, null, null)
                return when (response.code()) {
                    401 -> {
                        NotificationHelper.showNotification(
                            applicationContext, Constants.SESSION_EXPIRED_NOTIFICATION_ID,
                            NotificationHelper.buildErrorNotification(applicationContext, "Session expired", "Please log in again")
                        )
                        Result.failure()
                    }
                    else -> if (runAttemptCount >= 5) Result.failure() else Result.retry()
                }
            }

            val body = response.body() ?: run {
                resolver.delete(targetUri, null, null)
                return if (runAttemptCount >= 5) Result.failure() else Result.retry()
            }

            resolver.openOutputStream(targetUri)?.use { out ->
                body.byteStream().use { it.copyTo(out) }
            }

            val updateValues = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(targetUri, updateValues, null, null)

            NotificationHelper.showNotification(
                applicationContext, Constants.DOWNLOAD_NOTIFICATION_ID,
                NotificationHelper.buildCompletionNotification(applicationContext, "Download complete", fileName)
            )
            Result.success()
        } catch (e: IOException) {
            resolver.delete(targetUri, null, null)
            NotificationHelper.showNotification(
                applicationContext, Constants.DOWNLOAD_NOTIFICATION_ID,
                NotificationHelper.buildErrorNotification(applicationContext, "Not enough space", fileName)
            )
            Result.failure()
        } catch (e: Exception) {
            resolver.delete(targetUri, null, null)
            if (runAttemptCount >= 5) Result.failure() else Result.retry()
        }
    }
}
