package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.data.remote.JobMatchDto
import com.aiplacementos.app.data.remote.RequirementDto

interface JobRepository {
    suspend fun parseJobDescription(rawText: String): JobDescriptionParseResponseDto
    suspend fun getRequirements(jdId: Int): List<RequirementDto>
    suspend fun matchJob(jdId: Int): JobMatchDto
}
