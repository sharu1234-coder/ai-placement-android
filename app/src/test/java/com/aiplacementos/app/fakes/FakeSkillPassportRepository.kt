package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.SkillPassportItemDto
import com.aiplacementos.app.domain.repository.SkillPassportRepository

class FakeSkillPassportRepository : SkillPassportRepository {
    var listSkillsResult: (suspend () -> List<SkillPassportItemDto>)? = null
    var getSkillResult: (suspend (String) -> SkillPassportItemDto)? = null

    var lastRequestedSkill: String? = null

    override suspend fun listSkills(): List<SkillPassportItemDto> {
        val fn = requireNotNull(listSkillsResult) { "listSkillsResult not configured" }
        return fn()
    }

    override suspend fun getSkill(skill: String): SkillPassportItemDto {
        lastRequestedSkill = skill
        val fn = requireNotNull(getSkillResult) { "getSkillResult not configured" }
        return fn(skill)
    }
}
