package com.aiplacementos.app.ui.history

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeConversationRepository
import com.aiplacementos.app.fakes.conversationDetail
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.fakes.turn
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * One saved conversation.
 *
 * The case worth pinning is the 404. The server answers 404 both for a conversation that does not
 * exist and for one belonging to another student, deliberately, so that possessing an id proves
 * nothing. The client must therefore treat it as "not available to you" and must **not** offer a
 * retry, because retrying cannot change the answer either way.
 */
class ConversationDetailViewModelTest {

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val repository = FakeConversationRepository()

    private fun viewModel(id: Int = 1) = ConversationDetailViewModel(repository, id)

    @Test
    fun `a conversation loads with its turns`() {
        repository.onDetail = { conversationDetail(id = 5) }

        val state = viewModel(5).uiState.value

        assertTrue(state is ConversationDetailUiState.Loaded)
        assertEquals(5, (state as ConversationDetailUiState.Loaded).conversation.id)
        assertEquals(2, state.conversation.turns.size)
    }

    @Test
    fun `the server's turn order is preserved exactly`() {
        // Deliberately handed to the client out of order: the server's `seq` is authoritative and
        // the view model must not re-sort, re-number or reverse it.
        repository.onDetail = {
            conversationDetail(
                turns = listOf(
                    turn(1, "user", "first"),
                    turn(2, "assistant", "second"),
                    turn(3, "user", "third"),
                    turn(4, "assistant", "fourth"),
                ),
            )
        }

        val state = viewModel().uiState.value as ConversationDetailUiState.Loaded

        assertEquals(listOf(1, 2, 3, 4), state.conversation.turns.map { it.seq })
        assertEquals(
            listOf("first", "second", "third", "fourth"),
            state.conversation.turns.map { it.text },
        )
    }

    @Test
    fun `a conversation with no turns still loads`() {
        repository.onDetail = { conversationDetail(turns = emptyList()) }

        val state = viewModel().uiState.value

        assertTrue(state is ConversationDetailUiState.Loaded)
        assertTrue((state as ConversationDetailUiState.Loaded).conversation.turns.isEmpty())
    }

    @Test
    fun `an interview conversation keeps its interview link`() {
        repository.onDetail = { conversationDetail(kind = "mock_interview", interviewId = 42) }

        val state = viewModel().uiState.value as ConversationDetailUiState.Loaded

        assertEquals("mock_interview", state.conversation.kind)
        assertEquals(42, state.conversation.interviewId)
    }

    @Test
    fun `a 404 is unavailable and offers no retry`() {
        repository.onDetail = { throw httpException(404) }

        val state = viewModel().uiState.value

        assertTrue(state is ConversationDetailUiState.Failed)
        val failed = state as ConversationDetailUiState.Failed
        assertTrue("a 404 must be reported as unavailable", failed.unavailable)
        assertFalse("retrying a 404 cannot change the answer", failed.retryable)
    }

    @Test
    fun `possessing an id is not authorization`() {
        // The client asked for a real-looking id and was refused. It must not fall back to
        // anything, cache anything, or present a partial conversation.
        repository.onDetail = { throw httpException(404) }

        val state = viewModel(12345).uiState.value

        assertTrue(state is ConversationDetailUiState.Failed)
    }

    @Test
    fun `a transport failure offers a retry`() {
        repository.onDetail = { throw UnknownHostException("no route") }

        val failed = viewModel().uiState.value as ConversationDetailUiState.Failed

        assertTrue(failed.retryable)
        assertFalse(failed.unavailable)
    }

    @Test
    fun `an expired session is not retried as if it were transport`() {
        repository.onDetail = { throw httpException(401) }

        val failed = viewModel().uiState.value as ConversationDetailUiState.Failed

        assertFalse(failed.retryable)
        assertFalse(failed.unavailable)
    }

    @Test
    fun `retry reloads after a recoverable failure`() {
        var attempt = 0
        repository.onDetail = {
            attempt += 1
            if (attempt == 1) throw UnknownHostException("no route") else conversationDetail(id = 3)
        }

        val vm = viewModel(3)
        assertTrue(vm.uiState.value is ConversationDetailUiState.Failed)

        vm.retry()

        assertTrue(vm.uiState.value is ConversationDetailUiState.Loaded)
    }
}
