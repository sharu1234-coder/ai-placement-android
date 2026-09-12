package com.aiplacementos.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Career Strategy endpoints.
 *
 * `getStrategy` and `getTargets` are callable by every signed-in student and return a projected
 * response -- the server decides what is in it. The other three are Premium and return 403 to a
 * free caller, which the ViewModel turns into a locked state rather than an error, because a
 * paywall is not a failure.
 *
 * Nothing here sends an API key, and nothing here talks to a job provider. Provider traffic is
 * backend-only by design; this client never holds a provider credential.
 */
interface CareerStrategyApi {
    @GET("career-strategy")
    suspend fun getStrategy(): CareerStrategyDto

    /** Premium. 403 for a free student. */
    @GET("career-strategy/history")
    suspend fun getHistory(): StrategyHistoryDto

    /** Premium. 404 for a snapshot that is not this student's -- never 403, so ids cannot be probed. */
    @GET("career-strategy/compare/{snapshotId}")
    suspend fun compare(@Path("snapshotId") snapshotId: Int): StrategyComparisonDto

    /** Premium. 404 for an unknown listing. */
    @GET("career-strategy/job/{listingId}")
    suspend fun getJobStrategy(@Path("listingId") listingId: Int): JobStrategyDto

    /**
     * Free-callable, and returns `locked=true` with no comparison data for a free student --
     * deliberately not a 403, so the screen has something to render an upgrade prompt around.
     */
    @GET("career-strategy/targets")
    suspend fun getTargets(): TargetComparisonDto

    /** Premium. Replaces the comparison set; an empty list clears it. */
    @POST("career-strategy/targets")
    suspend fun setTargets(@Body request: SetStrategyTargetsRequest): TargetComparisonDto
}
