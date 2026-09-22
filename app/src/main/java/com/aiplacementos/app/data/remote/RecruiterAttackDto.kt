package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartRecruiterAttackSessionRequestDto(
    @SerialName("source_type") val sourceType: String,
    @SerialName("project_build_id") val projectBuildId: Int? = null,
    @SerialName("claim_text") val claimText: String? = null,
)

@Serializable
data class RecruiterAttackSessionDto(
    val id: Int,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_label") val sourceLabel: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String?,
)

@Serializable
data class RecruiterAttackSessionListItemDto(
    val id: Int,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_label") val sourceLabel: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String?,
    @SerialName("overall_score") val overallScore: Double?,
)

@Serializable
data class RecruiterAttackTurnDto(
    val id: Int,
    @SerialName("order_index") val orderIndex: Int,
    @SerialName("question_text") val questionText: String,
    @SerialName("based_on") val basedOn: String,
    @SerialName("answer_text") val answerText: String?,
)

@Serializable
data class NextRecruiterAttackTurnResponseDto(
    val completed: Boolean,
    val turn: RecruiterAttackTurnDto?,
)

@Serializable
data class SubmitRecruiterAttackAnswerRequestDto(
    @SerialName("turn_id") val turnId: Int,
    @SerialName("answer_text") val answerText: String,
)

@Serializable
data class RecruiterAttackResultsDto(
    // Every score is nullable because an ungraded session genuinely has none. When the AI
    // evaluation fails the backend persists the feedback text and leaves all five score
    // columns NULL (see recruiter_attack_service.get_results) rather than inventing a
    // placeholder, so `null` on the wire is the honest, expected value -- not a bug to be
    // defaulted away. Declaring these non-null made kotlinx throw on exactly that body, which
    // meant the "couldn't grade this" UI below could never be reached.
    //
    // Do NOT give these a numeric default. 0.0 would turn "we never graded you" into "you
    // scored zero", which is the specific lie this whole null-carrying contract exists to
    // prevent.
    @SerialName("technical_defense") val technicalDefense: Double? = null,
    @SerialName("project_ownership") val projectOwnership: Double? = null,
    val specificity: Double? = null,
    val communication: Double? = null,
    @SerialName("overall_score") val overallScore: Double? = null,
    val feedback: String,
    /**
     * True when the AI grader failed, so this session was never given a numeric evaluation at
     * all. Every score field above is null in that case and the UI must show an honest
     * "couldn't grade this" state (see RecruiterAttackResultsScreen).
     *
     * The backend derives this from the scores themselves being unset rather than storing it
     * separately, so the two can never disagree: used_fallback true always means all five
     * scores are null. It is NOT a "we graded you badly" signal -- the student's answers were
     * saved and only the grading failed, which is why re-running the session is the remedy.
     *
     * Defaults to false so a response from a backend that predates this field deserializes to
     * "this was really graded", which is the correct reading of an older successful response.
     * The JSON key is fixed by the backend contract -- do not rename it.
     */
    @SerialName("used_fallback") val usedFallback: Boolean = false,
)
