package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.data.remote.InterviewDto
import com.aiplacementos.app.data.remote.InterviewEvaluationDto
import com.aiplacementos.app.data.remote.InterviewsApi
import com.aiplacementos.app.data.remote.NextInterviewQuestionResponseDto
import com.aiplacementos.app.data.remote.StartInterviewRequestDto
import com.aiplacementos.app.data.remote.SubmitInterviewAnswerRequestDto
import com.aiplacementos.app.domain.repository.InterviewRepository

class InterviewRepositoryImpl(private val interviewsApi: InterviewsApi) : InterviewRepository {
    override suspend fun startInterview(type: String, jdId: Int?): InterviewDto =
        interviewsApi.startInterview(StartInterviewRequestDto(type = type, jdId = jdId))

    override suspend fun nextQuestion(interviewId: Int): NextInterviewQuestionResponseDto =
        interviewsApi.nextQuestion(interviewId)

    override suspend fun submitAnswer(interviewId: Int, questionId: Int, answerText: String): InterviewAnswerDto =
        interviewsApi.submitAnswer(
            interviewId,
            SubmitInterviewAnswerRequestDto(interviewQuestionId = questionId, answerText = answerText),
        )

    override suspend fun getEvaluation(interviewId: Int): InterviewEvaluationDto =
        interviewsApi.getEvaluation(interviewId)
}
