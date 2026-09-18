package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.data.remote.JobMatchDto
import com.aiplacementos.app.data.remote.JobPreparationPlanDto

interface JobRepository {
    suspend fun listJobDescriptions(): List<JobDescriptionListItemDto>
    suspend fun parseJobDescription(rawText: String): JobDescriptionParseResponseDto

    /** Analyse a job description the student has as a PDF. Produces the same result as
     * parseJobDescription -- the file is only a delivery mechanism and is never stored. */
    suspend fun uploadJobDescription(fileName: String, bytes: ByteArray): JobDescriptionParseResponseDto
    suspend fun matchJob(jdId: Int): JobMatchDto
    suspend fun generatePreparationPlan(jdId: Int, companyName: String?, jobTitle: String?): JobPreparationPlanDto
    suspend fun getPreparationPlan(jdId: Int): JobPreparationPlanDto
    suspend fun deleteJobDescription(jdId: Int)
}
