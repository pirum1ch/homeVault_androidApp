package com.homevalt.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homevalt.app.data.network.dto.FileDto
import com.homevalt.app.data.preferences.EncryptedPrefs
import com.homevalt.app.data.repository.AuthRepository
import com.homevalt.app.data.repository.FileRepository
import com.homevalt.app.util.FileUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NasStatus { UNKNOWN, UP, DOWN }

sealed class FileListUiState {
    object Loading : FileListUiState()
    data class Success(val files: List<FileDto>, val hasMore: Boolean = true) : FileListUiState()
    data class Error(val message: String) : FileListUiState()
}

sealed class FileListEvent {
    object NavigateToLogin : FileListEvent()
    data class ShowError(val message: String) : FileListEvent()
    data class ShowMessage(val message: String) : FileListEvent()
}

class FileListViewModel(
    application: Application,
    private val fileRepository: FileRepository,
    private val authRepository: AuthRepository,
    private val encryptedPrefs: EncryptedPrefs
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<FileListUiState>(FileListUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _nasStatus = MutableStateFlow(NasStatus.UNKNOWN)
    val nasStatus = _nasStatus.asStateFlow()

    private val _events = MutableSharedFlow<FileListEvent>()
    val events = _events.asSharedFlow()

    private val _autoRefreshInterval = MutableStateFlow(encryptedPrefs.getAutoRefreshInterval())
    val autoRefreshInterval = _autoRefreshInterval.asStateFlow()

    private var currentPage = 0
    private val allFiles = mutableListOf<FileDto>()
    private val pageSize = 20
    private var autoRefreshJob: Job? = null

    companion object {
        private const val NAS_POLL_INTERVAL_MS = 30_000L
    }

    init {
        loadFiles()
        startNasHealthPolling()
    }

    private fun startNasHealthPolling() {
        viewModelScope.launch {
            while (true) {
                refreshNasHealth()
                delay(NAS_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshNasHealth() {
        val result = fileRepository.checkNasHealth()
        _nasStatus.value = when {
            result.isFailure -> NasStatus.DOWN
            result.getOrNull()?.status.equals("UP", ignoreCase = true) -> NasStatus.UP
            else -> NasStatus.DOWN
        }
    }

    fun loadFiles() {
        currentPage = 0
        allFiles.clear()
        viewModelScope.launch {
            _uiState.value = FileListUiState.Loading
            val result = fileRepository.getFiles(0, pageSize)
            if (result.isSuccess) {
                val files = result.getOrDefault(emptyList())
                allFiles.addAll(files)
                _uiState.value = FileListUiState.Success(allFiles.toList(), files.size == pageSize)
            } else {
                _uiState.value = FileListUiState.Error(result.exceptionOrNull()?.message ?: "Failed to load files")
            }
        }
    }

    fun refresh() = loadFiles()

    fun reloadIntervalFromPrefs() {
        _autoRefreshInterval.value = encryptedPrefs.getAutoRefreshInterval()
        restartAutoRefresh()
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun restartAutoRefresh() {
        autoRefreshJob?.cancel()
        val interval = _autoRefreshInterval.value
        if (interval <= 0L) return
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(interval)
                loadFiles()
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            currentPage++
            val result = fileRepository.getFiles(currentPage, pageSize)
            if (result.isSuccess) {
                val files = result.getOrDefault(emptyList())
                allFiles.addAll(files)
                _uiState.value = FileListUiState.Success(allFiles.toList(), files.size == pageSize)
            }
        }
    }

    fun enqueueUpload(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val name = FileUtils.getFileName(context, uri)
            val size = FileUtils.getFileSize(context, uri)
            val mime = FileUtils.getMimeType(context, uri)
            fileRepository.enqueueUpload(uri.toString(), name, size, mime)
            _events.emit(FileListEvent.ShowMessage("Upload queued: $name"))
        }
    }

    fun logout() {
        autoRefreshJob?.cancel()
        viewModelScope.launch {
            authRepository.logout()
            _events.emit(FileListEvent.NavigateToLogin)
        }
    }
}
