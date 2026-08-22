package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.data.remote.JobMatchDto
import com.aiplacementos.app.data.remote.JobsApi
import com.aiplacementos.app.data.remote.ParseJdRequestDto
import com.aiplacementos.app.data.remote.RequirementDto
import com.aiplacementos.app.domain.repository.JobRepository

class JobRepositoryImpl(private val jobsApi: JobsApi) : JobRepository {
    override suspend fun parseJobDescription(rawText: String): JobDescriptionParseResponseDto =
        jobsApi.createJobDescription(ParseJdRequestDto(rawText = rawText))

    override suspend fun getRequirements(jdId: Int): List<RequirementDto> =
        jobsApi.getRequirements(jdId)

    override suspend fun matchJob(jdId: Int): JobMatchDto = jobsApi.matchJob(jdId)
}
