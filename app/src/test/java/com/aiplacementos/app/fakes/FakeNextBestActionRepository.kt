package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.RecommendedActionDto
import com.aiplacementos.app.domain.repository.NextBestActionRepository

class FakeNextBestActionRepository : NextBestActionRepository {
    var result: (suspend () -> RecommendedActionDto)? = null

    override suspend fun getNextBestAction(): RecommendedActionDto {
        val fn = requireNotNull(result) { "result not configured" }
        return fn()
    }
}
