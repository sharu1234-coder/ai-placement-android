package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.data.remote.InterviewDto
import com.aiplacementos.app.data.remote.InterviewEvaluationDto
import com.aiplacementos.app.data.remote.NextInterviewQuestionResponseDto

interface InterviewRepository {
    suspend fun startInterview(type: String, jdId: Int?): InterviewDto
    suspend fun nextQuestion(interviewId: Int): NextInterviewQuestionResponseDto
    suspend fun submitAnswer(interviewId: Int, questionId: Int, answerText: String): InterviewAnswerDto
    suspend fun getEvaluation(interviewId: Int): InterviewEvaluationDto
}
