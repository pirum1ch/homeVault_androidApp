package com.homevalt.app.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.homevalt.app.data.database.HomeVaultDatabase
import com.homevalt.app.data.network.JwtInterceptor
import com.homevalt.app.data.network.RetrofitClient
import com.homevalt.app.data.network.dto.LoginRequest
import com.homevalt.app.data.preferences.EncryptedPrefs

class AuthRepository(
    private val encryptedPrefs: EncryptedPrefs,
    private val networkSwitcher: NetworkSwitcher,
    private val jwtInterceptor: JwtInterceptor,
    private val database: HomeVaultDatabase,
    private val context: Context
) {
    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val baseUrl = networkSwitcher.getActiveBaseUrl()
            val api = RetrofitClient.create(baseUrl, jwtInterceptor)
            val response = api.login(LoginRequest(username, password))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                encryptedPrefs.saveToken(body.token)
                encryptedPrefs.saveUsername(username)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Login failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        encryptedPrefs.clearToken()
        encryptedPrefs.saveUsername("")
        encryptedPrefs.saveBiometricEnabled(false)
        encryptedPrefs.saveBiometricAsked(false)
        database.uploadRequestDao().deleteAll()
        WorkManager.getInstance(context).cancelAllWork()
    }

    fun isLoggedIn(): Boolean = encryptedPrefs.getToken() != null
    fun getUsername(): String? = encryptedPrefs.getUsername()
    fun isBiometricEnabled(): Boolean = encryptedPrefs.isBiometricEnabled()
    fun setBiometricEnabled(enabled: Boolean) = encryptedPrefs.saveBiometricEnabled(enabled)
    fun isBiometricAsked(): Boolean = encryptedPrefs.isBiometricAsked()
    fun setBiometricAsked(asked: Boolean) = encryptedPrefs.saveBiometricAsked(asked)
}
