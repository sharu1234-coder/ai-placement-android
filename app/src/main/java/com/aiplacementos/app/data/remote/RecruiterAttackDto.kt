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
    @SerialName("technical_defense") val technicalDefense: Double,
    @SerialName("project_ownership") val projectOwnership: Double,
    val specificity: Double,
    val communication: Double,
    @SerialName("overall_score") val overallScore: Double,
    val feedback: String,
    /**
     * True when the AI grader failed and the backend fell back to a completion ratio -- a
     * measure of *how many questions were answered*, not any judgment of how well they were
     * defended. Every score field above is meaningless in that case, so the UI must show an
     * honest "couldn't grade this" state rather than rendering those numbers as a real
     * assessment (see RecruiterAttackResultsScreen).
     *
     * Defaults to false so a response from a backend that predates this field deserializes to
     * "this was really graded", which is the correct reading of an older successful response.
     * The JSON key is fixed by the backend contract -- do not rename it.
     */
    @SerialName("used_fallback") val usedFallback: Boolean = false,
)
