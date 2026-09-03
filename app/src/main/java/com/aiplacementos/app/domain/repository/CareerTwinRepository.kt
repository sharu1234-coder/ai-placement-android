package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.CareerTwinDto
import com.aiplacementos.app.data.remote.CareerTwinReadinessDto
import com.aiplacementos.app.data.remote.SkillTwinItemDto

/** Deliberately independent of CareerPathRepository/PlacementPathRepository -- Career Twin
 * only ever *reads* a composed view (backed server-side by Evidence Engine + Skill Graph +
 * whichever of Career Path/target role is active); it never writes to either. */
interface CareerTwinRepository {
    suspend fun getCareerTwin(): CareerTwinDto
    suspend fun getReadiness(): CareerTwinReadinessDto
    suspend fun getSkills(): List<SkillTwinItemDto>
    suspend fun getSkill(skill: String): SkillTwinItemDto
}
