package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PrepareMeRequestDto(
    @SerialName("role_id") val roleId: Int? = null,
    @SerialName("jd_id") val jdId: Int? = null,
)

@Serializable
data class BlueprintSectionDto(
    val skill: String,
    @SerialName("assessment_type") val assessmentType: String,
    val weight: Double,
    @SerialName("question_count") val questionCount: Int,
    @SerialName("is_gap") val isGap: Boolean,
)

@Serializable
data class AssessmentBlueprintDto(
    val id: Int,
    @SerialName("role_id") val roleId: Int?,
    @SerialName("jd_id") val jdId: Int?,
    val source: String,
    val sections: List<BlueprintSectionDto>,
)

@Serializable
data class StartAssessmentRequestDto(
    @SerialName("blueprint_id") val blueprintId: Int,
)

@Serializable
data class AssessmentDto(
    val id: Int,
    @SerialName("blueprint_id") val blueprintId: Int,
    val status: String,
)

@Serializable
data class QuestionDto(
    val id: Int,
    @SerialName("skill_tag") val skillTag: String,
    val type: String,
    val difficulty: String,
    val prompt: String,
    val options: List<String>?,
)

@Serializable
data class NextQuestionResponseDto(
    val completed: Boolean,
    val question: QuestionDto?,
)

@Serializable
data class SubmitAnswerRequestDto(
    @SerialName("question_id") val questionId: Int,
    @SerialName("student_answer") val studentAnswer: String,
    @SerialName("time_taken_seconds") val timeTakenSeconds: Int? = null,
)

@Serializable
data class AnswerResultDto(
    @SerialName("question_id") val questionId: Int,
    @SerialName("is_correct") val isCorrect: Boolean,
    val score: Double,
    val explanation: String,
)

@Serializable
data class SkillScoreDto(
    @SerialName("skill_tag") val skillTag: String,
    val score: Double,
)

@Serializable
data class AssessmentResultDto(
    @SerialName("assessment_id") val assessmentId: Int,
    @SerialName("overall_score") val overallScore: Double,
    @SerialName("skill_scores") val skillScores: List<SkillScoreDto>,
    @SerialName("priority_weaknesses") val priorityWeaknesses: List<String>,
)
