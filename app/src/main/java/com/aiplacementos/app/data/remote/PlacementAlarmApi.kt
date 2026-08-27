package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PlacementAlarmApi {
    @GET("placement-alarm/eligibility")
    suspend fun checkEligibility(): AlarmEligibilityDto

    @POST("placement-alarm/challenges")
    suspend fun startChallenge(@Body request: StartChallengeRequestDto): ChallengeStateDto

    @GET("placement-alarm/challenges/{challengeId}")
    suspend fun getChallenge(@Path("challengeId") challengeId: Int): ChallengeStateDto

    @POST("placement-alarm/challenges/{challengeId}/answer")
    suspend fun submitAnswer(
        @Path("challengeId") challengeId: Int,
        @Body request: SubmitChallengeAnswerRequestDto,
    ): ChallengeAnswerResultDto
}
