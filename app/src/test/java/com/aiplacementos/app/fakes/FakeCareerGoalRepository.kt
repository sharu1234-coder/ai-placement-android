package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.CareerGoalAnalyzeResponseDto
import com.aiplacementos.app.data.remote.CareerGoalDetailDto
import com.aiplacementos.app.data.remote.CareerGoalListItemDto
import com.aiplacementos.app.domain.repository.CareerGoalRepository

class FakeCareerGoalRepository : CareerGoalRepository {
    var listGoalsResult: (suspend () -> List<CareerGoalListItemDto>)? = null
    var createGoalResult: (suspend () -> CareerGoalListItemDto)? = null
    var getGoalResult: (suspend (Int) -> CareerGoalDetailDto)? = null
    var setStatusResult: (suspend (Int, String) -> CareerGoalListItemDto)? = null
    var deleteGoalResult: (suspend (Int) -> Unit)? = null
    var analyzeGoalResult: (suspend (Int) -> CareerGoalAnalyzeResponseDto)? = null
    var linkApplicationResult: (suspend (Int, Int) -> CareerGoalListItemDto)? = null
    var unlinkApplicationResult: (suspend (Int) -> CareerGoalListItemDto)? = null

    // What actually reached the repository, so a test can assert on it rather than on the
    // ViewModel's own report of what it did.
    var createdLocation: String? = null
    var createCalls = 0
    var analyzeCalls = 0
    var lastStatus: String? = null
    var deleteCalls = 0
    var linkedSavedJobId: Int? = null
    var linkCalls = 0
    var unlinkCalls = 0

    override suspend fun listGoals(): List<CareerGoalListItemDto> {
        val fn = requireNotNull(listGoalsResult) { "listGoalsResult not configured" }
        return fn()
    }

    override suspend fun createGoal(
        title: String,
        company: String,
        location: String?,
        jobDescription: String,
    ): CareerGoalListItemDto {
        createCalls += 1
        createdLocation = location
        val fn = requireNotNull(createGoalResult) { "createGoalResult not configured" }
        return fn()
    }

    override suspend fun getGoal(careerGoalId: Int): CareerGoalDetailDto {
        val fn = requireNotNull(getGoalResult) { "getGoalResult not configured" }
        return fn(careerGoalId)
    }

    override suspend fun setStatus(careerGoalId: Int, status: String): CareerGoalListItemDto {
        lastStatus = status
        val fn = requireNotNull(setStatusResult) { "setStatusResult not configured" }
        return fn(careerGoalId, status)
    }

    override suspend fun deleteGoal(careerGoalId: Int) {
        deleteCalls += 1
        deleteGoalResult?.invoke(careerGoalId)
    }

    override suspend fun analyzeGoal(careerGoalId: Int): CareerGoalAnalyzeResponseDto {
        analyzeCalls += 1
        val fn = requireNotNull(analyzeGoalResult) { "analyzeGoalResult not configured" }
        return fn(careerGoalId)
    }

    override suspend fun linkApplication(careerGoalId: Int, savedJobId: Int): CareerGoalListItemDto {
        linkCalls += 1
        linkedSavedJobId = savedJobId
        val fn = requireNotNull(linkApplicationResult) { "linkApplicationResult not configured" }
        return fn(careerGoalId, savedJobId)
    }

    override suspend fun unlinkApplication(careerGoalId: Int): CareerGoalListItemDto {
        unlinkCalls += 1
        val fn = requireNotNull(unlinkApplicationResult) { "unlinkApplicationResult not configured" }
        return fn(careerGoalId)
    }
}
