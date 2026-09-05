package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.AtsCheckListItemDto
import com.aiplacementos.app.data.remote.AtsCheckReportDto
import com.aiplacementos.app.domain.repository.AtsCheckRepository
import kotlinx.coroutines.delay

class FakeAtsCheckRepository : AtsCheckRepository {
    var createResult: Result<AtsCheckReportDto>? = null
    var listResult: Result<List<AtsCheckListItemDto>> = Result.success(emptyList())
    var getResult: Result<AtsCheckReportDto>? = null

    // Only used by AtsCheckAnalyzingViewModelTest to control how the network call races
    // against the staged-loader's timer -- 0 elsewhere (default), i.e. resolves immediately.
    var createDelayMs: Long = 0

    var lastCreateResumeId: Int? = null
    var lastCreateTargetRole: String? = null
    var lastGetCheckId: Int? = null

    /** How many analyses were actually requested -- an ATS check is a real, billable
     * server-side AI run, so "exactly one per user intent" is the property under test. */
    var createCallCount: Int = 0
        private set

    override suspend fun createAtsCheck(resumeId: Int, targetRole: String?): AtsCheckReportDto {
        createCallCount++
        lastCreateResumeId = resumeId
        lastCreateTargetRole = targetRole
        if (createDelayMs > 0) delay(createDelayMs)
        return requireNotNull(createResult) { "createResult not configured" }.getOrThrow()
    }

    override suspend fun listAtsChecks(): List<AtsCheckListItemDto> = listResult.getOrThrow()

    override suspend fun getAtsCheck(checkId: Int): AtsCheckReportDto {
        lastGetCheckId = checkId
        return requireNotNull(getResult) { "getResult not configured" }.getOrThrow()
    }
}
