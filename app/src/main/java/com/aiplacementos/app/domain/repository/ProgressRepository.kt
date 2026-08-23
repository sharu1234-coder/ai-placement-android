package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ProgressDashboardDto

interface ProgressRepository {
    suspend fun getDashboard(): ProgressDashboardDto
}
