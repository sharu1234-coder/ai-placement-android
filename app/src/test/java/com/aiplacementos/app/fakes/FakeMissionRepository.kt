package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.MissionDto
import com.aiplacementos.app.domain.repository.MissionRepository

class FakeMissionRepository : MissionRepository {
    var getTodayResult: (suspend () -> MissionDto)? = null
    var startResult: (suspend (Int) -> MissionDto)? = null
    var completeResult: (suspend (Int, Int?) -> MissionDto)? = null

    override suspend fun getToday(): MissionDto {
        val fn = requireNotNull(getTodayResult) { "getTodayResult not configured" }
        return fn()
    }

    override suspend fun start(missionId: Int): MissionDto {
        val fn = requireNotNull(startResult) { "startResult not configured" }
        return fn(missionId)
    }

    override suspend fun complete(missionId: Int, assessmentId: Int?): MissionDto {
        val fn = requireNotNull(completeResult) { "completeResult not configured" }
        return fn(missionId, assessmentId)
    }
}
