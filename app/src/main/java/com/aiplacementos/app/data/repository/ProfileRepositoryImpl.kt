package com.aiplacementos.app.data.repository

import com.aiplacementos.app.data.remote.AccountDeleteRequestDto
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.data.remote.RoleSelectRequestDto
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
        college: String?,
        graduationYear: Int?,
    ): ProfileDto = usersApi.updateMyProfile(
        ProfileUpsertDto(fullName = fullName, college = college, graduationYear = graduationYear)
    )

    override suspend fun selectRole(roleId: Int): ProfileDto =
        usersApi.selectRole(RoleSelectRequestDto(roleId))

    override suspend fun addSkill(name: String, proficiency: String?): SkillDto =
        usersApi.addSkill(SkillCreateDto(name = name, proficiency = proficiency))

    override suspend fun listSkills(): List<SkillDto> = usersApi.listSkills()

    override suspend fun deleteMyAccount(password: String) {
        usersApi.deleteMyAccount(AccountDeleteRequestDto(password = password))
    }
}
