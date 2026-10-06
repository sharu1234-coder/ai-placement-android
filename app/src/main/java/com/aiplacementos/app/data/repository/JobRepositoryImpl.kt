package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.GeneratePreparationPlanRequestDto
import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.data.remote.JobMatchDto
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.data.remote.JobsApi
import com.aiplacementos.app.data.remote.ParseJdRequestDto
import com.aiplacementos.app.domain.repository.JobRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class JobRepositoryImpl(private val jobsApi: JobsApi) : JobRepository {
    override suspend fun listJobDescriptions(): List<JobDescriptionListItemDto> =
        jobsApi.listJobDescriptions()

    override suspend fun parseJobDescription(
        rawText: String,
        idempotencyKey: String?,
    ): JobDescriptionParseResponseDto =
        jobsApi.createJobDescription(ParseJdRequestDto(rawText = rawText), idempotencyKey)

    override suspend fun uploadJobDescription(
        fileName: String,
        bytes: ByteArray,
        idempotencyKey: String?,
    ): JobDescriptionParseResponseDto {
        // Identical shape to ResumeRepositoryImpl.uploadResume -- same server-side multipart
        // handling, so there is no reason for the two to differ.
        val requestBody = bytes.toRequestBody("application/pdf".toMediaType())
        val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
        return jobsApi.uploadJobDescription(part, idempotencyKey)
    }

    override suspend fun matchJob(
        jdId: Int,
        idempotencyKey: String?,
    ): JobMatchDto = jobsApi.matchJob(jdId, idempotencyKey)

    override suspend fun generatePreparationPlan(
        jdId: Int,
        companyName: String?,
        jobTitle: String?,
        idempotencyKey: String?,
    ): JobPreparationPlanDto = jobsApi.generatePreparationPlan(
        jdId,
        GeneratePreparationPlanRequestDto(companyName = companyName, jobTitle = jobTitle),
        idempotencyKey,
    )

    override suspend fun getPreparationPlan(jdId: Int): JobPreparationPlanDto =
        jobsApi.getPreparationPlan(jdId)

    override suspend fun deleteJobDescription(jdId: Int) {
        jobsApi.deleteJobDescription(jdId)
    }
}
