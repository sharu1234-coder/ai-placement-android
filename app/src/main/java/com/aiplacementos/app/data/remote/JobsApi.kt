package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface JobsApi {
    @POST("jobs")
    suspend fun createJobDescription(@Body request: ParseJdRequestDto): JobDescriptionParseResponseDto

    @GET("jobs/{id}/requirements")
    suspend fun getRequirements(@Path("id") id: Int): List<RequirementDto>

    @POST("jobs/{id}/match")
    suspend fun matchJob(@Path("id") id: Int): JobMatchDto
}
