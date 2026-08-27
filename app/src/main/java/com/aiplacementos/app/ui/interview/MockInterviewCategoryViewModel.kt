package com.aiplacementos.app.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.InterviewHistoryItemDto
import com.aiplacementos.app.domain.repository.InterviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MockInterviewCategoryViewModel(private val interviewRepository: InterviewRepository) : ViewModel() {
    private val _history = MutableStateFlow<List<InterviewHistoryItemDto>>(emptyList())
    val history: StateFlow<List<InterviewHistoryItemDto>> = _history.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _history.value = runCatching { interviewRepository.listInterviews() }.getOrDefault(emptyList())
        }
    }
}
