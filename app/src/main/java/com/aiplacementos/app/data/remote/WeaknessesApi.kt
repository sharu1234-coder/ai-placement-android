package com.aiplacementos.app.data.remote

import retrofit2.http.GET

interface WeaknessesApi {
    @GET("weaknesses")
    suspend fun listWeaknesses(): List<WeaknessRecordDto>
}
