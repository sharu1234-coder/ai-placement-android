package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MissionApi {
    @GET("missions/today")
    suspend fun getToday(): MissionDto

    @POST("missions/{missionId}/start")
    suspend fun start(@Path("missionId") missionId: Int): MissionDto

    @POST("missions/{missionId}/complete")
    suspend fun complete(
        @Path("missionId") missionId: Int,
        @Body request: CompleteMissionRequestDto,
    ): MissionDto
}
