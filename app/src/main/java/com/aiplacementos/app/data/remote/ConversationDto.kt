package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The unified voice conversation history, as the server actually sends it.
 *
 * Mirrors `app/schemas/conversation.py`. Two things about these types are deliberate:
 *
 * **There is no turn count on a list row.** The server does not send one, and the list endpoint
 * does not return turns, so a count shown next to a conversation would have to be invented. The
 * number of turns is knowable only from the detail response, and that is the only place it is
 * displayed.
 *
 * **Timestamps are [String], not a date type.** That is the convention across this package, and
 * here it also matters for a second reason: the server's columns are `DateTime` without a
 * timezone, so it serialises naive UTC with **no offset** -- `2026-10-17T21:30:00`, not
 * `...Z`. `OffsetDateTime.parse` throws on that, which is why the UI parses through
 * `conversationTimestamp()` rather than calling the parser directly.
 */
@Serializable
data class ConversationDto(
    val id: Int,
    /** `career_coach` or `mock_interview`. */
    val kind: String,
    /** `active`, `completed` or `interrupted`. */
    val status: String,
    /** The interview mode, when this was an interview. Absent for coaching. */
    val mode: String? = null,
    val title: String? = null,
    @SerialName("interview_id") val interviewId: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
)

@Serializable
data class ConversationTurnDto(
    /** Position within the conversation, starting at 1. The server's ordering is authoritative. */
    val seq: Int,
    /** `user` or `assistant`. */
    val role: String,
    val text: String,
    @SerialName("interview_question_id") val interviewQuestionId: Int? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ConversationDetailDto(
    val id: Int,
    val kind: String,
    val status: String,
    val mode: String? = null,
    val title: String? = null,
    @SerialName("interview_id") val interviewId: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    val turns: List<ConversationTurnDto>,
)
