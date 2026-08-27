package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.data.remote.JobMatchDto
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.data.remote.RequirementDto

interface JobRepository {
    suspend fun listJobDescriptions(): List<JobDescriptionListItemDto>
    suspend fun parseJobDescription(rawText: String): JobDescriptionParseResponseDto
    suspend fun getRequirements(jdId: Int): List<RequirementDto>
    suspend fun matchJob(jdId: Int): JobMatchDto
    suspend fun generatePreparationPlan(jdId: Int, companyName: String?, jobTitle: String?): JobPreparationPlanDto
    suspend fun getPreparationPlan(jdId: Int): JobPreparationPlanDto
    suspend fun deleteJobDescription(jdId: Int)
}
