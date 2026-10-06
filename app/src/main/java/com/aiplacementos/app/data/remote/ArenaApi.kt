package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ArenaApi {
    @GET("arena/challenges")
    suspend fun listChallenges(): List<ArenaChallengeDto>

    @POST("arena/challenges/{challengeKey}/attempt")
    suspend fun startAttempt(
        @Path("challengeKey") challengeKey: String,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): ArenaAttemptStartDto

    @POST("arena/attempts/{arenaAttemptId}/complete")
    suspend fun completeAttempt(
        @Path("arenaAttemptId") arenaAttemptId: Int,
        @Body request: CompleteArenaAttemptRequestDto,
    ): ArenaAttemptResultDto

    @GET("arena/challenges/{challengeKey}/history")
    suspend fun getHistory(@Path("challengeKey") challengeKey: String): ArenaChallengeHistoryDto
}
