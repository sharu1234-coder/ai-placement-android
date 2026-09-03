package com.aiplacementos.app.data.remote

import retrofit2.http.GET

interface NextBestActionApi {
    @GET("next-best-action")
    suspend fun getNextBestAction(): RecommendedActionDto
}
