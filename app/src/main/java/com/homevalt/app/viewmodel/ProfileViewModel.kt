package com.homevalt.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homevalt.app.data.network.dto.NasConnectionRequest
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.data.repository.AuthRepository
import com.homevalt.app.data.repository.NasRepository
import com.homevalt.app.worker.PhotoBackupWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val username: String = "",
    val publicUrl: String = "",
    val localUrl: String = "",
    val biometricEnabled: Boolean = false,
    val autoRefreshInterval: Long = 0L,
    val autoPhotoBackup: Boolean = false,
    val connectionMode: String = "NAS",
    val userRole: String = "",
    val webDavHost: String = "",
    val webDavUsername: String = "",
    val webDavPath: String = "",
    val webDavConnectionId: String? = null,
    val isSavingWebDav: Boolean = false
)

sealed class ProfileEvent {
    object NavigateToLogin : ProfileEvent()
    data class ShowMessage(val message: String) : ProfileEvent()
}

class ProfileViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val encryptedPrefs: EncryptedPrefs,
    private val nasRepository: NasRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events = _events.asSharedFlow()

    init {
        val role = encryptedPrefs.getUserRole()
        _uiState.value = ProfileUiState(
            username = encryptedPrefs.getUsername() ?: "",
            publicUrl = encryptedPrefs.getPublicUrl(),
            localUrl = encryptedPrefs.getLocalUrl(),
            biometricEnabled = encryptedPrefs.isBiometricEnabled(),
            autoRefreshInterval = encryptedPrefs.getAutoRefreshInterval(),
            autoPhotoBackup = encryptedPrefs.isAutoPhotoBackupEnabled(),
            connectionMode = encryptedPrefs.getConnectionMode(),
            userRole = role
        )
        if (role == "ADMIN") {
            viewModelScope.launch { fetchWebDavConnection() }
        }
    }

    fun updatePublicUrl(url: String) {
        encryptedPrefs.savePublicUrl(url)
        _uiState.value = _uiState.value.copy(publicUrl = url)
        viewModelScope.launch { _events.emit(ProfileEvent.ShowMessage("Public URL saved")) }
    }

    fun updateLocalUrl(url: String) {
        encryptedPrefs.saveLocalUrl(url)
        _uiState.value = _uiState.value.copy(localUrl = url)
        viewModelScope.launch { _events.emit(ProfileEvent.ShowMessage("Local URL saved")) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        authRepository.setBiometricEnabled(enabled)
        _uiState.value = _uiState.value.copy(biometricEnabled = enabled)
    }

    fun setAutoRefreshInterval(ms: Long) {
        encryptedPrefs.saveAutoRefreshInterval(ms)
        _uiState.value = _uiState.value.copy(autoRefreshInterval = ms)
    }

    fun setAutoPhotoBackup(enabled: Boolean) {
        encryptedPrefs.saveAutoPhotoBackup(enabled)
        _uiState.value = _uiState.value.copy(autoPhotoBackup = enabled)
        val context = getApplication<Application>()
        if (enabled) PhotoBackupWorker.enqueue(context)
        else PhotoBackupWorker.cancel(context)
    }

    fun setConnectionMode(mode: String) {
        encryptedPrefs.saveConnectionMode(mode)
        _uiState.value = _uiState.value.copy(connectionMode = mode)
    }

    private suspend fun fetchWebDavConnection() {
        val result = nasRepository.getWebDavConnection()
        result.getOrNull()?.let { conn ->
            _uiState.value = _uiState.value.copy(
                webDavConnectionId = conn.id,
                webDavHost = conn.host,
                webDavUsername = conn.username ?: "",
                webDavPath = conn.path
            )
        }
    }

    fun saveWebDavConfig(host: String, username: String, password: String, path: String) {
        if (host.isBlank() || username.isBlank() || password.isBlank()) {
            viewModelScope.launch { _events.emit(ProfileEvent.ShowMessage("Host, username and password are required")) }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingWebDav = true)

            val request = NasConnectionRequest(
                type = "WEBDAV",
                name = "HomeVault WebDAV",
                host = host,
                username = username,
                password = password,
                path = path.ifBlank { "/uploads" }
            )

            val existingId = _uiState.value.webDavConnectionId
            val saveResult = if (existingId != null) {
                nasRepository.updateConnection(existingId, request)
            } else {
                nasRepository.createConnection(request)
            }

            if (saveResult.isFailure) {
                _uiState.value = _uiState.value.copy(isSavingWebDav = false)
                _events.emit(ProfileEvent.ShowMessage("Failed to save WebDAV config: ${saveResult.exceptionOrNull()?.message}"))
                return@launch
            }

            val savedId = saveResult.getOrNull()!!.id
            val activateResult = nasRepository.activateConnection(savedId)

            if (activateResult.isFailure) {
                _uiState.value = _uiState.value.copy(isSavingWebDav = false)
                _events.emit(ProfileEvent.ShowMessage("Saved but failed to activate: ${activateResult.exceptionOrNull()?.message}"))
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isSavingWebDav = false,
                webDavConnectionId = savedId,
                webDavHost = host,
                webDavUsername = username,
                webDavPath = path
            )
            _events.emit(ProfileEvent.ShowMessage("WebDAV connection saved and activated"))
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _events.emit(ProfileEvent.NavigateToLogin)
        }
    }
}
