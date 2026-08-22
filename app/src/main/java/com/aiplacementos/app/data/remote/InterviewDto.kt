package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartInterviewRequestDto(
    val type: String = "technical",
    val mode: String = "text",
    @SerialName("jd_id") val jdId: Int? = null,
)

@Serializable
data class InterviewDto(
    val id: Int,
    val type: String,
    val mode: String,
    @SerialName("jd_id") val jdId: Int?,
    val status: String,
)

@Serializable
data class InterviewQuestionDto(
    val id: Int,
    @SerialName("order_index") val orderIndex: Int,
    @SerialName("question_text") val questionText: String,
    @SerialName("based_on") val basedOn: String,
)

@Serializable
data class NextInterviewQuestionResponseDto(
    val completed: Boolean,
    val question: InterviewQuestionDto?,
)

@Serializable
data class SubmitInterviewAnswerRequestDto(
    @SerialName("interview_question_id") val interviewQuestionId: Int,
    @SerialName("answer_text") val answerText: String,
)

@Serializable
data class InterviewAnswerDto(
    @SerialName("interview_question_id") val interviewQuestionId: Int,
    @SerialName("answer_text") val answerText: String,
)

@Serializable
data class InterviewEvaluationDto(
    @SerialName("technical_correctness") val technicalCorrectness: Double,
    val relevance: Double,
    val completeness: Double,
    val reasoning: Double,
    val structure: Double,
    val clarity: Double,
    val grammar: Double?,
    @SerialName("speaking_pace") val speakingPace: Double?,
    @SerialName("filler_word_rate") val fillerWordRate: Double?,
    @SerialName("role_jd_coverage") val roleJdCoverage: Double,
    @SerialName("overall_score") val overallScore: Double,
    val feedback: String,
)
