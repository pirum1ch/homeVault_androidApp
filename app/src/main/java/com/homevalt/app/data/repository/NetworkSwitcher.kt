package com.homevalt.app.data.repository

import android.content.Context
import com.homevalt.app.data.network.NetworkMonitor
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class NetworkSwitcher(
    private val encryptedPrefs: EncryptedPrefs,
    private val networkMonitor: NetworkMonitor,
    private val context: Context
) {
    private var consecutiveFailures = 0
    private var cooldownUntil = 0L

    suspend fun isLocalReachable(): Boolean = withContext(Dispatchers.IO) {
        val localUrl = encryptedPrefs.getLocalUrl()
        if (localUrl.isBlank()) return@withContext false
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        return@withContext try {
            val response = client.newCall(
                Request.Builder().url(localUrl.trimEnd('/') + "/health/nas").build()
            ).execute()
            val ok = response.code == 200
            response.close()
            if (ok) { consecutiveFailures = 0; true }
            else { incrementFailures(); false }
        } catch (e: Exception) {
            incrementFailures()
            false
        }
    }

    private fun incrementFailures() {
        consecutiveFailures++
        if (consecutiveFailures >= 2) {
            cooldownUntil = System.currentTimeMillis() + Constants.LOCAL_FAIL_COOLDOWN_MS
        }
    }

    suspend fun getActiveBaseUrl(): String {
        val publicUrl = encryptedPrefs.getPublicUrl()
        if (System.currentTimeMillis() < cooldownUntil) return publicUrl
        if (!networkMonitor.isWifiConnected.value) return publicUrl
        val localUrl = encryptedPrefs.getLocalUrl()
        if (localUrl.isBlank()) return publicUrl
        return if (isLocalReachable()) localUrl else publicUrl
    }

    fun getPublicUrl(): String = encryptedPrefs.getPublicUrl()
    fun getLocalUrl(): String = encryptedPrefs.getLocalUrl()
}
