package com.aiplacementos.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ConversationDetailDto
import com.aiplacementos.app.domain.repository.ConversationRepository
import com.aiplacementos.app.ui.common.ErrorKind
import com.aiplacementos.app.ui.common.classify
import com.aiplacementos.app.ui.common.isRetryable
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConversationDetailUiState {
    data object Loading : ConversationDetailUiState

    data class Loaded(val conversation: ConversationDetailDto) : ConversationDetailUiState

    /**
     * [unavailable] is the 404 case, which the server returns both for a conversation that does
     * not exist and for one belonging to another student. The client cannot tell those apart and
     * must not try: possessing an id is not authorization, and retrying would not help either
     * way, so this state offers no retry.
     */
    data class Failed(
        val message: String,
        val retryable: Boolean,
        val unavailable: Boolean = false,
    ) : ConversationDetailUiState
}

/**
 * One saved conversation, read from the server every time it is opened.
 *
 * Nothing is carried over from the list row. The list is a summary and could be stale by the time
 * a student taps it, and the transcript is the authoritative record -- so the detail screen asks
 * the server rather than decorating what the list already had.
 */
class ConversationDetailViewModel(
    private val repository: ConversationRepository,
    private val conversationId: Int,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ConversationDetailUiState>(ConversationDetailUiState.Loading)
    val uiState: StateFlow<ConversationDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        if (_uiState.value is ConversationDetailUiState.Loading) return
        load()
    }

    private fun load() {
        _uiState.value = ConversationDetailUiState.Loading
        viewModelScope.launch {
            runCatching { repository.getConversation(conversationId) }
                .onSuccess { detail ->
                    _uiState.value = ConversationDetailUiState.Loaded(detail)
                }
                .onFailure { error ->
                    val kind = error.classify()
                    if (kind == ErrorKind.CANCELLED) return@onFailure
                    _uiState.value = ConversationDetailUiState.Failed(
                        message = error.toUserMessage("open this conversation"),
                        retryable = kind.isRetryable,
                        unavailable = kind == ErrorKind.NOT_FOUND,
                    )
                }
        }
    }
}
