package com.aiplacementos.app.navigation

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.NextBestActionDto
import com.aiplacementos.app.data.remote.PlacementPathDto
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.RoleDto
import com.aiplacementos.app.fakes.FakePlacementPathRepository
import com.aiplacementos.app.fakes.FakeProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private fun profile(fullName: String?, targetRoleId: Int?, customTargetRole: String?) = ProfileDto(
    id = 1,
    fullName = fullName,
    email = "student@example.com",
    mobileNumber = null,
    targetRoleId = targetRoleId,
    secondaryTargetRoleId = null,
    customTargetRole = customTargetRole,
    college = null,
    graduationYear = null,
    degree = null,
    branch = null,
    experienceLevel = null,
    preparationGoals = null,
    onboardingCompleted = true,
)

class MainScreenViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `resolves the catalog role name and roadmap progress`() {
        val profileRepository = FakeProfileRepository().apply {
            profileResult = Result.success(profile(fullName = "Asha Rao", targetRoleId = 5, customTargetRole = null))
            rolesResult = Result.success(listOf(RoleDto(id = 5, name = "SDE", category = "Engineering")))
        }
        val pathRepository = FakePlacementPathRepository().apply {
            currentResult = {
                PlacementPathDto(
                    id = 1, status = "active", createdAt = "2026-08-01T00:00:00Z", completedTopics = 5, totalTopics = 10,
                    stages = emptyList(),
                    nextBestAction = NextBestActionDto(topicId = 1, title = "SQL Basics", reason = "Start here."),
                )
            }
        }

        val viewModel = MainScreenViewModel(profileRepository, pathRepository)

        val header = viewModel.headerData.value
        assertEquals("Asha Rao", header?.fullName)
        assertEquals("SDE", header?.targetRoleName)
        assertEquals(50, header?.roadmapProgressPercent)
    }

    @Test
    fun `a custom target role is used as-is without a catalog lookup`() {
        val profileRepository = FakeProfileRepository().apply {
            profileResult = Result.success(
                profile(fullName = "Asha Rao", targetRoleId = null, customTargetRole = "Bioinformatics Engineer")
            )
        }
        val pathRepository = FakePlacementPathRepository().apply { currentResult = { null } }

        val viewModel = MainScreenViewModel(profileRepository, pathRepository)

        assertEquals("Bioinformatics Engineer", viewModel.headerData.value?.targetRoleName)
    }

    @Test
    fun `a load failure degrades to a blank header rather than crashing`() {
        val profileRepository = FakeProfileRepository().apply {
            profileResult = Result.failure(java.io.IOException("boom"))
        }
        val pathRepository = FakePlacementPathRepository().apply { currentResult = { null } }

        val viewModel = MainScreenViewModel(profileRepository, pathRepository)

        val header = viewModel.headerData.value
        assertNull(header?.fullName)
        assertNull(header?.targetRoleName)
    }
}
