package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RoadmapApi {
    @POST("roadmap/generate")
    suspend fun generateRoadmap(
        @Body request: GenerateRoadmapRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): RoadmapDto

    /** Empty until a roadmap is generated for this scope -- see [NullableBody]. */
    @GET("roadmap/current")
    suspend fun getCurrentRoadmap(@Query("jd_id") jdId: Int?): NullableBody<RoadmapDto>

    @PATCH("roadmap/tasks/{taskId}")
    suspend fun updateTask(@Path("taskId") taskId: Int, @Body request: UpdateRoadmapTaskRequestDto): RoadmapTaskDto

    @POST("roadmap/{roadmapId}/reassess")
    suspend fun reassessRoadmap(
        @Path("roadmapId") roadmapId: Int,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): RoadmapDto
}
