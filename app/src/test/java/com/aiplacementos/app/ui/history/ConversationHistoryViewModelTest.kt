package com.aiplacementos.app.ui.history

import kotlinx.coroutines.CompletableDeferred
import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.fakes.FakeConversationRepository
import com.aiplacementos.app.fakes.conversation
import com.aiplacementos.app.fakes.httpException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The unified history list.
 *
 * The behaviour worth defending here is that a **failure is never shown as an empty list**.
 * Telling a student "no conversations yet" because a request timed out says their record is gone,
 * which is the UI equivalent of describing an unverified state as fact. Several tests below exist
 * only to pin that distinction.
 */
class ConversationHistoryViewModelTest {

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val repository = FakeConversationRepository()

    private fun viewModel() = ConversationHistoryViewModel(repository)

    // ------------------------------------------------------------------ loading

    @Test
    fun `a loaded list is reported as loaded`() {
        repository.onList = { listOf(conversation(1), conversation(2)) }

        val state = viewModel().uiState.value

        assertEquals(HistoryPhase.Loaded, state.phase)
        assertEquals(listOf(1, 2), state.items.map { it.id })
        assertFalse(state.isEmpty)
    }

    @Test
    fun `an empty history is empty, not a failure`() {
        repository.onList = { emptyList() }

        val state = viewModel().uiState.value

        assertEquals(HistoryPhase.Loaded, state.phase)
        assertTrue(state.isEmpty)
        assertFalse(state.isFilteredEmpty)
    }

    @Test
    fun `a network failure is a failure, never an empty history`() {
        repository.onList = { throw UnknownHostException("no route") }

        val state = viewModel().uiState.value

        val phase = state.phase
        assertTrue("expected Failed, was " + phase, phase is HistoryPhase.Failed)
        assertTrue("a transport failure must offer a retry", (phase as HistoryPhase.Failed).retryable)
        // The distinction this whole test exists for.
        assertFalse("a failure must not present as an empty history", state.isEmpty)
    }

    @Test
    fun `an expired session is reported and not retried as if it were transport`() {
        repository.onList = { throw httpException(401) }

        val phase = viewModel().uiState.value.phase

        assertTrue(phase is HistoryPhase.Failed)
        assertFalse(
            "a 401 is not fixed by trying again; TokenAuthenticator already refreshed",
            (phase as HistoryPhase.Failed).retryable,
        )
    }

    @Test
    fun `retry asks the server again`() {
        var attempt = 0
        repository.onList = {
            attempt += 1
            if (attempt == 1) throw UnknownHostException("no route") else listOf(conversation(7))
        }

        val vm = viewModel()
        assertTrue(vm.uiState.value.phase is HistoryPhase.Failed)

        vm.retry()

        assertEquals(HistoryPhase.Loaded, vm.uiState.value.phase)
        assertEquals(listOf(7), vm.uiState.value.items.map { it.id })
    }

    // ------------------------------------------------------------------ filters

    @Test
    fun `the default filter sends no kind parameter`() {
        repository.onList = { emptyList() }

        viewModel()

        // `null`, not an empty string: the server rejects an unrecognised kind with 422.
        assertNull(repository.listCalls.first().kind)
    }

    @Test
    fun `selecting a filter sends the server's own wire value`() {
        repository.onList = { emptyList() }
        val vm = viewModel()

        vm.onFilterSelected(ConversationFilter.MockInterview)

        assertEquals("mock_interview", repository.listCalls.last().kind)
        assertEquals(ConversationFilter.MockInterview, vm.uiState.value.filter)
    }

    @Test
    fun `every filter's wire value is one the server accepts`() {
        // The closed vocabulary on the server side is {career_coach, mock_interview}, plus absent.
        val allowed = setOf(null, "career_coach", "mock_interview")
        ConversationFilter.entries.forEach { filter ->
            assertTrue(
                "unknown wire value: " + filter.wire,
                filter.wire in allowed,
            )
        }
    }

    @Test
    fun `an empty filtered result is distinguished from an empty history`() {
        repository.onList = { call -> if (call.kind == null) listOf(conversation(1)) else emptyList() }
        val vm = viewModel()

        vm.onFilterSelected(ConversationFilter.MockInterview)
        val state = vm.uiState.value

        assertTrue(state.isFilteredEmpty)
        // Not "no conversations yet": they have one, just not of this kind.
        assertFalse(state.isEmpty)
    }

    @Test
    fun `reselecting the current filter does not refetch`() {
        repository.onList = { emptyList() }
        val vm = viewModel()
        val before = repository.listCalls.size

        vm.onFilterSelected(ConversationFilter.All)

        assertEquals(before, repository.listCalls.size)
    }

    // ------------------------------------------------------------------ pagination

    @Test
    fun `a full page offers more and the next page is requested at the right offset`() {
        val fullPage = (1..20).map { conversation(it) }
        repository.onList = { call -> if (call.offset == 0) fullPage else listOf(conversation(21)) }

        val vm = viewModel()
        assertTrue("a full page means there may be more", vm.uiState.value.canLoadMore)

        vm.loadMore()

        assertEquals(20, repository.listCalls.last().offset)
        assertEquals(21, vm.uiState.value.items.size)
        assertFalse("a short page ends the sequence", vm.uiState.value.canLoadMore)
    }

    @Test
    fun `a short first page does not offer more`() {
        repository.onList = { listOf(conversation(1)) }

        assertFalse(viewModel().uiState.value.canLoadMore)
    }

    @Test
    fun `a conversation repeated across pages is not shown twice`() {
        val fullPage = (1..20).map { conversation(it) }
        // Row 20 reappears on page two, which is what happens when activity reorders the list
        // between requests.
        repository.onList = { call ->
            if (call.offset == 0) fullPage else listOf(conversation(20), conversation(21))
        }

        val vm = viewModel()
        vm.loadMore()

        val ids = vm.uiState.value.items.map { it.id }
        assertEquals("duplicate rows: " + ids, ids.size, ids.toSet().size)
    }

    @Test
    fun `a failed next page keeps the rows already shown`() {
        val fullPage = (1..20).map { conversation(it) }
        repository.onList = { call ->
            if (call.offset == 0) fullPage else throw UnknownHostException("dropped")
        }

        val vm = viewModel()
        vm.loadMore()

        assertEquals("the first page must survive", 20, vm.uiState.value.items.size)
        assertEquals(HistoryPhase.Loaded, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.notice != null)
    }

    // ------------------------------------------------------------------ deletion

    @Test
    fun `a conversation disappears only after the server confirms the delete`() {
        repository.onList = { listOf(conversation(1), conversation(2)) }
        val vm = viewModel()

        vm.deleteConversation(1)

        assertEquals(listOf(1), repository.deletedIds)
        assertEquals(listOf(2), vm.uiState.value.items.map { it.id })
        assertNull(vm.uiState.value.deletingId)
    }

    @Test
    fun `a failed delete leaves the conversation in the list`() {
        repository.onList = { listOf(conversation(1), conversation(2)) }
        repository.onDelete = { throw UnknownHostException("dropped") }
        val vm = viewModel()

        vm.deleteConversation(1)

        // The row must stay. Removing it would tell the student their transcript is gone when it
        // is still on the server.
        assertEquals(listOf(1, 2), vm.uiState.value.items.map { it.id })
        assertNull(vm.uiState.value.deletingId)
        assertTrue(vm.uiState.value.notice != null)
    }

    @Test
    fun `deleting something already gone reports it without removing another row`() {
        repository.onList = { listOf(conversation(1), conversation(2)) }
        repository.onDelete = { throw httpException(404) }
        val vm = viewModel()

        vm.deleteConversation(1)

        assertEquals(listOf(1, 2), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `a second delete is ignored while the first is still in flight`() {
        repository.onList = { listOf(conversation(1), conversation(2)) }
        val held = CompletableDeferred<Unit>()
        // Holds the first delete open, which is the only way the guard is observable.
        repository.onDelete = { held.await() }
        val vm = viewModel()

        vm.deleteConversation(1)
        vm.deleteConversation(2)

        assertEquals(
            "the second tap must not reach the server",
            listOf(1),
            repository.deletedIds,
        )
        assertEquals(1, vm.uiState.value.deletingId)
        held.complete(Unit)
    }
    @Test
    fun `dismissing the notice clears it`() {
        repository.onList = { listOf(conversation(1)) }
        repository.onDelete = { throw UnknownHostException("dropped") }
        val vm = viewModel()
        vm.deleteConversation(1)
        assertTrue(vm.uiState.value.notice != null)

        vm.dismissNotice()

        assertNull(vm.uiState.value.notice)
    }
}
