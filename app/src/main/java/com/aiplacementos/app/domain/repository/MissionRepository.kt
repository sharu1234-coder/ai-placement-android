package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.MissionDto

interface MissionRepository {
    suspend fun getToday(): MissionDto
    suspend fun start(missionId: Int): MissionDto
    suspend fun complete(missionId: Int, assessmentId: Int? = null): MissionDto
}
