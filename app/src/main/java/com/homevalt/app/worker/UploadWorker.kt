package com.homevalt.app.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.homevalt.app.data.database.HomeVaultDatabase
import com.homevalt.app.data.database.UploadStatus
import com.homevalt.app.data.network.JwtInterceptor
import com.homevalt.app.data.network.RetrofitClient
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.util.Constants
import com.homevalt.app.util.NotificationHelper
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo() =
        NotificationHelper.uploadForegroundInfo(applicationContext, "Uploading...")

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

        val uploadId = inputData.getString("uploadId") ?: return Result.failure()
        val db = HomeVaultDatabase.getDatabase(applicationContext)
        val dao = db.uploadRequestDao()
        val entity = dao.getById(uploadId) ?: return Result.failure()

        val prefs = EncryptedPrefs(applicationContext)
        val token = prefs.getToken() ?: run {
            NotificationHelper.showNotification(
                applicationContext, Constants.SESSION_EXPIRED_NOTIFICATION_ID,
                NotificationHelper.buildErrorNotification(applicationContext, "Session expired", "Please log in again")
            )
            return Result.failure()
        }

        val baseUrl = prefs.getPublicUrl()
        val interceptor = JwtInterceptor(prefs)
        val api = RetrofitClient.create(baseUrl, interceptor)

        return try {
            val inputStream = applicationContext.contentResolver
                .openInputStream(Uri.parse(entity.localUri))
                ?: return Result.failure()

            val mediaType = entity.mimeType.toMediaTypeOrNull()
            val requestBody = object : RequestBody() {
                override fun contentType() = mediaType
                override fun writeTo(sink: BufferedSink) { sink.writeAll(inputStream.source()) }
            }
            val part = MultipartBody.Part.createFormData("file", entity.originalName, requestBody)
            val uploadResponse = api.uploadFile(part)

            when {
                uploadResponse.code() == 401 -> {
                    NotificationHelper.showNotification(
                        applicationContext, Constants.SESSION_EXPIRED_NOTIFICATION_ID,
                        NotificationHelper.buildErrorNotification(applicationContext, "Session expired", "Please log in again")
                    )
                    return Result.failure()
                }
                uploadResponse.code() == 413 || uploadResponse.code() == 400 -> {
                    dao.update(entity.copy(status = UploadStatus.FAILED))
                    NotificationHelper.showNotification(
                        applicationContext, Constants.UPLOAD_NOTIFICATION_ID,
                        NotificationHelper.buildErrorNotification(applicationContext, "Upload failed", entity.originalName)
                    )
                    return Result.failure()
                }
                uploadResponse.code() >= 500 -> {
                    dao.update(entity.copy(retryCount = entity.retryCount + 1))
                    return Result.retry()
                }
                !uploadResponse.isSuccessful -> {
                    dao.update(entity.copy(status = UploadStatus.FAILED))
                    return Result.failure()
                }
            }

            val remoteId = uploadResponse.body()?.id ?: return Result.failure()
            dao.update(entity.copy(remoteId = remoteId, status = UploadStatus.UPLOADING))

            repeat(Constants.MAX_POLL_ATTEMPTS) { _ ->
                delay(Constants.POLL_INTERVAL_MS)
                val detail = api.getFileDetail(remoteId)
                when {
                    detail.code() == 401 -> {
                        NotificationHelper.showNotification(
                            applicationContext, Constants.SESSION_EXPIRED_NOTIFICATION_ID,
                            NotificationHelper.buildErrorNotification(applicationContext, "Session expired", "Please log in again")
                        )
                        return Result.failure()
                    }
                    detail.isSuccessful -> when (detail.body()?.status) {
                        "STORED_ON_NAS" -> {
                            dao.update(entity.copy(status = UploadStatus.COMPLETED))
                            dao.delete(entity.copy(status = UploadStatus.COMPLETED))
                            NotificationHelper.showNotification(
                                applicationContext, Constants.UPLOAD_NOTIFICATION_ID,
                                NotificationHelper.buildCompletionNotification(applicationContext, "Upload complete", entity.originalName)
                            )
                            return Result.success()
                        }
                        "FAILED" -> {
                            dao.update(entity.copy(status = UploadStatus.FAILED))
                            NotificationHelper.showNotification(
                                applicationContext, Constants.UPLOAD_NOTIFICATION_ID,
                                NotificationHelper.buildErrorNotification(applicationContext, "Upload failed", entity.originalName)
                            )
                            return Result.failure()
                        }
                    }
                }
            }

            dao.update(entity.copy(status = UploadStatus.FAILED))
            NotificationHelper.showNotification(
                applicationContext, Constants.UPLOAD_NOTIFICATION_ID,
                NotificationHelper.buildErrorNotification(applicationContext, "Upload timeout", entity.originalName)
            )
            Result.failure()
        } catch (e: IOException) {
            dao.update(entity.copy(retryCount = entity.retryCount + 1))
            Result.retry()
        } catch (e: Exception) {
            dao.update(entity.copy(status = UploadStatus.FAILED))
            Result.failure()
        }
    }
}
