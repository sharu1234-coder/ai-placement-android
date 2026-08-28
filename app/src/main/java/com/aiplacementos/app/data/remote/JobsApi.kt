package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface JobsApi {
    @GET("jobs")
    suspend fun listJobDescriptions(): List<JobDescriptionListItemDto>

    @POST("jobs")
    suspend fun createJobDescription(@Body request: ParseJdRequestDto): JobDescriptionParseResponseDto

    @POST("jobs/{id}/match")
    suspend fun matchJob(@Path("id") id: Int): JobMatchDto

    @POST("jobs/{id}/preparation-plan")
    suspend fun generatePreparationPlan(
        @Path("id") id: Int,
        @Body request: GeneratePreparationPlanRequestDto,
    ): JobPreparationPlanDto

    @GET("jobs/{id}/preparation-plan")
    suspend fun getPreparationPlan(@Path("id") id: Int): JobPreparationPlanDto

    @DELETE("jobs/{id}")
    suspend fun deleteJobDescription(@Path("id") id: Int)
}
