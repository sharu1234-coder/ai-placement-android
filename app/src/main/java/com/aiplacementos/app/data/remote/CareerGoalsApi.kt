package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Eight endpoints. The six the goal itself needs, plus the two that attach and detach the
 * real-world application it is being pursued through. The detail response carries alignment, evidence, risks, the
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
    suspend fun createGoal(
        @Body body: CareerGoalCreateRequest,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): CareerGoalListItemDto

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
    suspend fun analyzeGoal(
        @Path("careerGoalId") careerGoalId: Int,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): CareerGoalAnalyzeResponseDto

    /**
     * PUT, not POST, because linking is idempotent: sending the same pair twice leaves the
     * same single link, which is what a client retrying a dropped response needs.
     *
     * Costs nothing -- the server runs no AI for this, so it is safe to offer as an ordinary
     * button rather than as a deliberate, explained action the way [analyzeGoal] has to be.
     */
    @PUT("career-goals/{careerGoalId}/application")
    suspend fun linkApplication(
        @Path("careerGoalId") careerGoalId: Int,
        @Body body: LinkApplicationRequest,
    ): CareerGoalListItemDto

    @DELETE("career-goals/{careerGoalId}/application")
    suspend fun unlinkApplication(
        @Path("careerGoalId") careerGoalId: Int,
    ): CareerGoalListItemDto
}
