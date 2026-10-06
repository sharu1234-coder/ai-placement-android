package com.aiplacementos.app.data.remote

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface JobsApi {
    @GET("jobs")
    suspend fun listJobDescriptions(): List<JobDescriptionListItemDto>

    @POST("jobs")
    suspend fun createJobDescription(
        @Body request: ParseJdRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): JobDescriptionParseResponseDto

    /** Same pipeline as createJobDescription -- the server extracts the text and parses it
     * identically; only how the job description arrived differs. */
    @Multipart
    @POST("jobs/upload")
    suspend fun uploadJobDescription(
        @Part file: MultipartBody.Part,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): JobDescriptionParseResponseDto

    @POST("jobs/{id}/match")
    suspend fun matchJob(
        @Path("id") id: Int,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): JobMatchDto

    @POST("jobs/{id}/preparation-plan")
    suspend fun generatePreparationPlan(
        @Path("id") id: Int,
        @Body request: GeneratePreparationPlanRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): JobPreparationPlanDto

    @GET("jobs/{id}/preparation-plan")
    suspend fun getPreparationPlan(@Path("id") id: Int): JobPreparationPlanDto

    @DELETE("jobs/{id}")
    suspend fun deleteJobDescription(@Path("id") id: Int)
}
