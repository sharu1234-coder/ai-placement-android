package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.CompleteMissionRequestDto
import com.aiplacementos.app.data.remote.MissionApi
import com.aiplacementos.app.data.remote.MissionDto
import com.aiplacementos.app.domain.repository.MissionRepository

class MissionRepositoryImpl(private val missionApi: MissionApi) : MissionRepository {
    override suspend fun getToday(): MissionDto = missionApi.getToday()
    override suspend fun start(missionId: Int): MissionDto = missionApi.start(missionId)
    override suspend fun complete(missionId: Int, assessmentId: Int?): MissionDto =
        missionApi.complete(missionId, CompleteMissionRequestDto(assessmentId))
}
