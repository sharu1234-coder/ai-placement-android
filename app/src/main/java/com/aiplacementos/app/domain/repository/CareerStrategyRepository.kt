package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.CareerStrategyDto
import com.aiplacementos.app.data.remote.JobStrategyDto
import com.aiplacementos.app.data.remote.StrategyComparisonDto
import com.aiplacementos.app.data.remote.StrategyHistoryDto
import com.aiplacementos.app.data.remote.TargetComparisonDto

/**
 * Read-only, like CareerTwinRepository and for the same reason: Career Strategy is an
 * orchestration layer over systems that already own their own data. It never writes evidence,
 * readiness, matches or recommendations -- the one thing it does write, a strategy snapshot,
 * the backend does for itself when the strategy actually changes.
 *
 * `setTargets` is the single exception and is still not an exception to that rule: it records
 * which roles the student wants compared, not any judgement about them.
 */
interface CareerStrategyRepository {
    suspend fun getStrategy(): CareerStrategyDto
    suspend fun getHistory(): StrategyHistoryDto
    suspend fun compare(snapshotId: Int): StrategyComparisonDto
    suspend fun getJobStrategy(listingId: Int): JobStrategyDto
    suspend fun getTargets(): TargetComparisonDto
    suspend fun setTargets(roleIds: List<Int>): TargetComparisonDto
}
