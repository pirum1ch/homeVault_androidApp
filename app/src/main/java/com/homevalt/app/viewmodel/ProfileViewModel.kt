package com.homevalt.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.data.repository.AuthRepository
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
    val autoPhotoBackup: Boolean = false
)

sealed class ProfileEvent {
    object NavigateToLogin : ProfileEvent()
    data class ShowMessage(val message: String) : ProfileEvent()
}

class ProfileViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val encryptedPrefs: EncryptedPrefs
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events = _events.asSharedFlow()

    init {
        _uiState.value = ProfileUiState(
            username = encryptedPrefs.getUsername() ?: "",
            publicUrl = encryptedPrefs.getPublicUrl(),
            localUrl = encryptedPrefs.getLocalUrl(),
            biometricEnabled = encryptedPrefs.isBiometricEnabled(),
            autoRefreshInterval = encryptedPrefs.getAutoRefreshInterval(),
            autoPhotoBackup = encryptedPrefs.isAutoPhotoBackupEnabled()
        )
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
        if (enabled) {
            encryptedPrefs.saveLastPhotoBackupTs(System.currentTimeMillis())
            PhotoBackupWorker.enqueue(getApplication())
        } else {
            PhotoBackupWorker.cancel(getApplication())
        }
        encryptedPrefs.saveAutoPhotoBackupEnabled(enabled)
        _uiState.value = _uiState.value.copy(autoPhotoBackup = enabled)
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _events.emit(ProfileEvent.NavigateToLogin)
        }
    }
}
