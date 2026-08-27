package com.aiplacementos.app.fakes

import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.domain.repository.ProfileRepository

class FakeProfileRepository : ProfileRepository {
    var rolesResult: Result<List<RoleDto>> = Result.success(emptyList())
    var profileResult: Result<ProfileDto> = Result.success(
        ProfileDto(
            id = 1,
            fullName = null,
            email = "student@example.com",
            mobileNumber = null,
            targetRoleId = null,
            secondaryTargetRoleId = null,
            customTargetRole = null,
            college = null,
            graduationYear = null,
            degree = null,
            branch = null,
            experienceLevel = null,
            preparationGoals = null,
            onboardingCompleted = false,
        )
    )
    var updateProfileResult: Result<ProfileDto>? = null
    var selectRoleResult: Result<ProfileDto>? = null
    var selectSecondaryRoleResult: Result<ProfileDto>? = null
    var completeOnboardingResult: Result<ProfileDto>? = null
    var addSkillResult: Result<SkillDto> = Result.success(SkillDto(id = 1, name = "Python"))
    var skillsResult: Result<List<SkillDto>> = Result.success(emptyList())
    var deleteSkillResult: Result<Unit> = Result.success(Unit)
    var deleteAccountResult: Result<Unit> = Result.success(Unit)

    data class UpdateProfileArgs(
        val fullName: String?,
        val mobileNumber: String?,
        val college: String?,
        val graduationYear: Int?,
        val degree: String?,
        val branch: String?,
        val experienceLevel: String?,
        val preparationGoals: List<String>?,
    )

    var lastUpdateProfileArgs: UpdateProfileArgs? = null
    var lastSelectRoleArgs: Pair<Int?, String?>? = null
    var lastSelectSecondaryRoleId: Int? = null
    var lastAddSkillArgs: Pair<String, String?>? = null
    var lastDeletedSkillId: Int? = null
    var lastDeleteAccountPassword: String? = null

    override suspend fun getRoles(): List<RoleDto> = rolesResult.getOrThrow()

    override suspend fun getMyProfile(): ProfileDto = profileResult.getOrThrow()

    override suspend fun updateMyProfile(
        fullName: String?,
        mobileNumber: String?,
        college: String?,
        graduationYear: Int?,
        degree: String?,
        branch: String?,
        experienceLevel: String?,
        preparationGoals: List<String>?,
    ): ProfileDto {
        lastUpdateProfileArgs = UpdateProfileArgs(
            fullName, mobileNumber, college, graduationYear, degree, branch, experienceLevel, preparationGoals
        )
        return (updateProfileResult ?: profileResult).getOrThrow()
    }

    override suspend fun selectRole(roleId: Int?, customRole: String?): ProfileDto {
        lastSelectRoleArgs = roleId to customRole
        return (selectRoleResult ?: profileResult).getOrThrow()
    }

    override suspend fun selectSecondaryRole(roleId: Int): ProfileDto {
        lastSelectSecondaryRoleId = roleId
        return (selectSecondaryRoleResult ?: profileResult).getOrThrow()
    }

    override suspend fun completeOnboarding(): ProfileDto = (completeOnboardingResult ?: profileResult).getOrThrow()

    override suspend fun addSkill(name: String, proficiency: String?): SkillDto {
        lastAddSkillArgs = name to proficiency
        return addSkillResult.getOrThrow()
    }

    override suspend fun listSkills(): List<SkillDto> = skillsResult.getOrThrow()

    override suspend fun deleteSkill(id: Int) {
        lastDeletedSkillId = id
        deleteSkillResult.getOrThrow()
    }

    override suspend fun deleteMyAccount(password: String) {
        lastDeleteAccountPassword = password
        deleteAccountResult.getOrThrow()
    }
}
