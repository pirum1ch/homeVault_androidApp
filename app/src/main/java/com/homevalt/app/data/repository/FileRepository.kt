package com.homevalt.app.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.homevalt.app.data.database.HomeVaultDatabase
import com.homevalt.app.data.database.UploadRequestEntity
import com.homevalt.app.data.database.UploadStatus
import com.homevalt.app.data.network.HomeVaultApiService
import com.homevalt.app.data.network.JwtInterceptor
import com.homevalt.app.data.network.RetrofitClient
import com.homevalt.app.data.network.dto.FileDto
import com.homevalt.app.data.network.dto.HealthResponse
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.util.Constants
import com.homevalt.app.worker.UploadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.TimeUnit

class FileRepository(
    private val encryptedPrefs: EncryptedPrefs,
    private val networkSwitcher: NetworkSwitcher,
    private val jwtInterceptor: JwtInterceptor,
    private val database: HomeVaultDatabase,
    private val context: Context
) {
    private val mutex = Mutex()
    private var currentApiService: HomeVaultApiService? = null
    private var currentBaseUrl: String = ""

    private suspend fun getApiService(): HomeVaultApiService = mutex.withLock {
        val url = networkSwitcher.getActiveBaseUrl()
        if (url != currentBaseUrl || currentApiService == null) {
            currentBaseUrl = url
            currentApiService = RetrofitClient.create(url, jwtInterceptor)
        }
        currentApiService!!
    }

    suspend fun getFiles(page: Int = 0, size: Int = Constants.PAGE_SIZE): Result<List<FileDto>> {
        return try {
            val response = getApiService().listFiles(page, size)
            if (response.isSuccessful) Result.success(response.body()?.content ?: emptyList())
            else Result.failure(Exception("HTTP ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFileDetail(id: String): Result<FileDto> {
        return try {
            val response = getApiService().getFileDetail(id)
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else Result.failure(Exception("HTTP ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(id: String): Result<Unit> {
        return try {
            val response = getApiService().deleteFile(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("HTTP ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enqueueUpload(localUri: String, name: String, sizeBytes: Long, mimeType: String) {
        val entity = UploadRequestEntity(
            id = UUID.randomUUID().toString(),
            localUri = localUri,
            originalName = name,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            status = UploadStatus.PENDING
        )
        database.uploadRequestDao().insert(entity)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf("uploadId" to entity.id))
            .addTag(entity.id)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "upload_${entity.id}", ExistingWorkPolicy.KEEP, request
        )
    }

    suspend fun checkNasHealth(): Result<HealthResponse> {
        return try {
            val response = getApiService().checkHealth()
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body)
            else Result.failure(Exception("HTTP ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPendingUploads(): Flow<List<UploadRequestEntity>> =
        database.uploadRequestDao().getAllFlow()

    suspend fun cancelUpload(uploadId: String) {
        database.uploadRequestDao().getById(uploadId)?.let {
            database.uploadRequestDao().delete(it)
        }
        WorkManager.getInstance(context).cancelUniqueWork("upload_$uploadId")
    }
}
