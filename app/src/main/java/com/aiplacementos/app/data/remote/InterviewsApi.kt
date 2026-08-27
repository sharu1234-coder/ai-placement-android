package com.aiplacementos.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface InterviewsApi {
    @GET("interviews")
    suspend fun listInterviews(): List<InterviewHistoryItemDto>

    @POST("interviews")
    suspend fun startInterview(@Body request: StartInterviewRequestDto): InterviewDto

    @GET("interviews/{interviewId}/next-question")
    suspend fun nextQuestion(@Path("interviewId") interviewId: Int): NextInterviewQuestionResponseDto

    @POST("interviews/{interviewId}/answer")
    suspend fun submitAnswer(
        @Path("interviewId") interviewId: Int,
        @Body request: SubmitInterviewAnswerRequestDto,
    ): InterviewAnswerDto

    @GET("interviews/{interviewId}/questions/{questionId}/audio")
    suspend fun getQuestionAudio(
        @Path("interviewId") interviewId: Int,
        @Path("questionId") questionId: Int,
    ): ResponseBody

    @Multipart
    @POST("interviews/{interviewId}/answer/audio")
    suspend fun submitAudioAnswer(
        @Path("interviewId") interviewId: Int,
        @Part("interview_question_id") interviewQuestionId: RequestBody,
        @Part file: MultipartBody.Part,
    ): InterviewAnswerDto

    @GET("interviews/{interviewId}/evaluation")
    suspend fun getEvaluation(@Path("interviewId") interviewId: Int): InterviewEvaluationDto
}
