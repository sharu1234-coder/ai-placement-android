package com.aiplacementos.app.domain.repository

import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.data.remote.SkillDto

interface ProfileRepository {
    suspend fun getRoles(): List<RoleDto>
    suspend fun getMyProfile(): ProfileDto
    suspend fun updateMyProfile(
        fullName: String?,
        mobileNumber: String? = null,
        college: String?,
        graduationYear: Int?,
        degree: String? = null,
        branch: String? = null,
        experienceLevel: String? = null,
        preparationGoals: List<String>? = null,
    ): ProfileDto
    suspend fun selectRole(roleId: Int?, customRole: String? = null): ProfileDto
    suspend fun selectSecondaryRole(roleId: Int): ProfileDto
    suspend fun completeOnboarding(): ProfileDto
    suspend fun addSkill(
        name: String,
        proficiency: String?,
        idempotencyKey: String? = null,
    ): SkillDto
    suspend fun listSkills(): List<SkillDto>
    suspend fun deleteSkill(id: Int)
    suspend fun deleteMyAccount(password: String)
}
