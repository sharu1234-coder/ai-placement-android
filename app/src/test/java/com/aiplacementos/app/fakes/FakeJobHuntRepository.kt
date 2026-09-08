package com.aiplacementos.app.fakes

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
class FakeJobHuntRepository : JobHuntRepository {
    var profileResult: (suspend () -> JobSearchProfileDto)? = null
    var updateProfileResult: (suspend (JobSearchProfileUpdateDto) -> JobSearchProfileDto)? = null
    var roleSuggestionsResult: (suspend (Int) -> List<String>)? = null
    var searchResult: (suspend (JobSearchRequestDto) -> JobSearchResponseDto)? = null
    var getJobResult: (suspend (Int) -> JobResultDto)? = null
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

    // Recorded calls, so a test can assert what was actually sent rather than only what came
    // back -- e.g. that a filter tab really pushed employment_type into the request.
    var lastSearchRequest: JobSearchRequestDto? = null
    var lastSavedStatusFilter: List<String>? = null
    var lastUpdatedSavedJobId: Int? = null
    var lastUpdatedStatus: String? = null
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

    override suspend fun search(request: JobSearchRequestDto): JobSearchResponseDto {
        lastSearchRequest = request
        return requireNotNull(searchResult) { "searchResult not configured" }(request)
    }

    override suspend fun getJob(listingId: Int): JobResultDto =
        requireNotNull(getJobResult) { "getJobResult not configured" }(listingId)

    override suspend fun saveJob(listingId: Int, status: String): SavedJobDto {
        savedListingIds += listingId
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
}
