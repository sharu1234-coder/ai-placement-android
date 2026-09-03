package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.CareerTwinApi
import com.aiplacementos.app.data.remote.CareerTwinDto
import com.aiplacementos.app.data.remote.CareerTwinReadinessDto
import com.aiplacementos.app.data.remote.SkillTwinItemDto
import com.aiplacementos.app.domain.repository.CareerTwinRepository

class CareerTwinRepositoryImpl(private val careerTwinApi: CareerTwinApi) : CareerTwinRepository {
    override suspend fun getCareerTwin(): CareerTwinDto = careerTwinApi.getCareerTwin()
    override suspend fun getReadiness(): CareerTwinReadinessDto = careerTwinApi.getReadiness()
    override suspend fun getSkills(): List<SkillTwinItemDto> = careerTwinApi.getSkills()
    override suspend fun getSkill(skill: String): SkillTwinItemDto = careerTwinApi.getSkill(skill)
}
