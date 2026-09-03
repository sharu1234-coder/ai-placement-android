package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.NextBestActionApi
import com.aiplacementos.app.data.remote.RecommendedActionDto
import com.aiplacementos.app.domain.repository.NextBestActionRepository

class NextBestActionRepositoryImpl(private val nextBestActionApi: NextBestActionApi) : NextBestActionRepository {
    override suspend fun getNextBestAction(): RecommendedActionDto = nextBestActionApi.getNextBestAction()
}
