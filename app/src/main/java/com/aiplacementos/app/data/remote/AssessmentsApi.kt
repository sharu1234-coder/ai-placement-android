package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AssessmentsApi {
    @POST("assessments/blueprint")
    suspend fun createBlueprint(
        @Body request: PrepareMeRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): AssessmentBlueprintDto

    /**
     * [idempotencyKey] names the logical operation, not this attempt at it.
     *
     * OkHttp re-sends a request whose connection failed after it was written, and it re-sends
     * *this* Request -- same headers -- so the key arrives unchanged on the retry and the server
     * recognises the second arrival as the same operation. That is the whole mechanism: the key
     * has to be minted once, where the student's intent begins, and never per attempt.
     */
    @POST("assessments")
    suspend fun startAssessment(
        @Body request: StartAssessmentRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): AssessmentDto

    @GET("assessments/{assessmentId}/next-question")
    suspend fun nextQuestion(@Path("assessmentId") assessmentId: Int): NextQuestionResponseDto

    @POST("assessments/{assessmentId}/answer")
    suspend fun submitAnswer(
        @Path("assessmentId") assessmentId: Int,
        @Body request: SubmitAnswerRequestDto,
    ): AnswerResultDto

    @GET("assessments/{assessmentId}/result")
    suspend fun getResult(@Path("assessmentId") assessmentId: Int): AssessmentResultDto

    @GET("assessments/history")
    suspend fun getHistory(): List<AssessmentHistoryItemDto>
}
