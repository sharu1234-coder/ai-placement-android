package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.JobHuntApi
import com.aiplacementos.app.data.remote.JobResultDto
import com.aiplacementos.app.data.remote.JobSearchProfileDto
import com.aiplacementos.app.data.remote.JobSearchProfileUpdateDto
import com.aiplacementos.app.data.remote.JobSearchRequestDto
import com.aiplacementos.app.data.remote.JobSearchResponseDto
import com.aiplacementos.app.data.remote.SaveJobRequestDto
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.data.remote.SavedSearchCreateDto
import com.aiplacementos.app.data.remote.SavedSearchDto
import com.aiplacementos.app.data.remote.UpdateSavedJobRequestDto
import com.aiplacementos.app.domain.repository.JobHuntRepository

class JobHuntRepositoryImpl(private val jobHuntApi: JobHuntApi) : JobHuntRepository {
    override suspend fun getProfile(): JobSearchProfileDto = jobHuntApi.getProfile()

    override suspend fun updateProfile(update: JobSearchProfileUpdateDto): JobSearchProfileDto =
        jobHuntApi.updateProfile(update)

    override suspend fun getRoleSuggestions(resumeId: Int): List<String> =
        jobHuntApi.getRoleSuggestions(resumeId).suggestedRoleFamilies

    override suspend fun search(request: JobSearchRequestDto): JobSearchResponseDto =
        jobHuntApi.search(request)

    override suspend fun getJob(listingId: Int): JobResultDto = jobHuntApi.getJob(listingId)

    override suspend fun saveJob(listingId: Int, status: String): SavedJobDto =
        jobHuntApi.saveJob(SaveJobRequestDto(listingId = listingId, status = status))

    override suspend fun listSaved(statuses: List<String>?): List<SavedJobDto> =
        jobHuntApi.listSaved(statuses)

    override suspend fun updateSaved(
        savedJobId: Int,
        status: String?,
        notes: String?,
        followUpDate: String?,
    ): SavedJobDto = jobHuntApi.updateSaved(
        savedJobId,
        UpdateSavedJobRequestDto(status = status, notes = notes, followUpDate = followUpDate),
    )

    override suspend fun deleteSaved(savedJobId: Int) = jobHuntApi.deleteSaved(savedJobId)

    override suspend fun createSavedSearch(
        name: String,
        filters: JobSearchRequestDto,
        notifyEnabled: Boolean,
    ): SavedSearchDto = jobHuntApi.createSavedSearch(
        SavedSearchCreateDto(name = name, filters = filters, notifyEnabled = notifyEnabled),
    )

    override suspend fun listSavedSearches(): List<SavedSearchDto> = jobHuntApi.listSavedSearches()

    override suspend fun deleteSavedSearch(savedSearchId: Int) =
        jobHuntApi.deleteSavedSearch(savedSearchId)
}
