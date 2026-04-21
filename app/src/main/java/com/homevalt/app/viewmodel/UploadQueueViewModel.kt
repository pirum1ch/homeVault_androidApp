package com.homevalt.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homevalt.app.data.database.UploadRequestEntity
import com.homevalt.app.data.repository.FileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UploadQueueViewModel(
    application: Application,
    private val fileRepository: FileRepository
) : AndroidViewModel(application) {

    val uploads: StateFlow<List<UploadRequestEntity>> = fileRepository.getPendingUploads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancelUpload(id: String) {
        viewModelScope.launch { fileRepository.cancelUpload(id) }
    }
}
