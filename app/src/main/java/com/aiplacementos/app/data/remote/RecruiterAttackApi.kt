package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface RecruiterAttackApi {
    @POST("recruiter-attack/sessions")
    suspend fun startSession(
        @Body request: StartRecruiterAttackSessionRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): RecruiterAttackSessionDto

    @GET("recruiter-attack/sessions")
    suspend fun listSessions(): List<RecruiterAttackSessionListItemDto>

    @GET("recruiter-attack/sessions/{sessionId}/next-turn")
    suspend fun nextTurn(@Path("sessionId") sessionId: Int): NextRecruiterAttackTurnResponseDto

    @POST("recruiter-attack/sessions/{sessionId}/answer")
    suspend fun answer(
        @Path("sessionId") sessionId: Int,
        @Body request: SubmitRecruiterAttackAnswerRequestDto,
    ): RecruiterAttackTurnDto

    @GET("recruiter-attack/sessions/{sessionId}/results")
    suspend fun getResults(@Path("sessionId") sessionId: Int): RecruiterAttackResultsDto
}
