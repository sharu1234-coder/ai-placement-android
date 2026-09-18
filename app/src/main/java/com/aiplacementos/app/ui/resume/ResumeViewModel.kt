package com.aiplacementos.app.ui.resume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.domain.repository.ResumeRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Resume screen: what the student currently has on file, plus the bytes of that PDF
 * for the in-app preview.
 *
 * The two are loaded separately on purpose. The summary is small and always worth showing; the
 * document is a multi-megabyte download that can fail on its own (offline, storage outage)
 * without that meaning "you have no resume". Folding them into one state would let a failed
 * preview hide a resume that is perfectly safely stored.
 *
 * Rendering deliberately lives in the screen, not here: it needs Android's PdfRenderer and
 * produces Bitmaps, neither of which exists on the JVM, so keeping this class free of them is
 * what lets its behaviour be unit-tested at all.
 */
class ResumeViewModel(private val resumeRepository: ResumeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ResumeSummaryDto?>>(UiState.Loading)
    val uiState: StateFlow<UiState<ResumeSummaryDto?>> = _uiState.asStateFlow()

    private val _preview = MutableStateFlow<UiState<ByteArray>>(UiState.Empty)
    val preview: StateFlow<UiState<ByteArray>> = _preview.asStateFlow()

    private val _removing = MutableStateFlow(false)
    val removing: StateFlow<Boolean> = _removing.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val summary = resumeRepository.getLatestResume()
                _uiState.value = UiState.Success(summary)
                // Only worth downloading once we know something is there to download.
                if (summary != null) loadPreview(summary.id) else _preview.value = UiState.Empty
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.toUserMessage("load your resume"),
                    isNetworkError = e.isNetworkError(),
                )
            }
        }
    }

    fun loadPreview(resumeId: Int) {
        viewModelScope.launch {
            _preview.value = UiState.Loading
            try {
                _preview.value = UiState.Success(resumeRepository.downloadResumeFile(resumeId))
            } catch (e: Exception) {
                _preview.value = UiState.Error(
                    e.toUserMessage("open your resume"),
                    isNetworkError = e.isNetworkError(),
                )
            }
        }
    }

    /**
     * Removes the resume and reloads from the server rather than trusting a local guess.
     *
     * [removing] gates the button so a second tap cannot fire a duplicate delete while the
     * first is still in flight.
     */
    fun removeResume(resumeId: Int) {
        if (_removing.value) return
        viewModelScope.launch {
            _removing.value = true
            try {
                resumeRepository.deleteResume(resumeId)
                _preview.value = UiState.Empty
                _uiState.value = UiState.Success(resumeRepository.getLatestResume())
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.toUserMessage("remove your resume"),
                    isNetworkError = e.isNetworkError(),
                )
            } finally {
                _removing.value = false
            }
        }
    }
}
