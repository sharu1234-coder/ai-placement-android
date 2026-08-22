package com.aiplacementos.app.ui.resume

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ResumeUploadResponseDto
import com.aiplacementos.app.domain.repository.ResumeRepository
import com.aiplacementos.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class ResumeUploadViewModel(private val resumeRepository: ResumeRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<ResumeUploadResponseDto>>(UiState.Empty)
    val uiState: StateFlow<UiState<ResumeUploadResponseDto>> = _uiState.asStateFlow()

    fun upload(contentResolver: ContentResolver, uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read the selected file")
                _uiState.value = UiState.Success(resumeRepository.uploadResume(fileName, bytes))
            } catch (e: HttpException) {
                _uiState.value = UiState.Error(
                    when (e.code()) {
                        415 -> "Only PDF files are supported"
                        413 -> "File is too large (max 10MB)"
                        422 -> "Couldn't read that PDF -- it may be scanned or empty"
                        else -> "Upload failed (${e.code()})"
                    }
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Couldn't reach the server. Check your connection.")
            }
        }
    }
}
