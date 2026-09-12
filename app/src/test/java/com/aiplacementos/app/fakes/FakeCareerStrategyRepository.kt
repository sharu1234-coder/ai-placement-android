package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.CareerStrategyDto
import com.aiplacementos.app.data.remote.JobStrategyDto
import com.aiplacementos.app.data.remote.StrategyComparisonDto
import com.aiplacementos.app.data.remote.StrategyHistoryDto
import com.aiplacementos.app.data.remote.TargetComparisonDto
import com.aiplacementos.app.domain.repository.CareerStrategyRepository

class FakeCareerStrategyRepository : CareerStrategyRepository {
    var strategyResult: (suspend () -> CareerStrategyDto)? = null
    var historyResult: (suspend () -> StrategyHistoryDto)? = null
    var compareResult: (suspend (Int) -> StrategyComparisonDto)? = null
    var jobStrategyResult: (suspend (Int) -> JobStrategyDto)? = null
    var targetsResult: (suspend () -> TargetComparisonDto)? = null
    var setTargetsResult: (suspend (List<Int>) -> TargetComparisonDto)? = null

    var lastRequestedListingId: Int? = null
    var lastSetRoleIds: List<Int>? = null

    // Call counters. A screen that re-requests the same advert on every recomposition or resume
    // would still pass every assertion about rendered content, so the count is the only thing
    // that catches it.
    var strategyCallCount: Int = 0
    var jobStrategyCallCount: Int = 0

    override suspend fun getStrategy(): CareerStrategyDto {
        strategyCallCount++
        val fn = requireNotNull(strategyResult) { "strategyResult not configured" }
        return fn()
    }

    override suspend fun getHistory(): StrategyHistoryDto {
        val fn = requireNotNull(historyResult) { "historyResult not configured" }
        return fn()
    }

    override suspend fun compare(snapshotId: Int): StrategyComparisonDto {
        val fn = requireNotNull(compareResult) { "compareResult not configured" }
        return fn(snapshotId)
    }

    override suspend fun getJobStrategy(listingId: Int): JobStrategyDto {
        jobStrategyCallCount++
        lastRequestedListingId = listingId
        val fn = requireNotNull(jobStrategyResult) { "jobStrategyResult not configured" }
        return fn(listingId)
    }

    override suspend fun getTargets(): TargetComparisonDto {
        val fn = requireNotNull(targetsResult) { "targetsResult not configured" }
        return fn()
    }

    override suspend fun setTargets(roleIds: List<Int>): TargetComparisonDto {
        lastSetRoleIds = roleIds
        val fn = requireNotNull(setTargetsResult) { "setTargetsResult not configured" }
        return fn(roleIds)
    }
}
