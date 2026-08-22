package com.aiplacementos.app.data.remote

import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ResumesApi {
    @Multipart
    @POST("resumes")
    suspend fun uploadResume(@Part file: MultipartBody.Part): ResumeUploadResponseDto

    @GET("resumes/{id}/analysis")
    suspend fun getResumeAnalysis(@Path("id") id: Int): ResumeAnalysisDto
}
