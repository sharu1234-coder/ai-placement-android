package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ApplicationReadinessDto
import com.aiplacementos.app.data.remote.JobAlertCreateDto
import com.aiplacementos.app.data.remote.JobAlertDto
import com.aiplacementos.app.data.remote.JobAlertHistoryDto
import com.aiplacementos.app.data.remote.JobAlertQuotaDto
import com.aiplacementos.app.data.remote.JobAlertUpdateDto
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

    suspend fun search(request: JobSearchRequestDto): JobSearchResponseDto
    suspend fun getJob(listingId: Int): JobResultDto

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
    ): SavedSearchDto
    suspend fun listSavedSearches(): List<SavedSearchDto>
    suspend fun deleteSavedSearch(savedSearchId: Int)
    suspend fun runSavedSearch(savedSearchId: Int): JobSearchResponseDto

    suspend fun getHome(): JobHuntHomeDto
    suspend fun getSkillAnalysis(listingId: Int): SkillMatchAnalysisDto

    /** Premium on the server. Throws the 403 through so the caller can show a paywall rather
     * than an error -- see toJobSearchFailure's PremiumRequired branch. */
    suspend fun getReadiness(listingId: Int): ApplicationReadinessDto

    suspend fun listAlerts(): List<JobAlertDto>
    suspend fun getAlertQuota(): JobAlertQuotaDto
    suspend fun getAlertHistory(): List<JobAlertHistoryDto>
    suspend fun createAlert(body: JobAlertCreateDto): JobAlertDto
    suspend fun updateAlert(alertId: Int, body: JobAlertUpdateDto): JobAlertDto
    suspend fun deleteAlert(alertId: Int)
}
