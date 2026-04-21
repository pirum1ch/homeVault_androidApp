package com.homevalt.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class LoginViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val encryptedPrefs: EncryptedPrefs
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()
    fun isBiometricEnabled(): Boolean = authRepository.isBiometricEnabled()

    fun getPublicUrl(): String = encryptedPrefs.getPublicUrl()
    fun savePublicUrl(url: String) { encryptedPrefs.savePublicUrl(url.trim()) }

    fun login(username: String, password: String, enableBiometric: Boolean, customPublicUrl: String? = null) {
        if (username.isBlank() || password.isBlank()) {
            viewModelScope.launch { _events.emit("Username and password are required") }
            return
        }
        if (customPublicUrl != null) {
            encryptedPrefs.savePublicUrl(customPublicUrl.trim())
        }
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            val result = authRepository.login(username, password)
            if (result.isSuccess) {
                if (enableBiometric) authRepository.setBiometricEnabled(true)
                _uiState.value = LoginUiState.Success
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Login failed"
                _uiState.value = LoginUiState.Error(msg)
                _events.emit(msg)
            }
        }
    }

    fun loginWithBiometric() {
        if (authRepository.isLoggedIn()) {
            _uiState.value = LoginUiState.Success
        } else {
            _uiState.value = LoginUiState.Error("Session expired. Please log in again.")
        }
    }

    fun resetState() { _uiState.value = LoginUiState.Idle }
}
