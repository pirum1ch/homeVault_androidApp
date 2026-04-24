package com.homevalt.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.homevalt.app.data.database.HomeVaultDatabase
import com.homevalt.app.data.database.UploadStatus
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.util.NotificationHelper
import com.homevalt.app.worker.PhotoBackupWorker
import com.homevalt.app.worker.UploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        if (EncryptedPrefs(this).isAutoPhotoBackupEnabled()) PhotoBackupWorker.enqueue(this)
        CoroutineScope(Dispatchers.IO).launch {
            val db = HomeVaultDatabase.getDatabase(applicationContext)
            val pending = db.uploadRequestDao().getByStatus(UploadStatus.PENDING)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            pending.forEach { entity ->
                val request = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(constraints)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setInputData(workDataOf("uploadId" to entity.id))
                    .build()
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "upload_${entity.id}", ExistingWorkPolicy.KEEP, request
                )
            }
        }
    }
}
