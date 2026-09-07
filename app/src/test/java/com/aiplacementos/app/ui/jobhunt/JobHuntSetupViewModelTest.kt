package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobSearchProfileDto
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.FakeResumeRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun profile(
    targetRoleTitle: String? = null,
    resumeId: Int? = null,
    isReady: Boolean = false,
) = JobSearchProfileDto(
    resumeId = resumeId,
    targetRoleTitle = targetRoleTitle,
    isReadyToSearch = isReady,
)

private fun resume(id: Int = 5) = ResumeSummaryDto(
    id = id,
    filename = "asha-cv.pdf",
    parseStatus = "completed",
    createdAt = "2026-09-01T10:00:00",
)

class JobHuntSetupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        jobHunt: FakeJobHuntRepository,
        resumes: FakeResumeRepository = FakeResumeRepository(),
    ) = JobHuntSetupViewModel(jobHunt, resumes)

    @Test
    fun `a role alone is enough to submit`() {
        // The role-only path is first-class, not a degraded fallback.
        val repository = FakeJobHuntRepository().apply { profileResult = { profile() } }
        val vm = viewModel(repository)
        vm.onTargetRoleChange("Software Engineer")

        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `a resume alone is enough to submit`() {
        val jobHunt = FakeJobHuntRepository().apply {
            profileResult = { profile(resumeId = 5) }
            roleSuggestionsResult = { emptyList() }
        }
        val resumes = FakeResumeRepository().apply { latestResumeResult = Result.success(resume()) }
        val vm = viewModel(jobHunt, resumes)

        assertTrue(vm.uiState.value.useResume)
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `neither a role nor a resume means nothing to search on`() {
        val repository = FakeJobHuntRepository().apply { profileResult = { profile() } }
        assertFalse(viewModel(repository).uiState.value.canSubmit)
    }

    @Test
    fun `a missing resume never blocks the role-only form`() {
        // Each source degrades independently -- a resume fetch failure must not strand a
        // student who only wants to type a role.
        val jobHunt = FakeJobHuntRepository().apply { profileResult = { profile() } }
        val resumes = FakeResumeRepository().apply { latestResumeResult = Result.failure(httpException(500)) }
        val vm = viewModel(jobHunt, resumes)
        vm.onTargetRoleChange("Data Analyst")

        assertTrue(vm.uiState.value.canSubmit)
        assertNull(vm.uiState.value.existingResumeId)
    }

    @Test
    fun `a failed profile load still leaves a usable empty form`() {
        val jobHunt = FakeJobHuntRepository().apply { profileResult = { throw httpException(500) } }
        val vm = viewModel(jobHunt)
        vm.onTargetRoleChange("Software Engineer")

        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `role suggestions are offered but never applied automatically`() {
        val jobHunt = FakeJobHuntRepository().apply {
            profileResult = { profile() }
            roleSuggestionsResult = { listOf("software_engineer") }
        }
        val resumes = FakeResumeRepository().apply { latestResumeResult = Result.success(resume()) }
        val vm = viewModel(jobHunt, resumes)

        assertEquals(listOf("software_engineer"), vm.uiState.value.roleSuggestions)
        // The student still has to choose one -- nothing was written to targetRole for them.
        assertEquals("", vm.uiState.value.targetRole)
    }

    @Test
    fun `a blank location is sent as null so it does not overwrite a stored preference`() {
        val jobHunt = FakeJobHuntRepository().apply {
            profileResult = { profile() }
            updateProfileResult = { profile(targetRoleTitle = "Software Engineer", isReady = true) }
        }
        val vm = viewModel(jobHunt)
        vm.onTargetRoleChange("Software Engineer")
        vm.submit()

        assertNull(jobHunt.lastProfileUpdate?.preferredLocation)
        assertEquals("Software Engineer", jobHunt.lastProfileUpdate?.targetRoleTitle)
    }

    @Test
    fun `unticking an existing resume clears it explicitly rather than by omission`() {
        val jobHunt = FakeJobHuntRepository().apply {
            profileResult = { profile(resumeId = 5) }
            roleSuggestionsResult = { emptyList() }
            updateProfileResult = { profile(targetRoleTitle = "Software Engineer", isReady = true) }
        }
        val resumes = FakeResumeRepository().apply { latestResumeResult = Result.success(resume()) }
        val vm = viewModel(jobHunt, resumes)

        vm.onTargetRoleChange("Software Engineer")
        vm.onUseResumeChange(false)
        vm.submit()

        assertTrue(jobHunt.lastProfileUpdate?.clearResume == true)
        assertNull(jobHunt.lastProfileUpdate?.resumeId)
    }

    @Test
    fun `a successful save signals navigation exactly once`() {
        val jobHunt = FakeJobHuntRepository().apply {
            profileResult = { profile() }
            updateProfileResult = { profile(targetRoleTitle = "SDE", isReady = true) }
        }
        val vm = viewModel(jobHunt)
        vm.onTargetRoleChange("SDE")
        vm.submit()

        assertTrue(vm.uiState.value.savedSuccessfully)
        vm.onNavigated()
        assertFalse(vm.uiState.value.savedSuccessfully)
    }

    @Test
    fun `a failed save reports the error and does not navigate`() {
        val jobHunt = FakeJobHuntRepository().apply {
            profileResult = { profile() }
            updateProfileResult = { throw httpException(500) }
        }
        val vm = viewModel(jobHunt)
        vm.onTargetRoleChange("SDE")
        vm.submit()

        assertFalse(vm.uiState.value.savedSuccessfully)
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `submitting with nothing to search on is a no-op rather than a bad request`() {
        val jobHunt = FakeJobHuntRepository().apply { profileResult = { profile() } }
        val vm = viewModel(jobHunt)
        vm.submit()

        assertNull(jobHunt.lastProfileUpdate)
    }
}
