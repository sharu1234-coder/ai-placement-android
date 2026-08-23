package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.domain.repository.ProfileRepository

class FakeProfileRepository : ProfileRepository {
    var rolesResult: Result<List<RoleDto>> = Result.success(emptyList())
    var profileResult: Result<ProfileDto> =
        Result.success(ProfileDto(id = 1, fullName = null, targetRoleId = null, college = null, graduationYear = null))
    var updateProfileResult: Result<ProfileDto>? = null
    var selectRoleResult: Result<ProfileDto>? = null
    var addSkillResult: Result<SkillDto> = Result.success(SkillDto(id = 1, name = "Python"))
    var skillsResult: Result<List<SkillDto>> = Result.success(emptyList())
    var deleteAccountResult: Result<Unit> = Result.success(Unit)

    var lastUpdateProfileArgs: Triple<String?, String?, Int?>? = null
    var lastDeleteAccountPassword: String? = null

    override suspend fun getRoles(): List<RoleDto> = rolesResult.getOrThrow()

    override suspend fun getMyProfile(): ProfileDto = profileResult.getOrThrow()

    override suspend fun updateMyProfile(fullName: String?, college: String?, graduationYear: Int?): ProfileDto {
        lastUpdateProfileArgs = Triple(fullName, college, graduationYear)
        return (updateProfileResult ?: profileResult).getOrThrow()
    }

    override suspend fun selectRole(roleId: Int): ProfileDto = (selectRoleResult ?: profileResult).getOrThrow()

    override suspend fun addSkill(name: String, proficiency: String?): SkillDto = addSkillResult.getOrThrow()

    override suspend fun listSkills(): List<SkillDto> = skillsResult.getOrThrow()

    override suspend fun deleteMyAccount(password: String) {
        lastDeleteAccountPassword = password
        deleteAccountResult.getOrThrow()
    }
}
