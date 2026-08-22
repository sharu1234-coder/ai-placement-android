package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.data.remote.InterviewDto
import com.aiplacementos.app.data.remote.InterviewEvaluationDto
import com.aiplacementos.app.data.remote.InterviewsApi
import com.aiplacementos.app.data.remote.NextInterviewQuestionResponseDto
import com.aiplacementos.app.data.remote.StartInterviewRequestDto
import com.aiplacementos.app.data.remote.SubmitInterviewAnswerRequestDto
import com.aiplacementos.app.domain.repository.InterviewRepository
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class InterviewRepositoryImpl(private val interviewsApi: InterviewsApi) : InterviewRepository {
    override suspend fun startInterview(type: String, mode: String, jdId: Int?): InterviewDto =
        interviewsApi.startInterview(StartInterviewRequestDto(type = type, mode = mode, jdId = jdId))

    override suspend fun nextQuestion(interviewId: Int): NextInterviewQuestionResponseDto =
        interviewsApi.nextQuestion(interviewId)

    override suspend fun submitAnswer(interviewId: Int, questionId: Int, answerText: String): InterviewAnswerDto =
        interviewsApi.submitAnswer(
            interviewId,
            SubmitInterviewAnswerRequestDto(interviewQuestionId = questionId, answerText = answerText),
        )

    override suspend fun getQuestionAudio(interviewId: Int, questionId: Int): ByteArray =
        interviewsApi.getQuestionAudio(interviewId, questionId).bytes()

    override suspend fun submitAudioAnswer(interviewId: Int, questionId: Int, audioFile: File): InterviewAnswerDto {
        val requestBody = audioFile.asRequestBody("audio/mp4".toMediaType())
        val filePart = MultipartBody.Part.createFormData("file", audioFile.name, requestBody)
        val questionIdPart = questionId.toString().toRequestBody("text/plain".toMediaType())
        return interviewsApi.submitAudioAnswer(interviewId, questionIdPart, filePart)
    }

    override suspend fun getEvaluation(interviewId: Int): InterviewEvaluationDto =
        interviewsApi.getEvaluation(interviewId)
}
