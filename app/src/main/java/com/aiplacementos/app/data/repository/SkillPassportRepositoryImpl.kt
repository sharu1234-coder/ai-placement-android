package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.SkillPassportApi
import com.aiplacementos.app.data.remote.SkillPassportItemDto
import com.aiplacementos.app.domain.repository.SkillPassportRepository

class SkillPassportRepositoryImpl(private val skillPassportApi: SkillPassportApi) : SkillPassportRepository {
    override suspend fun listSkills(): List<SkillPassportItemDto> = skillPassportApi.listSkills()
    override suspend fun getSkill(skill: String): SkillPassportItemDto = skillPassportApi.getSkill(skill)
}
