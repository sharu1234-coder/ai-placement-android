package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.SkillPassportItemDto

/** Deliberately independent of CareerTwinRepository -- both read Evidence Engine server-side,
 * but neither Android repository calls into the other. */
interface SkillPassportRepository {
    suspend fun listSkills(): List<SkillPassportItemDto>
    suspend fun getSkill(skill: String): SkillPassportItemDto
}
