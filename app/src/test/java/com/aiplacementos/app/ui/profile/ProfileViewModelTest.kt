package com.aiplacementos.app.ui.profile

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ProfileDto
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.data.remote.SkillDto
import com.aiplacementos.app.fakes.FakeProfileRepository
import com.aiplacementos.app.fakes.FakeResumeRepository
import com.aiplacementos.app.ui.common.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the profile, skills, and resume on init`() {
        val profile = testProfile(fullName = "Jane Doe", college = "IIT Bombay", graduationYear = 2027)
        val profileRepository = FakeProfileRepository().apply {
            profileResult = Result.success(profile)
            skillsResult = Result.success(listOf(SkillDto(id = 1, name = "Python")))
        }
        val resumeRepository = FakeResumeRepository().apply {
            latestResumeResult = Result.success(ResumeSummaryDto(1, "resume.pdf", "completed", "2026-01-01T00:00:00Z"))
        }

        val viewModel = ProfileViewModel(profileRepository, resumeRepository)

        assertEquals(UiState.Success(profile), viewModel.uiState.value)
        assertEquals(1, viewModel.skills.value.size)
        assertEquals("resume.pdf", viewModel.resume.value?.filename)
    }

    @Test
    fun `a load failure surfaces a connectivity message`() {
        val profileRepository = FakeProfileRepository().apply {
            profileResult = Result.failure(java.io.IOException("no network"))
        }

        val viewModel = ProfileViewModel(profileRepository, FakeResumeRepository())

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
    }

    @Test
    fun `savePersonalAndEducation sends only personal and education fields`() {
        val profileRepository = FakeProfileRepository()
        val viewModel = ProfileViewModel(profileRepository, FakeResumeRepository())

        viewModel.savePersonalAndEducation("Jane Doe", "+91 98765", "MIT", "B.Tech", "CS", 2028)

        val args = profileRepository.lastUpdateProfileArgs
        assertEquals("Jane Doe", args?.fullName)
        assertEquals("+91 98765", args?.mobileNumber)
        assertEquals("MIT", args?.college)
        assertEquals("B.Tech", args?.degree)
        assertEquals("CS", args?.branch)
        assertEquals(2028, args?.graduationYear)
        assertNull(args?.experienceLevel)
        assertNull(args?.preparationGoals)
    }

    @Test
    fun `a save failure keeps the loaded profile intact and surfaces a banner instead of blanking the screen`() {
        val profile = testProfile(fullName = "Jane Doe", college = "IIT Bombay", graduationYear = 2027)
        val profileRepository = FakeProfileRepository().apply {
            profileResult = Result.success(profile)
            updateProfileResult = Result.failure(java.io.IOException("no network"))
        }
        val viewModel = ProfileViewModel(profileRepository, FakeResumeRepository())

        viewModel.savePersonalAndEducation("New Name", null, null, null, null, null)

        // The screen must NOT flip to UiState.Error on a save failure -- that would discard
        // whatever the student was mid-editing in every other section (the bug this pass
        // fixes). The originally-loaded profile stays exactly as it was.
        assertEquals(UiState.Success(profile), viewModel.uiState.value)
        assertEquals(
            "Couldn't reach the server. Check your connection and try again.",
            viewModel.saveError.value,
        )
    }

    @Test
    fun `savePreparationGoals sends only the goals field`() {
        val profileRepository = FakeProfileRepository()
        val viewModel = ProfileViewModel(profileRepository, FakeResumeRepository())

        viewModel.savePreparationGoals(listOf("DSA", "SQL"))

        val args = profileRepository.lastUpdateProfileArgs
        assertEquals(listOf("DSA", "SQL"), args?.preparationGoals)
        assertNull(args?.fullName)
        assertNull(args?.college)
    }

    @Test
    fun `saveExperienceLevel sends only the experience level field`() {
        val profileRepository = FakeProfileRepository()
        val viewModel = ProfileViewModel(profileRepository, FakeResumeRepository())

        viewModel.saveExperienceLevel("advanced")

        val args = profileRepository.lastUpdateProfileArgs
        assertEquals("advanced", args?.experienceLevel)
        assertNull(args?.fullName)
        assertNull(args?.preparationGoals)
    }

    @Test
    fun `toggleSkill adds a skill not already present`() {
        val profileRepository = FakeProfileRepository().apply {
            skillsResult = Result.success(emptyList())
            addSkillResult = Result.success(SkillDto(id = 5, name = "Kotlin"))
        }
        val viewModel = ProfileViewModel(profileRepository, FakeResumeRepository())
        profileRepository.skillsResult = Result.success(listOf(SkillDto(id = 5, name = "Kotlin")))

        viewModel.toggleSkill("Kotlin")

        assertEquals("Kotlin" to null, profileRepository.lastAddSkillArgs)
        assertEquals(listOf(SkillDto(id = 5, name = "Kotlin")), viewModel.skills.value)
    }

    @Test
    fun `toggleSkill removes a skill already present`() {
        val profileRepository = FakeProfileRepository().apply {
            skillsResult = Result.success(listOf(SkillDto(id = 5, name = "Kotlin")))
        }
        val viewModel = ProfileViewModel(profileRepository, FakeResumeRepository())
        profileRepository.skillsResult = Result.success(emptyList())

        viewModel.toggleSkill("Kotlin")

        assertEquals(5, profileRepository.lastDeletedSkillId)
        assertEquals(emptyList<SkillDto>(), viewModel.skills.value)
    }

    @Test
    fun `removeResume deletes and clears the resume state`() {
        val resumeRepository = FakeResumeRepository().apply {
            latestResumeResult = Result.success(ResumeSummaryDto(9, "old.pdf", "completed", "2026-01-01T00:00:00Z"))
        }
        val viewModel = ProfileViewModel(FakeProfileRepository(), resumeRepository)

        viewModel.removeResume(9)

        assertEquals(9, resumeRepository.lastDeletedResumeId)
        assertNull(viewModel.resume.value)
    }
}

private fun testProfile(
    fullName: String? = null,
    college: String? = null,
    graduationYear: Int? = null,
) = ProfileDto(
    id = 1,
    fullName = fullName,
    email = "student@example.com",
    mobileNumber = null,
    targetRoleId = null,
    secondaryTargetRoleId = null,
    customTargetRole = null,
    college = college,
    graduationYear = graduationYear,
    degree = null,
    branch = null,
    experienceLevel = null,
    preparationGoals = null,
    onboardingCompleted = false,
)
