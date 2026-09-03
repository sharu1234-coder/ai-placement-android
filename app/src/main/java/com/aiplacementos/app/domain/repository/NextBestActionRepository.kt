package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.RecommendedActionDto

interface NextBestActionRepository {
    suspend fun getNextBestAction(): RecommendedActionDto
}
