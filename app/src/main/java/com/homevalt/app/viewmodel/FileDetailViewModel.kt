package com.homevalt.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.homevalt.app.data.network.dto.FileDto
import com.homevalt.app.data.repository.FileRepository
import com.homevalt.app.worker.DownloadWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FileDetailUiState {
    object Loading : FileDetailUiState()
    data class Success(val file: FileDto) : FileDetailUiState()
    data class Error(val message: String) : FileDetailUiState()
}

class FileDetailViewModel(
    application: Application,
    private val fileRepository: FileRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<FileDetailUiState>(FileDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private var currentFileId: String = ""

    fun loadFile(id: String) {
        currentFileId = id
        viewModelScope.launch {
            _uiState.value = FileDetailUiState.Loading
            val result = fileRepository.getFileDetail(id)
            if (result.isSuccess) {
                _uiState.value = FileDetailUiState.Success(result.getOrThrow())
            } else {
                _uiState.value = FileDetailUiState.Error(result.exceptionOrNull()?.message ?: "Failed to load file")
            }
        }
    }

    fun downloadFile() {
        val state = _uiState.value as? FileDetailUiState.Success ?: return
        val file = state.file
        val context = getApplication<Application>()
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("fileId" to file.id, "fileName" to file.name, "mimeType" to file.mimeType))
            .build()
        WorkManager.getInstance(context).enqueue(request)
        viewModelScope.launch { _events.emit("Download started: ${file.name}") }
    }

    fun deleteFile(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = fileRepository.deleteFile(currentFileId)
            if (result.isSuccess) onSuccess()
            else _events.emit(result.exceptionOrNull()?.message ?: "Delete failed")
        }
    }
}
