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
)
