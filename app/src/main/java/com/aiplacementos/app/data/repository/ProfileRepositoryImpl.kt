package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.AccountDeleteRequestDto
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.data.remote.RoleSelectRequestDto
import com.aiplacementos.app.data.remote.SecondaryRoleSelectRequestDto
import com.aiplacementos.app.data.remote.SkillCreateDto
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.data.remote.ProfileUpsertDto
import com.aiplacementos.app.data.remote.UsersApi
import com.aiplacementos.app.domain.repository.ProfileRepository

class ProfileRepositoryImpl(private val usersApi: UsersApi) : ProfileRepository {
    override suspend fun getRoles(): List<RoleDto> = usersApi.getRoles()

    override suspend fun getMyProfile(): ProfileDto = usersApi.getMyProfile()

    override suspend fun updateMyProfile(
        fullName: String?,
        mobileNumber: String?,
        college: String?,
        graduationYear: Int?,
        degree: String?,
        branch: String?,
        experienceLevel: String?,
        preparationGoals: List<String>?,
    ): ProfileDto = usersApi.updateMyProfile(
        ProfileUpsertDto(
            fullName = fullName,
            mobileNumber = mobileNumber,
            college = college,
            graduationYear = graduationYear,
            degree = degree,
            branch = branch,
            experienceLevel = experienceLevel,
            preparationGoals = preparationGoals,
        )
    )

    override suspend fun selectRole(roleId: Int?, customRole: String?): ProfileDto =
        usersApi.selectRole(RoleSelectRequestDto(roleId = roleId, customRole = customRole))

    override suspend fun selectSecondaryRole(roleId: Int): ProfileDto =
        usersApi.selectSecondaryRole(SecondaryRoleSelectRequestDto(roleId))

    override suspend fun completeOnboarding(): ProfileDto = usersApi.completeOnboarding()

    override suspend fun addSkill(
        name: String,
        proficiency: String?,
        idempotencyKey: String?,
    ): SkillDto =
        usersApi.addSkill(SkillCreateDto(name = name, proficiency = proficiency), idempotencyKey)

    override suspend fun listSkills(): List<SkillDto> = usersApi.listSkills()

    override suspend fun deleteSkill(id: Int) {
        usersApi.deleteSkill(id)
    }

    override suspend fun deleteMyAccount(password: String) {
        usersApi.deleteMyAccount(AccountDeleteRequestDto(password = password))
    }
}
