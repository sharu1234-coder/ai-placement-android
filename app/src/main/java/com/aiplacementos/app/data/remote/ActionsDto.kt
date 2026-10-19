package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The safe-action surface (VA-7): preview, confirm, execute, verify.
 *
 * Two things about this contract are worth stating because they change how the UI must behave.
 *
 * The confirmation token is minted by the server, bound to the exact capability, arguments and
 * resource the preview resolved, single-use, and short-lived. The client's job is to hold it
 * only as long as the dialog is open and hand it back once. It is not a flag the client decides;
 * sending `confirmed = true` would be worth nothing, which is why there is no such field.
 *
 * [ActionOutcomeDto.state] is the authority on what happened, and [ActionOutcomeDto.verified] is
 * the authority on whether anybody checked. A 200 means the request was handled, not that the
 * change took effect -- `verification_failed` and `unknown` both arrive as 200. The UI must read
 * the state rather than the status code, which is the whole reason the states exist.
 */
@Serializable
data class ActionRequestDto(
    val capability: String,
    val arguments: Map<String, Int> = emptyMap(),
)

@Serializable
data class ActionExecuteRequestDto(
    val capability: String,
    val arguments: Map<String, Int> = emptyMap(),
    @SerialName("confirmation_token") val confirmationToken: String,
)

@Serializable
data class ActionPreviewDto(
    val state: String,
    val capability: String,
    @SerialName("confirmation_id") val confirmationId: String,
    @SerialName("confirmation_token") val confirmationToken: String,
    /** The sentence the student must be shown. Server-authored on purpose: the screen and the
     * server act on the same words, so they cannot disagree about what is about to happen. */
    val description: String,
    val resource: String,
    @SerialName("risk_class") val riskClass: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Double,
    /** True when live state already satisfies the request. Still confirmed, because the student
     * asked and "it is already done" is a better answer than silence. */
    @SerialName("already_satisfied") val alreadySatisfied: Boolean = false,
    @SerialName("requires_idempotency_key") val requiresIdempotencyKey: Boolean = true,
)

@Serializable
data class ActionOutcomeDto(
    val state: String,
    val capability: String,
    val verified: Boolean,
    val description: String,
    val resource: String,
    val detail: String? = null,
    @SerialName("no_change_needed") val noChangeNeeded: Boolean = false,
    /** True when the server recognised a duplicate submission and the first one's effect stands. */
    val replayed: Boolean = false,
)

/**
 * The server's action states, as a closed set the UI can branch on.
 *
 * Parsed rather than matched inline so an unrecognised value from a newer backend lands in
 * [UNRECOGNISED] and is rendered as "we could not confirm this" -- the safe reading -- instead
 * of falling through an `if` chain into the success branch.
 */
enum class ActionState(val wire: String) {
    CONFIRMATION_REQUIRED("confirmation_required"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    REJECTED("rejected"),
    IN_PROGRESS("in_progress"),
    UNKNOWN("unknown"),
    VERIFICATION_FAILED("verification_failed"),
    UNRECOGNISED("");

    companion object {
        fun from(wire: String?): ActionState =
            entries.firstOrNull { it.wire == wire && it != UNRECOGNISED } ?: UNRECOGNISED
    }
}

/** The capability names VA-7 registered. Constants rather than literals at call sites: the name
 * is part of the server contract and a typo would surface as a 404 at the worst moment. */
object ActionCapability {
    const val SAVE_JOB = "jobs.save"
    const val UNSAVE_JOB = "jobs.unsave"
}
