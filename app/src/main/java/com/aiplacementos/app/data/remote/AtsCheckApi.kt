package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AtsCheckApi {
    @POST("resumes/{resumeId}/ats-check")
    suspend fun createAtsCheck(
        @Path("resumeId") resumeId: Int,
        @Body request: CreateAtsCheckRequestDto,
    ): AtsCheckReportDto

    @GET("resumes/ats-checks")
    suspend fun listAtsChecks(): List<AtsCheckListItemDto>

    @GET("resumes/ats-checks/{checkId}")
    suspend fun getAtsCheck(@Path("checkId") checkId: Int): AtsCheckReportDto
}
