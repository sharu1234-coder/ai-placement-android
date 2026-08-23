package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.ProgressApi
import com.aiplacementos.app.data.remote.ProgressDashboardDto
import com.aiplacementos.app.domain.repository.ProgressRepository

class ProgressRepositoryImpl(private val progressApi: ProgressApi) : ProgressRepository {
    override suspend fun getDashboard(): ProgressDashboardDto = progressApi.getDashboard()
}
