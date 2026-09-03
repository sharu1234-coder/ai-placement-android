package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface CareerDecisionApi {
    @POST("career-decisions/simulate")
    suspend fun simulate(@Body request: SimulateCareerDecisionsRequestDto): CareerDecisionSimulationDto
}
