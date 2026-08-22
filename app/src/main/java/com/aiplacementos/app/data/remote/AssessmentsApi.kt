package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AssessmentsApi {
    @POST("assessments/blueprint")
    suspend fun createBlueprint(@Body request: PrepareMeRequestDto): AssessmentBlueprintDto

    @POST("assessments")
    suspend fun startAssessment(@Body request: StartAssessmentRequestDto): AssessmentDto

    @GET("assessments/{assessmentId}/next-question")
    suspend fun nextQuestion(@Path("assessmentId") assessmentId: Int): NextQuestionResponseDto

    @POST("assessments/{assessmentId}/answer")
    suspend fun submitAnswer(
        @Path("assessmentId") assessmentId: Int,
        @Body request: SubmitAnswerRequestDto,
    ): AnswerResultDto

    @GET("assessments/{assessmentId}/result")
    suspend fun getResult(@Path("assessmentId") assessmentId: Int): AssessmentResultDto
}
