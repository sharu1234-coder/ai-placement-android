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
    /**
     * Which part of a Full Recruiter Simulation this question belongs to (INTRO, BACKGROUND,
     * RESUME, PROJECT, TECHNICAL, BEHAVIORAL, CANDIDATE_Q, or CLOSING once every question is
     * answered). Null for the ordinary interview types, which have no stages.
     *
     * Never render this value -- it is an internal vocabulary. Show [stageLabel]. The backend
     * derives both from the question's position and owns the whole plan; this app must not
     * compute stage boundaries, infer how many questions remain, or decide completion.
     */
    val stage: String? = null,
    /** What to actually show the student, e.g. "Your projects". Null when [stage] is. */
    @SerialName("stage_label") val stageLabel: String? = null,
    @SerialName("question_number") val questionNumber: Int? = null,
    /**
     * How many questions this interview asks in total -- 5 for the ordinary types, 12 for a
     * full simulation. Sent by the backend for every type so this app no longer hard-codes a
     * copy of a server constant; nullable so a response from a backend predating the field
     * still parses, in which case the caller falls back to the historical 5.
     */
    @SerialName("total_questions") val totalQuestions: Int? = null,
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
data class InterviewHistoryItemDto(
    val id: Int,
    val type: String,
    val mode: String,
    val status: String,
    @SerialName("overall_score") val overallScore: Double?,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class InterviewEvaluationDto(
    // Every score is nullable because an interview whose automatic review failed is
    // deliberately left ungraded rather than given a placeholder. `null` here is the honest,
    // expected value for that case -- never a bug to be defaulted away, and never a zero.
    //
    // Do NOT give these a numeric default and do NOT enable coerceInputValues: either would
    // turn "we could not review this" into "you scored nothing", which is exactly the lie the
    // backend's nullability exists to prevent. See [isGraded].
    @SerialName("technical_correctness") val technicalCorrectness: Double? = null,
    val relevance: Double? = null,
    val completeness: Double? = null,
    val reasoning: Double? = null,
    val structure: Double? = null,
    val clarity: Double? = null,
    val grammar: Double? = null,
    @SerialName("speaking_pace") val speakingPace: Double? = null,
    @SerialName("filler_word_rate") val fillerWordRate: Double? = null,
    @SerialName("role_jd_coverage") val roleJdCoverage: Double? = null,
    @SerialName("overall_score") val overallScore: Double? = null,
    val feedback: String,
    /**
     * False when the automatic review could not be completed, so this interview has no score at
     * all. The backend derives it from the scores being unset rather than storing it separately,
     * so the two can never disagree.
     *
     * This is NOT "scored badly" -- an ungraded interview says nothing about the student, and
     * the UI must not let the two read alike. Distinct from the simulation's deterministic
     * *question* fallback, which is a different mechanism entirely and never affects grading.
     *
     * Defaults to true so a response from a backend predating this field reads as graded, which
     * is the correct reading of every evaluation that existed before it.
     */
    @SerialName("is_graded") val isGraded: Boolean = true,
)
