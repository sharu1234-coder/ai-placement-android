package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface AssessmentsApi {
    @POST("assessments/blueprint")
    suspend fun createBlueprint(@Body request: PrepareMeRequestDto): AssessmentBlueprintDto
}
