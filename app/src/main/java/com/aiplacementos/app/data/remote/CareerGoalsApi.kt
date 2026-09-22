package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Six endpoints, and no more. The detail response carries alignment, evidence, risks, the
 * preparation plan, progress and the next action in one body, so the screen needs one call
 * rather than six -- and because none of that runs AI server-side, reopening it is free.
 *
 * [analyze] is the only call here that costs anything, which is why it is a separate, explicit
 * action rather than something the detail fetch triggers on its own.
 */
interface CareerGoalsApi {
    @GET("career-goals")
    suspend fun listGoals(): List<CareerGoalListItemDto>

    @POST("career-goals")
    suspend fun createGoal(@Body body: CareerGoalCreateRequest): CareerGoalListItemDto

    @GET("career-goals/{careerGoalId}")
    suspend fun getGoal(@Path("careerGoalId") careerGoalId: Int): CareerGoalDetailDto

    @PATCH("career-goals/{careerGoalId}")
    suspend fun updateGoal(
        @Path("careerGoalId") careerGoalId: Int,
        @Body body: CareerGoalUpdateRequest,
    ): CareerGoalListItemDto

    @DELETE("career-goals/{careerGoalId}")
    suspend fun deleteGoal(@Path("careerGoalId") careerGoalId: Int)

    @POST("career-goals/{careerGoalId}/analyze")
    suspend fun analyzeGoal(@Path("careerGoalId") careerGoalId: Int): CareerGoalAnalyzeResponseDto
}
