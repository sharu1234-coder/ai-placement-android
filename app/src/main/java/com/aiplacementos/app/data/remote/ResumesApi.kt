package com.aiplacementos.app.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

interface ResumesApi {
    @Multipart
    @POST("resumes")
    suspend fun uploadResume(@Part file: MultipartBody.Part): ResumeUploadResponseDto

    /** Empty until the student uploads a resume -- see [NullableBody]. */
    @GET("resumes/me/latest")
    suspend fun getMyLatestResume(): NullableBody<ResumeSummaryDto>

    /** @Streaming so the PDF is handed over as a stream instead of being fully buffered by
     * Retrofit before we see it -- the caller writes it straight to a cache file. */
    @Streaming
    @GET("resumes/{id}/file")
    suspend fun getResumeFile(@Path("id") id: Int): ResponseBody

    @DELETE("resumes/{id}")
    suspend fun deleteResume(@Path("id") id: Int)
}
