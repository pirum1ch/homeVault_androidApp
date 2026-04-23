package com.homevalt.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.homevalt.app.data.database.CachedFileEntity
import com.homevalt.app.data.database.HomeVaultDatabase
import com.homevalt.app.data.network.JwtInterceptor
import com.homevalt.app.data.network.NetworkMonitor
import com.homevalt.app.data.network.RetrofitClient
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.data.repository.NetworkSwitcher
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = EncryptedPrefs(applicationContext)
        prefs.getToken() ?: return Result.success()
        val jwtInterceptor = JwtInterceptor(prefs)
        val networkMonitor = NetworkMonitor(applicationContext)
        val networkSwitcher = NetworkSwitcher(prefs, networkMonitor, applicationContext)
        val api = RetrofitClient.create(networkSwitcher.getActiveBaseUrl(), jwtInterceptor)
        val dao = HomeVaultDatabase.getDatabase(applicationContext).cachedFileDao()

        return try {
            val allFiles = mutableListOf<CachedFileEntity>()
            var page = 0
            while (true) {
                val resp = api.listFiles(page = page, size = 50)
                val body = resp.body() ?: break
                body.content.forEach { dto ->
                    val lastModified = runCatching {
                        java.time.Instant.parse(dto.createdAt).toEpochMilli()
                    }.getOrElse { System.currentTimeMillis() }
                    allFiles.add(CachedFileEntity(
                        id = dto.id,
                        name = dto.name,
                        mimeType = dto.mimeType,
                        size = dto.size,
                        lastModified = lastModified
                    ))
                }
                if (page + 1 >= body.totalPages) break
                page++
            }
            dao.deleteAll()
            dao.insertAll(allFiles)
            applicationContext.contentResolver.notifyChange(
                android.net.Uri.parse("content://com.homevalt.app.documents/roots"), null
            )
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "homevault_saf_sync"

        fun enqueuePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
