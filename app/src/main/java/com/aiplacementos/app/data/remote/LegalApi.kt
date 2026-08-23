package com.aiplacementos.app.data.remote

import retrofit2.http.GET

interface LegalApi {
    @GET("legal/privacy-policy")
    suspend fun getPrivacyPolicy(): LegalDocumentDto
}
