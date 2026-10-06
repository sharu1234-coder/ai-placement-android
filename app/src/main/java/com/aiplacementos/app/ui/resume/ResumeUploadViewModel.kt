package com.aiplacementos.app.ui.resume

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.security.MessageDigest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.data.remote.ResumeUploadResponseDto
import com.aiplacementos.app.domain.repository.ResumeRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024 // matches the backend's own cap

/**
 * This upload's operation identity: the file name, and a digest of the bytes being sent.
 *
 * Exactly the two things the server hashes into its request fingerprint, so the client's notion
 * of "the same upload" and the server's cannot drift apart. The digest is the identity; the file
 * itself is never stored anywhere but the request body.
 *
 * `internal` rather than private so it can be tested directly. upload() needs a real
 * ContentResolver and Uri, and this module has no Robolectric and no mocking library, so the
 * identity rule would otherwise have had no test at all -- and an untested identity rule is the
 * thing this phase exists to fix.
 */
internal fun resumeUploadIdentity(fileName: String, bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    return "$fileName\u0000$digest"
}

class ResumeUploadViewModel(private val resumeRepository: ResumeRepository) : ViewModel() {
    // One key per upload the student picked, kept until it succeeds. In memory, not
    // SavedStateHandle: the chosen file is held in memory too, so the operation cannot
    // outlive this view model -- after process death the student picks a file again, and
    // that is a new upload rather than a resumption of this one.
    //
    // Keyed by the file name and a digest of its bytes, which is exactly what the server
    // fingerprints for this operation. reset() exists so a student can pick a *different* file
    // after a failed upload, and that is precisely the case a single slot got wrong: the new
    // file was sent under the old file's key, and the server answered 409 because the
    // fingerprint no longer matched.
    private val uploadKeys = OperationKeyHolder()
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
                val identity = resumeUploadIdentity(fileName, bytes)
                val uploaded = resumeRepository.uploadResume(fileName, bytes, uploadKeys.keyFor(identity))
                uploadKeys.consume(identity)
                _uiState.value = UiState.Success(uploaded)
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
                _uiState.value = UiState.Error(e.toUserMessage("upload your resume"))
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
