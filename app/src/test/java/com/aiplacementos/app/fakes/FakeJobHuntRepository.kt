package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ApplicationReadinessDto
import com.aiplacementos.app.data.remote.JobAlertCreateDto
import com.aiplacementos.app.data.remote.JobAlertDto
import com.aiplacementos.app.data.remote.JobAlertHistoryDto
import com.aiplacementos.app.data.remote.JobAlertQuotaDto
import com.aiplacementos.app.data.remote.JobAlertUpdateDto
import com.aiplacementos.app.data.remote.JobHuntEntitlementDto
import com.aiplacementos.app.data.remote.JobHuntHomeDto
import com.aiplacementos.app.data.remote.SkillMatchAnalysisDto
import com.aiplacementos.app.data.remote.JobResultDto
import com.aiplacementos.app.data.remote.JobSearchProfileDto
import com.aiplacementos.app.data.remote.JobSearchProfileUpdateDto
import com.aiplacementos.app.data.remote.JobSearchRequestDto
import com.aiplacementos.app.data.remote.JobSearchResponseDto
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.data.remote.SavedSearchDto
import com.aiplacementos.app.domain.repository.JobHuntRepository

/**
 * Each result is a lambda rather than a value so a test can throw from it -- the failure paths
 * (provider down, quota exhausted, Premium required) are most of what these ViewModels exist to
 * handle, and a plain value could only ever exercise the happy path.
 */
/** Exactly what reached the repository for one manual job, so a test can assert on the
 * normalisation the view model did rather than on what it hoped was sent. */
data class ManualJobSubmission(
    val title: String,
    val company: String?,
    val location: String?,
    val url: String?,
    val description: String?,
    val status: String,
)

class FakeJobHuntRepository : JobHuntRepository {
    var profileResult: (suspend () -> JobSearchProfileDto)? = null
    var updateProfileResult: (suspend (JobSearchProfileUpdateDto) -> JobSearchProfileDto)? = null
    var roleSuggestionsResult: (suspend (Int) -> List<String>)? = null
    var searchResult: (suspend (JobSearchRequestDto) -> JobSearchResponseDto)? = null
    var entitlementResult: (suspend () -> JobHuntEntitlementDto)? = null

    /**
     * Every Idempotency-Key the view model has sent, in order.
     *
     * Recorded rather than just the latest one, because what the tests need to assert is the
     * *relationship* between successive requests -- a retry and its original must share a key,
     * a new filter set must not -- and only the sequence shows that.
     */
    val sentIdempotencyKeys = mutableListOf<String?>()
    var getJobResult: (suspend (Int) -> JobResultDto)? = null
    var addManualJobResult: (suspend (ManualJobSubmission) -> SavedJobDto)? = null

    /**
     * Every manual job the view model has submitted, in order.
     *
     * Recorded rather than only the latest, because what these tests need to prove is that a
     * refused submit and its retry are *one* job rather than two, and that a double tap does
     * not send twice -- and only the sequence shows either.
     */
    val manualJobsAdded = mutableListOf<ManualJobSubmission>()
    var saveJobResult: (suspend (Int, String) -> SavedJobDto)? = null
    var listSavedResult: (suspend (List<String>?) -> List<SavedJobDto>)? = null
    var updateSavedResult: (suspend (Int, String?, String?, String?) -> SavedJobDto)? = null
    var deleteSavedResult: (suspend (Int) -> Unit)? = null
    var savedSearchesResult: (suspend () -> List<SavedSearchDto>)? = null
    var listSavedSearchesResult: (suspend () -> List<SavedSearchDto>)? = null
    var createSavedSearchResult: (suspend (String, JobSearchRequestDto) -> SavedSearchDto)? = null
    var deleteSavedSearchResult: (suspend (Int) -> Unit)? = null
    /** What the last createSavedSearch call was given, so a test can assert the filters that
     * were actually saved rather than the ones it hoped were. */
    var lastCreatedSavedSearch: Pair<String, JobSearchRequestDto>? = null

    var runSavedSearchResult: (suspend (Int) -> JobSearchResponseDto)? = null

    /**
     * Every saved search actually run through the server's own endpoint, in order.
     *
     * The sequence, not just the latest, because what these tests need to show is which of the
     * two paths a tap took -- the endpoint that records the run, or a replay of its filters
     * through the ordinary search -- and that changing a filter afterwards stops it being a
     * run of the saved search at all.
     */
    val savedSearchRunIds = mutableListOf<Int>()
    var homeResult: (suspend () -> JobHuntHomeDto)? = null
    var skillAnalysisResult: (suspend (Int) -> SkillMatchAnalysisDto)? = null
    var readinessResult: (suspend (Int) -> ApplicationReadinessDto)? = null
    var alertsResult: (suspend () -> List<JobAlertDto>)? = null
    var alertQuotaResult: (suspend () -> JobAlertQuotaDto)? = null
    var alertHistoryResult: (suspend () -> List<JobAlertHistoryDto>)? = null
    var createAlertResult: (suspend (JobAlertCreateDto) -> JobAlertDto)? = null
    var updateAlertResult: (suspend (Int, JobAlertUpdateDto) -> JobAlertDto)? = null
    var deleteAlertResult: (suspend (Int) -> Unit)? = null
    /** What the last saveJob call was told about provenance, so a test can assert the source
     * actually travelled rather than hoping it did. */
    var lastSaveSource: Pair<String, Int?>? = null

    // Recorded calls, so a test can assert what was actually sent rather than only what came
    // back -- e.g. that a filter tab really pushed employment_type into the request.
    var lastSearchRequest: JobSearchRequestDto? = null
    var lastSavedStatusFilter: List<String>? = null
    var lastUpdatedSavedJobId: Int? = null
    var lastUpdatedStatus: String? = null

    /**
     * The note text as it actually reached the repository.
     *
     * Recorded separately from the status because the two travel in one PATCH, and what these
     * tests need to prove is that writing a note sends the note and nothing else -- a status
     * riding along would move the application without the student asking.
     */
    var lastUpdatedNotes: String? = null

    /** How many writes were attempted, so a rapid-tap test can assert one rather than five. */
    var updateSavedCallCount: Int = 0
    val savedListingIds = mutableListOf<Int>()
    val deletedSavedIds = mutableListOf<Int>()
    var lastProfileUpdate: JobSearchProfileUpdateDto? = null

    override suspend fun getProfile(): JobSearchProfileDto =
        requireNotNull(profileResult) { "profileResult not configured" }()

    override suspend fun updateProfile(update: JobSearchProfileUpdateDto): JobSearchProfileDto {
        lastProfileUpdate = update
        return requireNotNull(updateProfileResult) { "updateProfileResult not configured" }(update)
    }

    override suspend fun getRoleSuggestions(resumeId: Int): List<String> =
        requireNotNull(roleSuggestionsResult) { "roleSuggestionsResult not configured" }(resumeId)

    override suspend fun search(
        request: JobSearchRequestDto,
        idempotencyKey: String?,
    ): JobSearchResponseDto {
        lastSearchRequest = request
        sentIdempotencyKeys += idempotencyKey
        return requireNotNull(searchResult) { "searchResult not configured" }(request)
    }

    override suspend fun getEntitlement(): JobHuntEntitlementDto =
        requireNotNull(entitlementResult) { "entitlementResult not configured" }()

    override suspend fun getJob(listingId: Int): JobResultDto =
        requireNotNull(getJobResult) { "getJobResult not configured" }(listingId)

    override suspend fun addManualJob(
        title: String,
        company: String?,
        location: String?,
        url: String?,
        description: String?,
        status: String,
    ): SavedJobDto {
        manualJobsAdded += ManualJobSubmission(title, company, location, url, description, status)
        return requireNotNull(addManualJobResult) { "addManualJobResult not configured" }(
            manualJobsAdded.last(),
        )
    }

    override suspend fun saveJob(
        listingId: Int,
        status: String,
        source: String,
        sourceSavedSearchId: Int?,
    ): SavedJobDto {
        savedListingIds += listingId
        lastSaveSource = source to sourceSavedSearchId
        return requireNotNull(saveJobResult) { "saveJobResult not configured" }(listingId, status)
    }

    override suspend fun listSaved(statuses: List<String>?): List<SavedJobDto> {
        lastSavedStatusFilter = statuses
        return requireNotNull(listSavedResult) { "listSavedResult not configured" }(statuses)
    }

    override suspend fun updateSaved(
        savedJobId: Int,
        status: String?,
        notes: String?,
        followUpDate: String?,
    ): SavedJobDto {
        lastUpdatedSavedJobId = savedJobId
        lastUpdatedStatus = status
        lastUpdatedNotes = notes
        updateSavedCallCount++
        return requireNotNull(updateSavedResult) { "updateSavedResult not configured" }(
            savedJobId, status, notes, followUpDate,
        )
    }

    override suspend fun deleteSaved(savedJobId: Int) {
        deletedSavedIds += savedJobId
        deleteSavedResult?.invoke(savedJobId)
    }

    override suspend fun createSavedSearch(
        name: String,
        filters: JobSearchRequestDto,
        notifyEnabled: Boolean,
    ): SavedSearchDto {
        lastCreatedSavedSearch = name to filters
        return requireNotNull(createSavedSearchResult) { "createSavedSearchResult not configured" }(
            name,
            filters,
        )
    }

    override suspend fun listSavedSearches(): List<SavedSearchDto> {
        val configured = listSavedSearchesResult ?: savedSearchesResult
        return requireNotNull(configured) { "listSavedSearchesResult not configured" }()
    }

    override suspend fun deleteSavedSearch(savedSearchId: Int) =
        requireNotNull(deleteSavedSearchResult) { "deleteSavedSearchResult not configured" }(
            savedSearchId,
        )

    override suspend fun runSavedSearch(
        savedSearchId: Int,
        idempotencyKey: String?,
    ): JobSearchResponseDto {
        sentIdempotencyKeys += idempotencyKey
        savedSearchRunIds += savedSearchId
        return requireNotNull(runSavedSearchResult) { "runSavedSearchResult not configured" }(
            savedSearchId,
        )
    }

    override suspend fun getHome(): JobHuntHomeDto =
        requireNotNull(homeResult) { "homeResult not configured" }()

    override suspend fun getSkillAnalysis(listingId: Int): SkillMatchAnalysisDto =
        requireNotNull(skillAnalysisResult) { "skillAnalysisResult not configured" }(listingId)

    override suspend fun getReadiness(listingId: Int): ApplicationReadinessDto =
        requireNotNull(readinessResult) { "readinessResult not configured" }(listingId)

    override suspend fun listAlerts(): List<JobAlertDto> =
        requireNotNull(alertsResult) { "alertsResult not configured" }()

    override suspend fun getAlertQuota(): JobAlertQuotaDto =
        requireNotNull(alertQuotaResult) { "alertQuotaResult not configured" }()

    override suspend fun getAlertHistory(): List<JobAlertHistoryDto> =
        requireNotNull(alertHistoryResult) { "alertHistoryResult not configured" }()

    override suspend fun createAlert(body: JobAlertCreateDto): JobAlertDto =
        requireNotNull(createAlertResult) { "createAlertResult not configured" }(body)

    override suspend fun updateAlert(alertId: Int, body: JobAlertUpdateDto): JobAlertDto =
        requireNotNull(updateAlertResult) { "updateAlertResult not configured" }(alertId, body)

    override suspend fun deleteAlert(alertId: Int) =
        requireNotNull(deleteAlertResult) { "deleteAlertResult not configured" }(alertId)
}
