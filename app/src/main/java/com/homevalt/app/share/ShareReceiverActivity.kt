package com.homevalt.app.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.homevalt.app.MainActivity
import com.homevalt.app.data.database.HomeVaultDatabase
import com.homevalt.app.data.database.UploadRequestEntity
import com.homevalt.app.data.database.UploadStatus
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.util.Constants
import com.homevalt.app.util.FileUtils
import com.homevalt.app.util.NotificationHelper
import com.homevalt.app.worker.UploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = EncryptedPrefs(this)
        if (prefs.getToken() == null) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        val uris = extractUris(intent)
        if (uris.isEmpty()) { finish(); return }

        val fileNames = uris.map { FileUtils.getFileName(this, it) }

        AlertDialog.Builder(this)
            .setTitle("Upload to HomeVault")
            .setMessage(fileNames.joinToString("\n") { "  • $it" })
            .setPositiveButton("Upload") { _, _ -> enqueueUploads(uris) }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun extractUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            listOfNotNull(uri)
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
        }
        else -> emptyList()
    }

    private fun enqueueUploads(uris: List<Uri>) {
        val db = HomeVaultDatabase.getDatabase(this)
        val wm = WorkManager.getInstance(this)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val entity = UploadRequestEntity(
                        id = UUID.randomUUID().toString(),
                        localUri = uri.toString(),
                        originalName = FileUtils.getFileName(this@ShareReceiverActivity, uri),
                        sizeBytes = FileUtils.getFileSize(this@ShareReceiverActivity, uri),
                        mimeType = FileUtils.getMimeType(this@ShareReceiverActivity, uri),
                        status = UploadStatus.PENDING
                    )
                    db.uploadRequestDao().insert(entity)
                    wm.enqueueUniqueWork(
                        "upload_${entity.id}", ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<UploadWorker>()
                            .setConstraints(constraints)
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .setInputData(workDataOf("uploadId" to entity.id))
                            .build()
                    )
                }
            }
            NotificationHelper.showNotification(
                this@ShareReceiverActivity, Constants.UPLOAD_NOTIFICATION_ID,
                NotificationHelper.buildUploadNotification(this@ShareReceiverActivity, "Uploading ${uris.size} file(s)...")
            )
            finish()
        }
    }
}
