package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface PlacementPathApi {
    @POST("placement-path/generate")
    suspend fun generate(): PlacementPathDto

    /** Empty until a placement path is generated -- see [NullableBody]. */
    @GET("placement-path/current")
    suspend fun getCurrent(): NullableBody<PlacementPathDto>

    @GET("placement-path/history")
    suspend fun getHistory(): List<PlacementPathDto>

    @POST("placement-path/topics/{topicId}/start")
    suspend fun startTopic(@Path("topicId") topicId: Int): PlacementPathTopicDto

    @POST("placement-path/topics/{topicId}/practice")
    suspend fun practice(@Path("topicId") topicId: Int): PracticeHandoffDto

    @PATCH("placement-path/topics/{topicId}")
    suspend fun markComplete(
        @Path("topicId") topicId: Int,
        @Body request: UpdatePlacementPathTopicRequestDto,
    ): PlacementPathTopicDto
}
