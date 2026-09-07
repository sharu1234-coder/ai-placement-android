package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface JobHuntApi {
    @GET("job-hunt/profile")
    suspend fun getProfile(): JobSearchProfileDto

    @PUT("job-hunt/profile")
    suspend fun updateProfile(@Body body: JobSearchProfileUpdateDto): JobSearchProfileDto

    @GET("job-hunt/profile/role-suggestions")
    suspend fun getRoleSuggestions(@Query("resume_id") resumeId: Int): RoleSuggestionsDto

    @POST("job-hunt/search")
    suspend fun search(@Body body: JobSearchRequestDto): JobSearchResponseDto

    @GET("job-hunt/jobs/{listingId}")
    suspend fun getJob(@Path("listingId") listingId: Int): JobResultDto

    @POST("job-hunt/saved")
    suspend fun saveJob(@Body body: SaveJobRequestDto): SavedJobDto

    /** [statuses] is repeated once per value, matching the backend's `status` list query. */
    @GET("job-hunt/saved")
    suspend fun listSaved(@Query("status") statuses: List<String>? = null): List<SavedJobDto>

    @PATCH("job-hunt/saved/{savedJobId}")
    suspend fun updateSaved(
        @Path("savedJobId") savedJobId: Int,
        @Body body: UpdateSavedJobRequestDto,
    ): SavedJobDto

    @DELETE("job-hunt/saved/{savedJobId}")
    suspend fun deleteSaved(@Path("savedJobId") savedJobId: Int)

    @POST("job-hunt/saved-searches")
    suspend fun createSavedSearch(@Body body: SavedSearchCreateDto): SavedSearchDto

    @GET("job-hunt/saved-searches")
    suspend fun listSavedSearches(): List<SavedSearchDto>

    @DELETE("job-hunt/saved-searches/{savedSearchId}")
    suspend fun deleteSavedSearch(@Path("savedSearchId") savedSearchId: Int)
}
