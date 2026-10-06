package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.data.remote.InterviewDto
import com.aiplacementos.app.data.remote.InterviewEvaluationDto
import com.aiplacementos.app.data.remote.InterviewHistoryItemDto
import com.aiplacementos.app.data.remote.NextInterviewQuestionResponseDto
import java.io.File

interface InterviewRepository {
    suspend fun listInterviews(): List<InterviewHistoryItemDto>
    suspend fun startInterview(
        type: String,
        mode: String,
        jdId: Int?,
        idempotencyKey: String? = null,
    ): InterviewDto
    suspend fun nextQuestion(interviewId: Int): NextInterviewQuestionResponseDto
    suspend fun submitAnswer(interviewId: Int, questionId: Int, answerText: String): InterviewAnswerDto
    suspend fun getQuestionAudio(interviewId: Int, questionId: Int): ByteArray
    suspend fun submitAudioAnswer(interviewId: Int, questionId: Int, audioFile: File): InterviewAnswerDto
    suspend fun getEvaluation(interviewId: Int): InterviewEvaluationDto
}
