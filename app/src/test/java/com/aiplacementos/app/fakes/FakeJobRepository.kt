package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.data.remote.JobMatchDto
import com.aiplacementos.app.data.remote.JobPreparationPlanDto
import com.aiplacementos.app.domain.repository.JobRepository

class FakeJobRepository : JobRepository {
    var listResult: (suspend () -> List<JobDescriptionListItemDto>)? = null
    var parseResult: (suspend (String) -> JobDescriptionParseResponseDto)? = null
    var matchResult: (suspend (Int) -> JobMatchDto)? = null
    var generatePlanResult: (suspend (Int, String?, String?) -> JobPreparationPlanDto)? = null
    var getPlanResult: (suspend (Int) -> JobPreparationPlanDto)? = null
    var deleteResult: (suspend (Int) -> Unit)? = null

    var lastParsedText: String? = null
    var lastGeneratePlanArgs: Triple<Int, String?, String?>? = null
    var lastGetPlanJdId: Int? = null
    var lastDeletedJdId: Int? = null

    override suspend fun listJobDescriptions(): List<JobDescriptionListItemDto> {
        val fn = requireNotNull(listResult) { "listResult not configured" }
        return fn()
    }

    override suspend fun parseJobDescription(rawText: String): JobDescriptionParseResponseDto {
        lastParsedText = rawText
        val fn = requireNotNull(parseResult) { "parseResult not configured" }
        return fn(rawText)
    }

    override suspend fun matchJob(jdId: Int): JobMatchDto {
        val fn = requireNotNull(matchResult) { "matchResult not configured" }
        return fn(jdId)
    }

    override suspend fun generatePreparationPlan(jdId: Int, companyName: String?, jobTitle: String?): JobPreparationPlanDto {
        lastGeneratePlanArgs = Triple(jdId, companyName, jobTitle)
        val fn = requireNotNull(generatePlanResult) { "generatePlanResult not configured" }
        return fn(jdId, companyName, jobTitle)
    }

    override suspend fun getPreparationPlan(jdId: Int): JobPreparationPlanDto {
        lastGetPlanJdId = jdId
        val fn = requireNotNull(getPlanResult) { "getPlanResult not configured" }
        return fn(jdId)
    }

    override suspend fun deleteJobDescription(jdId: Int) {
        lastDeletedJdId = jdId
        val fn = requireNotNull(deleteResult) { "deleteResult not configured" }
        fn(jdId)
    }
}
