package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
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

    /**
     * [idempotencyKey] identifies one *operation* -- one tap on "Find Jobs" -- rather than one
     * request. Sending the same key for a retry of that tap (a dropped connection, a recreated
     * activity, a killed process) tells the server this is the same search it may already have
     * run, so a flaky connection does not cost a free student their one search. Retrofit omits
     * the header entirely when the value is null, which the server reads as "its own operation".
     *
     * It cannot grant anything. The server binds it to a fingerprint of the search parameters
     * and to the caller's own profile, so the only thing a key can do is make a repeat cheaper.
     */
    @POST("job-hunt/search")
    suspend fun search(
        @Body body: JobSearchRequestDto,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): JobSearchResponseDto

    /** Whether this student may run a search, and what to say if not. Reading it costs nothing
     * and grants nothing -- POST /search decides for itself. */
    @GET("job-hunt/entitlement")
    suspend fun getEntitlement(): JobHuntEntitlementDto

    @GET("job-hunt/jobs/{listingId}")
    suspend fun getJob(@Path("listingId") listingId: Int): JobResultDto

    /**
     * Records a job the student found outside this app and starts tracking it, in one call.
     *
     * Returns the tracked row rather than a bare listing: a listing nobody is tracking is not
     * something a student can act on, and the tracker row is what every screen after this one
     * needs. Costs no provider quota and makes no AI call, so it works with the job provider
     * unconfigured, down or out of budget -- which is the point of it existing.
     */
    @POST("job-hunt/listings")
    suspend fun createListing(@Body body: ManualJobCreateDto): SavedJobDto

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

    /** Re-runs a stored search and records that it ran. Shares the search endpoint's rate
     * limit on the server: a saved search is a search, not a second allowance. */
    @POST("job-hunt/saved-searches/{savedSearchId}/run")
    suspend fun runSavedSearch(
        @Path("savedSearchId") savedSearchId: Int,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): JobSearchResponseDto

    /** Counts and top recommendations from what is already stored. Never calls the provider,
     * so opening the app costs no quota and no network round trip beyond this one. */
    @GET("job-hunt/home")
    suspend fun getHome(): JobHuntHomeDto

    @GET("job-hunt/jobs/{listingId}/skill-analysis")
    suspend fun getSkillAnalysis(@Path("listingId") listingId: Int): SkillMatchAnalysisDto

    /** Premium on the server. A free student gets 403 with a message naming what stays free. */
    @GET("job-hunt/jobs/{listingId}/readiness")
    suspend fun getReadiness(@Path("listingId") listingId: Int): ApplicationReadinessDto

    @GET("job-hunt/alerts")
    suspend fun listAlerts(): List<JobAlertDto>

    @GET("job-hunt/alerts/quota")
    suspend fun getAlertQuota(): JobAlertQuotaDto

    @GET("job-hunt/alerts/history")
    suspend fun getAlertHistory(): List<JobAlertHistoryDto>

    @POST("job-hunt/alerts")
    suspend fun createAlert(@Body body: JobAlertCreateDto): JobAlertDto

    @PATCH("job-hunt/alerts/{alertId}")
    suspend fun updateAlert(
        @Path("alertId") alertId: Int,
        @Body body: JobAlertUpdateDto,
    ): JobAlertDto

    @DELETE("job-hunt/alerts/{alertId}")
    suspend fun deleteAlert(@Path("alertId") alertId: Int)
}
