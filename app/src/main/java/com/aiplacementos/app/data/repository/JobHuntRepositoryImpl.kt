package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.JobHuntApi
import com.aiplacementos.app.data.remote.ApplicationReadinessDto
import com.aiplacementos.app.data.remote.JobAlertCreateDto
import com.aiplacementos.app.data.remote.JobAlertDto
import com.aiplacementos.app.data.remote.JobAlertHistoryDto
import com.aiplacementos.app.data.remote.JobAlertQuotaDto
import com.aiplacementos.app.data.remote.JobAlertUpdateDto
import com.aiplacementos.app.data.remote.JobHuntHomeDto
import com.aiplacementos.app.data.remote.SkillMatchAnalysisDto
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

    override suspend fun saveJob(
        listingId: Int,
        status: String,
        source: String,
        sourceSavedSearchId: Int?,
    ): SavedJobDto =
        jobHuntApi.saveJob(
            SaveJobRequestDto(
                listingId = listingId,
                status = status,
                source = source,
                sourceSavedSearchId = sourceSavedSearchId,
            ),
        )

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

    override suspend fun runSavedSearch(savedSearchId: Int): JobSearchResponseDto =
        jobHuntApi.runSavedSearch(savedSearchId)

    override suspend fun getHome(): JobHuntHomeDto = jobHuntApi.getHome()

    override suspend fun getSkillAnalysis(listingId: Int): SkillMatchAnalysisDto =
        jobHuntApi.getSkillAnalysis(listingId)

    override suspend fun getReadiness(listingId: Int): ApplicationReadinessDto =
        jobHuntApi.getReadiness(listingId)

    override suspend fun listAlerts(): List<JobAlertDto> = jobHuntApi.listAlerts()

    override suspend fun getAlertQuota(): JobAlertQuotaDto = jobHuntApi.getAlertQuota()

    override suspend fun getAlertHistory(): List<JobAlertHistoryDto> = jobHuntApi.getAlertHistory()

    override suspend fun createAlert(body: JobAlertCreateDto): JobAlertDto =
        jobHuntApi.createAlert(body)

    override suspend fun updateAlert(alertId: Int, body: JobAlertUpdateDto): JobAlertDto =
        jobHuntApi.updateAlert(alertId, body)

    override suspend fun deleteAlert(alertId: Int) = jobHuntApi.deleteAlert(alertId)
}
