package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlarmEligibilityDto(
    val eligible: Boolean,
)

@Serializable
data class StartChallengeRequestDto(
    @SerialName("client_alarm_id") val clientAlarmId: String,
)

@Serializable
data class ChallengeQuestionDto(
    val id: Int,
    val index: Int,
    @SerialName("skill_tag") val skillTag: String,
    val prompt: String,
    val options: List<String>,
)

@Serializable
data class ChallengeStateDto(
    @SerialName("challenge_id") val challengeId: Int,
    val status: String,
    @SerialName("completed_count") val completedCount: Int,
    @SerialName("current_question") val currentQuestion: ChallengeQuestionDto?,
)

@Serializable
data class SubmitChallengeAnswerRequestDto(
    @SerialName("question_id") val questionId: Int,
    @SerialName("student_answer") val studentAnswer: String,
)

@Serializable
data class ChallengeAnswerResultDto(
    @SerialName("question_id") val questionId: Int,
    @SerialName("is_correct") val isCorrect: Boolean,
    @SerialName("correct_answer") val correctAnswer: String?,
    val explanation: String?,
    @SerialName("completed_count") val completedCount: Int,
    @SerialName("challenge_completed") val challengeCompleted: Boolean,
)
