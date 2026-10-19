package com.aiplacementos.app.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.ActionOutcomeDto
import com.aiplacementos.app.data.remote.ActionPreviewDto
import com.aiplacementos.app.data.remote.ActionState
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.domain.repository.ActionRepository
import com.aiplacementos.app.ui.common.isNetworkError
import com.aiplacementos.app.ui.common.isTimeoutError
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The reusable confirmation flow for a server-side action (VA-7).
 *
 * One object, shared by any screen that needs a confirmed write, because the sequence is the
 * same every time and a per-screen copy is how one of them ends up skipping a step. The screen
 * supplies the capability and its arguments; everything the student is shown comes from the
 * server's preview, so the sentence on the dialog is the sentence the server will act on.
 *
 * Three rules this class exists to hold.
 *
 * **Confirming is not succeeding.** [Executing] and [Finished] are separate states, and
 * [Finished] carries the server's own verdict. There is no path from "the student tapped
 * Confirm" to a success message; the only thing that produces one is an outcome whose state is
 * `succeeded` and whose `verified` is true.
 *
 * **A confirmation is spent once.** [confirm] does nothing unless the flow is sitting in
 * [AwaitingConfirmation], so a double tap, a recomposition or a second click on a slow network
 * cannot send the token twice. The token is cleared the moment it is used.
 *
 * **An unknown outcome is never retried for the student.** When the server cannot say whether
 * the change took effect, this stops and says so. The idempotency key is deliberately *kept* in
 * that case, so if the student chooses to try again the server recognises the same logical
 * operation and answers from authoritative state instead of mutating a second time.
 */
sealed interface ActionConfirmationUiState {
    data object Idle : ActionConfirmationUiState

    /** Asking the server what would happen. Nothing has been written. */
    data object Preparing : ActionConfirmationUiState

    data class AwaitingConfirmation(val preview: ActionPreviewDto) : ActionConfirmationUiState

    /** The confirmation has been spent and the mutation is in flight. The outcome is not known
     * yet, and nothing may be rendered as done. */
    data class Executing(val preview: ActionPreviewDto) : ActionConfirmationUiState

    /** The server's verdict. Read [ActionOutcomeDto.state], not the absence of an error. */
    data class Finished(val outcome: ActionOutcomeDto) : ActionConfirmationUiState

    /** The approval ran out before it was used. Nothing was written; previewing again is the
     * only way forward, which is also exactly what the server would enforce. */
    data class Expired(val description: String) : ActionConfirmationUiState

    data class Failed(val message: String, val isNetworkError: Boolean = false) : ActionConfirmationUiState
}

class ActionConfirmationViewModel(
    private val actionRepository: ActionRepository,
    private val keys: OperationKeyHolder = OperationKeyHolder(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _uiState = MutableStateFlow<ActionConfirmationUiState>(ActionConfirmationUiState.Idle)
    val uiState: StateFlow<ActionConfirmationUiState> = _uiState.asStateFlow()

    private var capability: String? = null
    private var arguments: Map<String, Int> = emptyMap()
    private var token: String? = null
    private var expiresAtMillis: Long = 0

    /**
     * Ask the server what this action would do, and show it.
     *
     * Writes nothing. Re-requesting while a confirmation is already pending replaces it, which
     * is correct: the arguments may have changed, and a stale grant would be refused anyway
     * because it is bound to the ones that were previewed.
     */
    fun request(capability: String, arguments: Map<String, Int>) {
        this.capability = capability
        this.arguments = arguments
        token = null
        viewModelScope.launch {
            _uiState.value = ActionConfirmationUiState.Preparing
            try {
                val preview = actionRepository.preview(capability, arguments)
                token = preview.confirmationToken
                expiresAtMillis = nowMillis() + (preview.expiresInSeconds * 1000).toLong()
                _uiState.value = ActionConfirmationUiState.AwaitingConfirmation(preview)
            } catch (e: Exception) {
                _uiState.value = ActionConfirmationUiState.Failed(
                    e.toUserMessage("check what that would change"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }

    /** Spend the confirmation. Ignored unless one is actually pending. */
    fun confirm() {
        val pending = _uiState.value as? ActionConfirmationUiState.AwaitingConfirmation ?: return
        val capability = this.capability ?: return
        val heldToken = token ?: return

        if (nowMillis() >= expiresAtMillis) {
            // Refused here as well as on the server. Sending a token we already know is dead
            // would cost a round trip and show the student a conflict instead of the plain
            // truth, which is that the approval ran out.
            token = null
            _uiState.value = ActionConfirmationUiState.Expired(pending.preview.description)
            return
        }

        // Cleared before the call, not after: this is what makes a double tap impossible rather
        // than unlikely, because the second tap finds no token even if it arrives mid-flight.
        token = null
        val identity = "$capability:${arguments.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }}"
        val key = keys.keyFor(identity)

        viewModelScope.launch {
            _uiState.value = ActionConfirmationUiState.Executing(pending.preview)
            try {
                val outcome = actionRepository.execute(capability, arguments, heldToken, key)
                if (ActionState.from(outcome.state) == ActionState.SUCCEEDED && outcome.verified) {
                    // Only a verified success retires the key. Anything else leaves it in place
                    // so a deliberate second attempt is recognised as the same operation.
                    keys.consume(identity)
                }
                _uiState.value = ActionConfirmationUiState.Finished(outcome)
            } catch (e: Exception) {
                _uiState.value = ActionConfirmationUiState.Failed(
                    e.toUserMessage("complete that"),
                    isNetworkError = e.isNetworkError() && !e.isTimeoutError(),
                )
            }
        }
    }

    /** The student backed out. Nothing was sent, and the grant is left to expire on its own. */
    fun cancel() {
        token = null
        capability = null
        arguments = emptyMap()
        _uiState.value = ActionConfirmationUiState.Idle
    }

    /** Dismiss a finished, expired or failed flow without re-running anything. */
    fun dismiss() = cancel()
}

/**
 * How a finished outcome should read to the student, derived from the server's state rather
 * than from the fact that a call returned.
 *
 * `UNRECOGNISED` -- a state from a newer backend this build does not know -- deliberately falls
 * in with the cannot-confirm cases. Guessing success for an unknown state is the one reading
 * that could tell someone their job was saved when it was not.
 */
fun ActionOutcomeDto.userMessage(): String = when (ActionState.from(state)) {
    ActionState.SUCCEEDED ->
        if (noChangeNeeded) "That was already done." else "Done."
    ActionState.IN_PROGRESS -> "That request is still running. Check again in a moment."
    ActionState.VERIFICATION_FAILED -> "We couldn't confirm that went through. Please check before trying again."
    ActionState.UNKNOWN -> "We couldn't confirm that went through. Please check before trying again."
    ActionState.FAILED, ActionState.REJECTED -> detail ?: "That couldn't be completed."
    ActionState.CONFIRMATION_REQUIRED -> "That still needs to be confirmed."
    ActionState.UNRECOGNISED -> "We couldn't confirm that went through. Please check before trying again."
}

/** True only for a verified success. The gate every "it worked" affordance should sit behind. */
fun ActionOutcomeDto.isConfirmedSuccess(): Boolean =
    ActionState.from(state) == ActionState.SUCCEEDED && verified
