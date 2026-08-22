package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface InterviewsApi {
    @POST("interviews")
    suspend fun startInterview(@Body request: StartInterviewRequestDto): InterviewDto

    @GET("interviews/{interviewId}/next-question")
    suspend fun nextQuestion(@Path("interviewId") interviewId: Int): NextInterviewQuestionResponseDto

    @POST("interviews/{interviewId}/answer")
    suspend fun submitAnswer(
        @Path("interviewId") interviewId: Int,
        @Body request: SubmitInterviewAnswerRequestDto,
    ): InterviewAnswerDto

    @GET("interviews/{interviewId}/evaluation")
    suspend fun getEvaluation(@Path("interviewId") interviewId: Int): InterviewEvaluationDto
}
