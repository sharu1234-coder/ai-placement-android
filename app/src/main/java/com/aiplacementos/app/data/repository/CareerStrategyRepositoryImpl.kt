package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.CareerStrategyApi
import com.aiplacementos.app.data.remote.CareerStrategyDto
import com.aiplacementos.app.data.remote.JobStrategyDto
import com.aiplacementos.app.data.remote.SetStrategyTargetsRequest
import com.aiplacementos.app.data.remote.StrategyComparisonDto
import com.aiplacementos.app.data.remote.StrategyHistoryDto
import com.aiplacementos.app.data.remote.TargetComparisonDto
import com.aiplacementos.app.domain.repository.CareerStrategyRepository

class CareerStrategyRepositoryImpl(
    private val careerStrategyApi: CareerStrategyApi,
) : CareerStrategyRepository {
    override suspend fun getStrategy(): CareerStrategyDto = careerStrategyApi.getStrategy()
    override suspend fun getHistory(): StrategyHistoryDto = careerStrategyApi.getHistory()
    override suspend fun compare(snapshotId: Int): StrategyComparisonDto =
        careerStrategyApi.compare(snapshotId)
    override suspend fun getJobStrategy(listingId: Int): JobStrategyDto =
        careerStrategyApi.getJobStrategy(listingId)
    override suspend fun getTargets(): TargetComparisonDto = careerStrategyApi.getTargets()
    override suspend fun setTargets(roleIds: List<Int>): TargetComparisonDto =
        careerStrategyApi.setTargets(SetStrategyTargetsRequest(roleIds = roleIds))
}
