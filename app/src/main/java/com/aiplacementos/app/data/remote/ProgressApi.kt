package com.aiplacementos.app.data.remote

import retrofit2.http.GET

interface ProgressApi {
    @GET("progress/dashboard")
    suspend fun getDashboard(): ProgressDashboardDto
}
