package com.aiplacementos.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ConversationDto
import com.aiplacementos.app.domain.repository.ConversationRepository
import com.aiplacementos.app.ui.common.classify
import com.aiplacementos.app.ui.common.isRetryable
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Which conversations the list is showing.
 *
 * [wire] is the server's own value, and `null` means "do not send the parameter" rather than
 * "send empty" -- the server rejects an unrecognised `kind` with 422, which is the behaviour this
 * enum exists to keep the client on the right side of.
 */
enum class ConversationFilter(val wire: String?, val label: String) {
    All(null, "All"),
    CareerCoach("career_coach", "Voice Career Coach"),
    MockInterview("mock_interview", "Mock Interviews"),
}

/** The load phase. Separate from the filter, which survives a reload. */
sealed interface HistoryPhase {
    data object Loading : HistoryPhase
    data object Loaded : HistoryPhase

    /** A reached-but-unhappy server or a transport failure. Never shown as an empty list. */
    data class Failed(val message: String, val retryable: Boolean) : HistoryPhase
}

data class ConversationHistoryUiState(
    val filter: ConversationFilter = ConversationFilter.All,
    val phase: HistoryPhase = HistoryPhase.Loading,
    val items: List<ConversationDto> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    /** The conversation currently being deleted, so its row can show progress and ignore taps. */
    val deletingId: Int? = null,
    /** A transient message about a delete that succeeded or failed. */
    val notice: String? = null,
) {
    /** Nothing at all, as opposed to nothing *matching* -- the two need different copy. */
    val isEmpty: Boolean
        get() = phase is HistoryPhase.Loaded && items.isEmpty() && filter == ConversationFilter.All

    val isFilteredEmpty: Boolean
        get() = phase is HistoryPhase.Loaded && items.isEmpty() && filter != ConversationFilter.All
}

/**
 * The unified history list.
 *
 * Every row comes from the authenticated server. There is no local cache and no local history
 * table: a conversation exists because the backend says it does, which is also why a failure is
 * reported as a failure rather than quietly becoming an empty list. Showing "no conversations yet"
 * to a student whose request timed out would tell them their record is gone.
 */
class ConversationHistoryViewModel(
    private val repository: ConversationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationHistoryUiState())
    val uiState: StateFlow<ConversationHistoryUiState> = _uiState.asStateFlow()

    /** Tracks the in-flight load so a filter change cannot be overtaken by the previous one. */
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onFilterSelected(filter: ConversationFilter) {
        if (_uiState.value.filter == filter) return
        // The list is replaced, not merged: a filter change starts a new page sequence.
        _uiState.value = _uiState.value.copy(
            filter = filter,
            phase = HistoryPhase.Loading,
            items = emptyList(),
            canLoadMore = false,
            notice = null,
        )
        load()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.value = _uiState.value.copy(isRefreshing = true, notice = null)
        load()
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(phase = HistoryPhase.Loading, notice = null)
        load()
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoadingMore || state.isRefreshing) return
        if (state.phase !is HistoryPhase.Loaded) return

        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            runCatching {
                repository.listConversations(
                    kind = state.filter.wire,
                    limit = PAGE_SIZE,
                    offset = state.items.size,
                )
            }.onSuccess { page ->
                val current = _uiState.value
                // De-duplicated by id: a conversation whose activity moved it between pages must
                // not appear twice.
                val known = current.items.map { it.id }.toSet()
                _uiState.value = current.copy(
                    items = current.items + page.filterNot { it.id in known },
                    isLoadingMore = false,
                    canLoadMore = page.size == PAGE_SIZE,
                )
            }.onFailure { error ->
                // The pages already shown stay: a failed *next* page is not a failed list.
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    canLoadMore = false,
                    notice = error.toUserMessage("load more conversations"),
                )
            }
        }
    }

    /**
     * Delete, then update. The row disappears only once the server has confirmed it, so a failed
     * delete never looks like a successful one.
     */
    fun deleteConversation(conversationId: Int) {
        if (_uiState.value.deletingId != null) return
        _uiState.value = _uiState.value.copy(deletingId = conversationId, notice = null)
        viewModelScope.launch {
            runCatching { repository.deleteConversation(conversationId) }
                .onSuccess {
                    val current = _uiState.value
                    _uiState.value = current.copy(
                        items = current.items.filterNot { it.id == conversationId },
                        deletingId = null,
                        notice = "Conversation deleted.",
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        deletingId = null,
                        notice = error.toUserMessage("delete this conversation"),
                    )
                }
        }
    }

    fun dismissNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    private fun load() {
        loadJob?.cancel()
        val filter = _uiState.value.filter
        loadJob = viewModelScope.launch {
            runCatching {
                repository.listConversations(kind = filter.wire, limit = PAGE_SIZE, offset = 0)
            }.onSuccess { page ->
                _uiState.value = _uiState.value.copy(
                    phase = HistoryPhase.Loaded,
                    items = page,
                    isRefreshing = false,
                    isLoadingMore = false,
                    canLoadMore = page.size == PAGE_SIZE,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    phase = HistoryPhase.Failed(
                        message = error.toUserMessage("load your conversations"),
                        retryable = error.classify().isRetryable,
                    ),
                    isRefreshing = false,
                    isLoadingMore = false,
                )
            }
        }
    }

    private companion object {
        /** Matches the server's default page; it caps anything above 100 with a 422. */
        const val PAGE_SIZE = 20
    }
}
