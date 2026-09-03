package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.CareerTwinDto
import com.aiplacementos.app.data.remote.CareerTwinReadinessDto
import com.aiplacementos.app.data.remote.SkillTwinItemDto
import com.aiplacementos.app.domain.repository.CareerTwinRepository

class FakeCareerTwinRepository : CareerTwinRepository {
    var careerTwinResult: (suspend () -> CareerTwinDto)? = null
    var readinessResult: (suspend () -> CareerTwinReadinessDto)? = null
    var skillsResult: (suspend () -> List<SkillTwinItemDto>)? = null
    var skillResult: (suspend (String) -> SkillTwinItemDto)? = null

    var lastRequestedSkill: String? = null

    override suspend fun getCareerTwin(): CareerTwinDto {
        val fn = requireNotNull(careerTwinResult) { "careerTwinResult not configured" }
        return fn()
    }

    override suspend fun getReadiness(): CareerTwinReadinessDto {
        val fn = requireNotNull(readinessResult) { "readinessResult not configured" }
        return fn()
    }

    override suspend fun getSkills(): List<SkillTwinItemDto> {
        val fn = requireNotNull(skillsResult) { "skillsResult not configured" }
        return fn()
    }

    override suspend fun getSkill(skill: String): SkillTwinItemDto {
        lastRequestedSkill = skill
        val fn = requireNotNull(skillResult) { "skillResult not configured" }
        return fn(skill)
    }
}
