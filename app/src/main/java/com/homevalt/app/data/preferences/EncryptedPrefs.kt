package com.homevalt.app.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.homevalt.app.util.Constants

class EncryptedPrefs(context: Context) {

    companion object {
        private const val PREFS_FILE_NAME = "secure_prefs"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_PUBLIC_BASE_URL = "public_base_url"
        private const val KEY_LOCAL_BASE_URL = "local_base_url"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_ASKED = "biometric_asked"
        private const val KEY_USERNAME = "username"
        private const val KEY_AUTO_REFRESH = "auto_refresh_interval_ms"
        private const val KEY_AUTO_PHOTO_BACKUP = "auto_photo_backup"
        private const val KEY_LAST_PHOTO_BACKUP_TS = "last_photo_backup_ts"
        private const val KEY_CONNECTION_MODE = "connection_mode"
        private const val KEY_USER_ROLE = "user_role"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) { prefs.edit().putString(KEY_JWT_TOKEN, token).apply() }
    fun getToken(): String? = prefs.getString(KEY_JWT_TOKEN, null)
    fun clearToken() { prefs.edit().remove(KEY_JWT_TOKEN).apply() }

    fun savePublicUrl(url: String) { prefs.edit().putString(KEY_PUBLIC_BASE_URL, url).apply() }
    fun getPublicUrl(): String = prefs.getString(KEY_PUBLIC_BASE_URL, Constants.DEFAULT_PUBLIC_URL) ?: Constants.DEFAULT_PUBLIC_URL

    fun saveLocalUrl(url: String) { prefs.edit().putString(KEY_LOCAL_BASE_URL, url).apply() }
    fun getLocalUrl(): String = prefs.getString(KEY_LOCAL_BASE_URL, Constants.DEFAULT_LOCAL_URL) ?: Constants.DEFAULT_LOCAL_URL

    fun saveBiometricEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply() }
    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun saveBiometricAsked(asked: Boolean) { prefs.edit().putBoolean(KEY_BIOMETRIC_ASKED, asked).apply() }
    fun isBiometricAsked(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ASKED, false)

    fun saveUsername(name: String) { prefs.edit().putString(KEY_USERNAME, name).apply() }
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun saveAutoRefreshInterval(ms: Long) { prefs.edit().putLong(KEY_AUTO_REFRESH, ms).apply() }
    fun getAutoRefreshInterval(): Long = prefs.getLong(KEY_AUTO_REFRESH, 0L)

    fun saveAutoPhotoBackup(enabled: Boolean) { prefs.edit().putBoolean(KEY_AUTO_PHOTO_BACKUP, enabled).apply() }
    fun isAutoPhotoBackupEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_PHOTO_BACKUP, false)

    fun saveLastPhotoBackupTs(ts: Long) { prefs.edit().putLong(KEY_LAST_PHOTO_BACKUP_TS, ts).apply() }
    fun getLastPhotoBackupTs(): Long = prefs.getLong(KEY_LAST_PHOTO_BACKUP_TS, 0L)

    fun saveConnectionMode(mode: String) { prefs.edit().putString(KEY_CONNECTION_MODE, mode).apply() }
    fun getConnectionMode(): String = prefs.getString(KEY_CONNECTION_MODE, "NAS") ?: "NAS"

    fun saveUserRole(role: String) { prefs.edit().putString(KEY_USER_ROLE, role).apply() }
    fun getUserRole(): String = prefs.getString(KEY_USER_ROLE, "") ?: ""

    fun clear() { prefs.edit().clear().apply() }
}
