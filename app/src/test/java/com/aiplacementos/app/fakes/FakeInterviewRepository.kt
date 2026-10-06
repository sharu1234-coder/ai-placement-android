package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.data.remote.InterviewDto
import com.aiplacementos.app.data.remote.InterviewEvaluationDto
import com.aiplacementos.app.data.remote.InterviewHistoryItemDto
import com.aiplacementos.app.data.remote.NextInterviewQuestionResponseDto
import com.aiplacementos.app.domain.repository.InterviewRepository
import java.io.File

class FakeInterviewRepository : InterviewRepository {
    /** Every Idempotency-Key this fake was handed, in order. A test asserting these are
     * equal across retries is what proves the caller holds one key per logical operation,
     * and a test asserting they differ is what proves a deliberate new action gets a new one. */
    val keys = mutableListOf<String?>()

    var listResult: Result<List<InterviewHistoryItemDto>> = Result.success(emptyList())
    var startResult: (suspend (String, String, Int?) -> InterviewDto)? = null
    var nextQuestionResult: (suspend (Int) -> NextInterviewQuestionResponseDto)? = null
    var submitAnswerResult: (suspend (Int, Int, String) -> InterviewAnswerDto)? = null
    var evaluationResult: (suspend (Int) -> InterviewEvaluationDto)? = null

    var lastStartArgs: Triple<String, String, Int?>? = null

    override suspend fun listInterviews(): List<InterviewHistoryItemDto> = listResult.getOrThrow()

    override suspend fun startInterview(
        type: String,
        mode: String,
        jdId: Int?,
        idempotencyKey: String?,
    ): InterviewDto {
        keys += idempotencyKey
        lastStartArgs = Triple(type, mode, jdId)
        val fn = requireNotNull(startResult) { "startResult not configured" }
        return fn(type, mode, jdId)
    }

    override suspend fun nextQuestion(interviewId: Int): NextInterviewQuestionResponseDto {
        val fn = requireNotNull(nextQuestionResult) { "nextQuestionResult not configured" }
        return fn(interviewId)
    }

    override suspend fun submitAnswer(interviewId: Int, questionId: Int, answerText: String): InterviewAnswerDto {
        val fn = requireNotNull(submitAnswerResult) { "submitAnswerResult not configured" }
        return fn(interviewId, questionId, answerText)
    }

    override suspend fun getQuestionAudio(interviewId: Int, questionId: Int): ByteArray =
        error("not used in these tests")

    override suspend fun submitAudioAnswer(interviewId: Int, questionId: Int, audioFile: File): InterviewAnswerDto =
        error("not used in these tests")

    override suspend fun getEvaluation(interviewId: Int): InterviewEvaluationDto {
        val fn = requireNotNull(evaluationResult) { "evaluationResult not configured" }
        return fn(interviewId)
    }
}
