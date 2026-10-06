package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ApplicationReadinessDto
import com.aiplacementos.app.data.remote.JobAlertCreateDto
import com.aiplacementos.app.data.remote.JobAlertDto
import com.aiplacementos.app.data.remote.JobAlertHistoryDto
import com.aiplacementos.app.data.remote.JobAlertQuotaDto
import com.aiplacementos.app.data.remote.JobAlertUpdateDto
import com.aiplacementos.app.data.remote.JobHuntEntitlementDto
import com.aiplacementos.app.data.remote.JobHuntHomeDto
import com.aiplacementos.app.data.remote.JobResultDto
import com.aiplacementos.app.data.remote.JobSearchProfileDto
import com.aiplacementos.app.data.remote.JobSearchProfileUpdateDto
import com.aiplacementos.app.data.remote.JobSearchRequestDto
import com.aiplacementos.app.data.remote.JobSearchResponseDto
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.data.remote.SavedSearchDto
import com.aiplacementos.app.data.remote.SkillMatchAnalysisDto

/**
 * Deliberately independent of JobRepository, which serves the Job Description Analyzer. The two
 * answer different questions -- "how do I score against this JD I pasted" versus "which real
 * adverts should I apply to" -- and share no server state.
 *
 * Every method here can throw. Search in particular has several distinct failure modes the UI
 * must tell apart (not configured, provider down, rate limited, timed out), and the backend
 * signals them with different HTTP status codes rather than parseable text -- see
 * JobHuntSearchViewModel for how each is classified.
 */
interface JobHuntRepository {
    suspend fun getProfile(): JobSearchProfileDto
    suspend fun updateProfile(update: JobSearchProfileUpdateDto): JobSearchProfileDto
    suspend fun getRoleSuggestions(resumeId: Int): List<String>

    /**
     * [idempotencyKey] names one search *operation*, so its retries and its extra pages are
     * recognised as the same thing the student asked for once. Optional: a null key means
     * "this request stands alone", which is the correct default and never the permissive one.
     */
    suspend fun search(
        request: JobSearchRequestDto,
        idempotencyKey: String? = null,
    ): JobSearchResponseDto

    /** What the student is allowed to do next, decided server-side. UX only -- the search
     * endpoint re-derives it and is the only thing that actually enforces anything. */
    suspend fun getEntitlement(): JobHuntEntitlementDto
    suspend fun getJob(listingId: Int): JobResultDto

    /**
     * Records a job the student found off-platform and starts tracking it.
     *
     * The one path into the tracker that does not go through the job provider, which is what
     * makes the whole outcome loop usable when the provider is unconfigured, down, or out of
     * the deployment's shared quota. Blank strings are the caller's to normalise to null.
     */
    suspend fun addManualJob(
        title: String,
        company: String? = null,
        location: String? = null,
        url: String? = null,
        description: String? = null,
        status: String = "saved",
        idempotencyKey: String? = null,
    ): SavedJobDto

    /** [source] records which screen the tap came from, for tracker provenance. It is a
     * display label, never an entitlement or an ownership claim, so the client may assert it. */
    suspend fun saveJob(
        listingId: Int,
        status: String = "saved",
        source: String = "manual",
        sourceSavedSearchId: Int? = null,
    ): SavedJobDto
    suspend fun listSaved(statuses: List<String>? = null): List<SavedJobDto>
    suspend fun updateSaved(
        savedJobId: Int,
        status: String? = null,
        notes: String? = null,
        followUpDate: String? = null,
    ): SavedJobDto
    suspend fun deleteSaved(savedJobId: Int)

    suspend fun createSavedSearch(
        name: String,
        filters: JobSearchRequestDto,
        notifyEnabled: Boolean = false,
        idempotencyKey: String? = null,
    ): SavedSearchDto
    suspend fun listSavedSearches(): List<SavedSearchDto>
    suspend fun deleteSavedSearch(savedSearchId: Int)
    suspend fun runSavedSearch(
        savedSearchId: Int,
        idempotencyKey: String? = null,
    ): JobSearchResponseDto

    suspend fun getHome(): JobHuntHomeDto
    suspend fun getSkillAnalysis(listingId: Int): SkillMatchAnalysisDto

    /** Premium on the server. Throws the 403 through so the caller can show a paywall rather
     * than an error -- see toJobSearchFailure's PremiumRequired branch. */
    suspend fun getReadiness(listingId: Int): ApplicationReadinessDto

    suspend fun listAlerts(): List<JobAlertDto>
    suspend fun getAlertQuota(): JobAlertQuotaDto
    suspend fun getAlertHistory(): List<JobAlertHistoryDto>
    suspend fun createAlert(
        body: JobAlertCreateDto,
        idempotencyKey: String? = null,
    ): JobAlertDto
    suspend fun updateAlert(alertId: Int, body: JobAlertUpdateDto): JobAlertDto
    suspend fun deleteAlert(alertId: Int)
}
