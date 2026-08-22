package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.data.remote.SkillDto

interface ProfileRepository {
    suspend fun getRoles(): List<RoleDto>
    suspend fun getMyProfile(): ProfileDto
    suspend fun updateMyProfile(fullName: String?, college: String?, graduationYear: Int?): ProfileDto
    suspend fun selectRole(roleId: Int): ProfileDto
    suspend fun addSkill(name: String, proficiency: String?): SkillDto
    suspend fun listSkills(): List<SkillDto>
}
