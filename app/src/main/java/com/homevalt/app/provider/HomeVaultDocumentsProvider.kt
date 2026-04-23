package com.homevalt.app.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.homevalt.app.R
import com.homevalt.app.data.database.HomeVaultDatabase
import com.homevalt.app.data.network.JwtInterceptor
import com.homevalt.app.data.network.NetworkMonitor
import com.homevalt.app.data.network.RetrofitClient
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.data.repository.NetworkSwitcher
import com.homevalt.app.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class HomeVaultDocumentsProvider : DocumentsProvider() {

    private lateinit var prefs: EncryptedPrefs
    private lateinit var database: HomeVaultDatabase

    companion object {
        const val AUTHORITY = "com.homevalt.app.documents"
        private const val ROOT_ID = "root"

        private val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY, Root.COLUMN_FLAGS, Root.COLUMN_ICON,
            Root.COLUMN_MIME_TYPES
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS
        )

        fun buildRootsUri(): Uri = Uri.parse("content://$AUTHORITY/roots")
    }

    override fun onCreate(): Boolean {
        prefs = EncryptedPrefs(context!!)
        database = HomeVaultDatabase.getDatabase(context!!)
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: ROOT_COLUMNS)
        if (prefs.getToken() == null) return cursor
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT_ID)
            add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            add(Root.COLUMN_TITLE, "HomeVault")
            add(Root.COLUMN_SUMMARY, prefs.getUsername() ?: "")
            add(Root.COLUMN_FLAGS,
                Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_LOCAL_ONLY)
            add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            add(Root.COLUMN_MIME_TYPES, "*/*")
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        context?.let { ctx ->
            SyncWorker.enqueuePeriodicSync(ctx)
            WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<SyncWorker>().build())
        }
        val files = runBlocking { database.cachedFileDao().getAll() }
        files.forEach { f ->
            cursor.newRow().apply {
                add(Document.COLUMN_DOCUMENT_ID, f.id)
                add(Document.COLUMN_DISPLAY_NAME, f.name)
                add(Document.COLUMN_MIME_TYPE, f.mimeType)
                add(Document.COLUMN_SIZE, f.size)
                add(Document.COLUMN_LAST_MODIFIED, f.lastModified)
                add(Document.COLUMN_FLAGS,
                    Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_WRITE)
            }
        }
        cursor.setNotificationUri(context!!.contentResolver, buildRootsUri())
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        if (documentId == ROOT_ID) {
            cursor.newRow().apply {
                add(Document.COLUMN_DOCUMENT_ID, ROOT_ID)
                add(Document.COLUMN_DISPLAY_NAME, "HomeVault")
                add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                add(Document.COLUMN_SIZE, 0L)
                add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
                add(Document.COLUMN_FLAGS, Document.FLAG_DIR_SUPPORTS_CREATE)
            }
            return cursor
        }
        val f = runBlocking { database.cachedFileDao().getById(documentId) } ?: return cursor
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, f.id)
            add(Document.COLUMN_DISPLAY_NAME, f.name)
            add(Document.COLUMN_MIME_TYPE, f.mimeType)
            add(Document.COLUMN_SIZE, f.size)
            add(Document.COLUMN_LAST_MODIFIED, f.lastModified)
            add(Document.COLUMN_FLAGS,
                Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_WRITE)
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        return if (mode.startsWith("r")) openForRead(documentId) else openForWrite(documentId)
    }

    private fun openForRead(documentId: String): ParcelFileDescriptor {
        val pipes = ParcelFileDescriptor.createPipe()
        val readEnd = pipes[0]
        val writeEnd = pipes[1]
        val ctx = context!!
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = buildApi(ctx)
                val response = api.downloadFile(documentId, download = true)
                response.body()?.use { body ->
                    ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { out ->
                        body.byteStream().copyTo(out)
                    }
                } ?: writeEnd.closeWithError("Download failed")
            } catch (e: Exception) {
                writeEnd.closeWithError(e.message ?: "Error")
            }
        }
        return readEnd
    }

    private fun openForWrite(documentId: String): ParcelFileDescriptor {
        val ctx = context!!
        val tmpFile = File(ctx.cacheDir, "saf_upload_$documentId")
        return ParcelFileDescriptor.open(
            tmpFile,
            ParcelFileDescriptor.MODE_READ_WRITE or
                ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE,
            Handler(Looper.getMainLooper())
        ) { error ->
            if (error == null && tmpFile.exists()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val api = buildApi(ctx)
                        val fileName = database.cachedFileDao().getById(documentId)?.name
                            ?: tmpFile.name
                        val reqBody = tmpFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                        val part = MultipartBody.Part.createFormData("file", fileName, reqBody)
                        api.uploadFile(part)
                    } catch (_: Exception) {
                    } finally {
                        tmpFile.delete()
                    }
                }
            }
        }
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String = "new_${System.currentTimeMillis()}_$displayName"

    override fun deleteDocument(documentId: String) {
        runBlocking {
            try { buildApi(context!!).deleteFile(documentId) } catch (_: Exception) {}
            database.cachedFileDao().deleteAll()
        }
    }

    private suspend fun buildApi(ctx: android.content.Context) =
        RetrofitClient.create(
            NetworkSwitcher(prefs, NetworkMonitor(ctx), ctx).getActiveBaseUrl(),
            JwtInterceptor(prefs)
        )
}
