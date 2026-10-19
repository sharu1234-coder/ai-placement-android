package com.aiplacementos.app.ui.actions

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ActionCapability
import com.aiplacementos.app.data.remote.ActionOutcomeDto
import com.aiplacementos.app.data.remote.ActionPreviewDto
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.fakes.FakeActionRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.UnknownHostException

private fun preview(
    capability: String = ActionCapability.SAVE_JOB,
    description: String = "Save Backend Engineer at Acme to your tracker.",
    token: String = "cid.secret",
    expiresInSeconds: Double = 120.0,
    alreadySatisfied: Boolean = false,
) = ActionPreviewDto(
    state = "confirmation_required",
    capability = capability,
    confirmationId = "cid",
    confirmationToken = token,
    description = description,
    resource = "Backend Engineer at Acme",
    riskClass = "low",
    expiresInSeconds = expiresInSeconds,
    alreadySatisfied = alreadySatisfied,
)

private fun outcome(
    state: String = "succeeded",
    verified: Boolean = true,
    noChangeNeeded: Boolean = false,
    replayed: Boolean = false,
    detail: String? = null,
) = ActionOutcomeDto(
    state = state,
    capability = ActionCapability.SAVE_JOB,
    verified = verified,
    description = "Save Backend Engineer at Acme to your tracker.",
    resource = "Backend Engineer at Acme",
    detail = detail,
    noChangeNeeded = noChangeNeeded,
    replayed = replayed,
)

private val ARGS = mapOf("listing_id" to 41)

class ActionConfirmationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeActionRepository()
    private var now = 1_000_000L

    private fun viewModel(keys: OperationKeyHolder = OperationKeyHolder()) =
        ActionConfirmationViewModel(repository, keys = keys, nowMillis = { now })

    // ------------------------------------------------------------------ previewing

    @Test
    fun `requesting shows the servers own description and writes nothing`() {
        repository.previewResult = { _, _ -> preview() }
        val vm = viewModel()

        vm.request(ActionCapability.SAVE_JOB, ARGS)

        val state = vm.uiState.value
        assertTrue(state is ActionConfirmationUiState.AwaitingConfirmation)
        assertEquals(
            "Save Backend Engineer at Acme to your tracker.",
            (state as ActionConfirmationUiState.AwaitingConfirmation).preview.description,
        )
        assertTrue("previewing must not execute", repository.executes.isEmpty())
    }

    @Test
    fun `a failed preview is an error, not a pending confirmation`() {
        repository.previewResult = { _, _ -> throw httpException(404) }
        val vm = viewModel()

        vm.request(ActionCapability.SAVE_JOB, ARGS)

        assertTrue(vm.uiState.value is ActionConfirmationUiState.Failed)
        assertTrue(repository.executes.isEmpty())
    }

    @Test
    fun `a network failure is reported as one so the retry affordance is right`() {
        repository.previewResult = { _, _ -> throw UnknownHostException("offline") }
        val vm = viewModel()

        vm.request(ActionCapability.SAVE_JOB, ARGS)

        val state = vm.uiState.value as ActionConfirmationUiState.Failed
        assertTrue(state.isNetworkError)
    }

    // ------------------------------------------------------------------ confirming once

    @Test
    fun `confirming sends the token exactly once`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome() }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        vm.confirm()

        assertEquals(1, repository.executes.size)
        assertEquals("cid.secret", repository.executes.single().confirmationToken)
    }

    @Test
    fun `a double tap cannot send the confirmation twice`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome() }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        vm.confirm()
        vm.confirm()
        vm.confirm()

        assertEquals("one confirmation, one mutation", 1, repository.executes.size)
    }

    @Test
    fun `confirming without a pending preview does nothing`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome() }
        val vm = viewModel()

        vm.confirm()

        assertTrue(repository.executes.isEmpty())
        assertTrue(vm.uiState.value is ActionConfirmationUiState.Idle)
    }

    @Test
    fun `cancelling before confirming sends nothing`() {
        repository.previewResult = { _, _ -> preview() }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        vm.cancel()
        vm.confirm()

        assertTrue(repository.executes.isEmpty())
        assertTrue(vm.uiState.value is ActionConfirmationUiState.Idle)
    }

    @Test
    fun `an expired confirmation is refused without a round trip`() {
        repository.previewResult = { _, _ -> preview(expiresInSeconds = 10.0) }
        repository.executeResult = { outcome() }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        now += 11_000

        vm.confirm()

        assertTrue(vm.uiState.value is ActionConfirmationUiState.Expired)
        assertTrue("a token we know is dead must not be sent", repository.executes.isEmpty())
    }

    // ------------------------------------------------------------------ outcomes

    @Test
    fun `confirming is not succeeding`() {
        // The state carries the server's verdict, and the only thing that reads as success is
        // a verified one. A UI that treated "the student tapped Confirm" as the answer would
        // tell them their job was saved before anybody checked.
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome(state = "verification_failed", verified = false) }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        vm.confirm()

        val finished = vm.uiState.value as ActionConfirmationUiState.Finished
        assertFalse(finished.outcome.isConfirmedSuccess())
        assertTrue(finished.outcome.userMessage().contains("couldn't confirm"))
    }

    @Test
    fun `an unverified succeeded state is still not a success`() {
        // Unreachable from this server, which refuses to build such an outcome. Asserted anyway
        // because the client must not be the place where that invariant is assumed.
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome(state = "succeeded", verified = false) }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        vm.confirm()

        val finished = vm.uiState.value as ActionConfirmationUiState.Finished
        assertFalse(finished.outcome.isConfirmedSuccess())
    }

    @Test
    fun `an unrecognised state from a newer backend reads as unconfirmed`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome(state = "quantum_superposition", verified = false) }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        vm.confirm()

        val finished = vm.uiState.value as ActionConfirmationUiState.Finished
        assertFalse(finished.outcome.isConfirmedSuccess())
        assertTrue(finished.outcome.userMessage().contains("couldn't confirm"))
    }

    @Test
    fun `an already satisfied request says so rather than claiming a change`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome(noChangeNeeded = true) }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        vm.confirm()

        val finished = vm.uiState.value as ActionConfirmationUiState.Finished
        assertTrue(finished.outcome.isConfirmedSuccess())
        assertEquals("That was already done.", finished.outcome.userMessage())
    }

    @Test
    fun `an in progress outcome asks the student to look again rather than retrying`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome(state = "in_progress", verified = false) }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        vm.confirm()

        val finished = vm.uiState.value as ActionConfirmationUiState.Finished
        assertTrue(finished.outcome.userMessage().contains("still running"))
        assertEquals("nothing may be retried on the student's behalf", 1, repository.executes.size)
    }

    @Test
    fun `an unknown outcome is never retried automatically`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome(state = "unknown", verified = false) }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        vm.confirm()

        assertEquals(1, repository.executes.size)
        val finished = vm.uiState.value as ActionConfirmationUiState.Finished
        assertTrue(finished.outcome.userMessage().contains("check before trying again"))
    }

    // ------------------------------------------------------------------ idempotency keys

    @Test
    fun `a deliberate retry after an unknown outcome reuses the same key`() {
        // This is what lets the server recognise the second attempt as the same logical
        // operation and answer from authoritative state instead of mutating again.
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome(state = "unknown", verified = false) }
        val keys = OperationKeyHolder()
        val vm = viewModel(keys)

        vm.request(ActionCapability.SAVE_JOB, ARGS)
        vm.confirm()
        vm.request(ActionCapability.SAVE_JOB, ARGS)
        vm.confirm()

        assertEquals(2, repository.executes.size)
        assertEquals(repository.executes[0].idempotencyKey, repository.executes[1].idempotencyKey)
    }

    @Test
    fun `a new deliberate action after a verified success gets a new key`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome() }
        val keys = OperationKeyHolder()
        val vm = viewModel(keys)

        vm.request(ActionCapability.SAVE_JOB, ARGS)
        vm.confirm()
        vm.request(ActionCapability.SAVE_JOB, ARGS)
        vm.confirm()

        assertEquals(2, repository.executes.size)
        assertNotEquals(repository.executes[0].idempotencyKey, repository.executes[1].idempotencyKey)
    }

    @Test
    fun `a different job is a different operation and gets its own key`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome(state = "unknown", verified = false) }
        val keys = OperationKeyHolder()
        val vm = viewModel(keys)

        vm.request(ActionCapability.SAVE_JOB, mapOf("listing_id" to 41))
        vm.confirm()
        vm.request(ActionCapability.SAVE_JOB, mapOf("listing_id" to 99))
        vm.confirm()

        assertNotEquals(repository.executes[0].idempotencyKey, repository.executes[1].idempotencyKey)
    }

    @Test
    fun `save and unsave of the same job are different operations`() {
        repository.previewResult = { capability, _ -> preview(capability = capability) }
        repository.executeResult = { outcome(state = "unknown", verified = false) }
        val keys = OperationKeyHolder()
        val vm = viewModel(keys)

        vm.request(ActionCapability.SAVE_JOB, ARGS)
        vm.confirm()
        vm.request(ActionCapability.UNSAVE_JOB, ARGS)
        vm.confirm()

        assertEquals(ActionCapability.SAVE_JOB, repository.executes[0].capability)
        assertEquals(ActionCapability.UNSAVE_JOB, repository.executes[1].capability)
        assertNotEquals(repository.executes[0].idempotencyKey, repository.executes[1].idempotencyKey)
    }

    @Test
    fun `the arguments sent are exactly the ones that were previewed`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { outcome() }
        val vm = viewModel()

        vm.request(ActionCapability.SAVE_JOB, ARGS)
        vm.confirm()

        assertEquals(ARGS, repository.executes.single().arguments)
        assertEquals(ARGS, repository.previews.single().second)
    }

    @Test
    fun `re-requesting replaces a pending confirmation rather than keeping both`() {
        var issued = 0
        repository.previewResult = { _, _ -> preview(token = "cid.secret${issued++}") }
        repository.executeResult = { outcome() }
        val vm = viewModel()

        vm.request(ActionCapability.SAVE_JOB, mapOf("listing_id" to 41))
        vm.request(ActionCapability.SAVE_JOB, mapOf("listing_id" to 99))
        vm.confirm()

        // The second preview's token, for the second preview's arguments. Sending the first
        // one's token with the second one's arguments is exactly what the server refuses.
        assertEquals("cid.secret1", repository.executes.single().confirmationToken)
        assertEquals(mapOf("listing_id" to 99), repository.executes.single().arguments)
    }

    @Test
    fun `an execute failure does not leave the flow looking successful`() {
        repository.previewResult = { _, _ -> preview() }
        repository.executeResult = { throw httpException(409) }
        val vm = viewModel()
        vm.request(ActionCapability.SAVE_JOB, ARGS)

        vm.confirm()

        assertTrue(vm.uiState.value is ActionConfirmationUiState.Failed)
        // And a second tap cannot resend: the token was spent on the attempt.
        vm.confirm()
        assertEquals(1, repository.executes.size)
    }
}
