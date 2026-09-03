package com.aiplacementos.app.ui.resume

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
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

private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024 // matches the backend's own cap

class ResumeUploadViewModel(private val resumeRepository: ResumeRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<ResumeUploadResponseDto>>(UiState.Empty)
    val uiState: StateFlow<UiState<ResumeUploadResponseDto>> = _uiState.asStateFlow()

    /** Lets the screen return to the "pick/upload" UI after a failed upload -- without this,
     * picking a new file after an Error has nothing to reset _uiState back to Empty, so the
     * Error card (and its Retry action) would show forever regardless of what's re-picked. */
    fun reset() {
        _uiState.value = UiState.Empty
    }

    fun upload(contentResolver: ContentResolver, uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // Check the picked file's size before reading it into memory -- the file
                // picker only restricts MIME type, not size, so without this a very large
                // PDF gets fully read into a ByteArray (risking OutOfMemoryError) before
                // the server's own 10MB limit is ever consulted.
                val declaredSize = querySize(contentResolver, uri)
                if (declaredSize != null && declaredSize > MAX_UPLOAD_BYTES) {
                    _uiState.value = UiState.Error("File is too large (max 10MB)")
                    return@launch
                }

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

/** Null if the content provider doesn't report a size (some don't) -- callers fall back to
 * the server-side check in that case rather than blocking a legitimate upload. */
private fun querySize(contentResolver: ContentResolver, uri: Uri): Long? {
    contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
            return cursor.getLong(sizeIndex)
        }
    }
    return null
}
