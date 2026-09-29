package com.aiplacementos.app.ui.careergoal

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.CareerGoalDetailDto
import com.aiplacementos.app.data.remote.CareerGoalListItemDto
import com.aiplacementos.app.data.remote.GoalApplicationDto
import com.aiplacementos.app.data.remote.GoalProgressDto
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.fakes.FakeCareerGoalRepository
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.ui.jobhunt.sampleListing
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Choosing which tracked application a Career Goal is about.
 *
 * Phase 2E built `linkApplication` end to end -- the endpoint, the repository, the ViewModel
 * call, and its handling of every refusal the server gives (someone else's job, a job another
 * goal already claimed, no network). Nothing in the app ever called it. A student could unlink
 * an application but had no way to link one, so the outcome loop the whole product is built
 * around could only be entered by editing the database.
 *
 * These tests cover the tap that was missing, and the two things it must not do: claim the
 * tracker is empty when the request simply failed, and leave a stale list on screen after a
 * link succeeds.
 */
class CareerGoalLinkPickerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun savedJob(id: Int, status: String = "saved", title: String = "Data Engineer") =
        SavedJobDto(
            id = id,
            status = status,
            createdAt = "2026-09-07T10:00:00",
            updatedAt = "2026-09-07T10:00:00",
            listing = sampleListing(id = id, title = title),
        )

    private fun listItem(applicationStatus: String?) = CareerGoalListItemDto(
        id = 1,
        title = "Data Engineer",
        company = "Contoso",
        location = null,
        status = "active",
        isAnalyzed = true,
        preparationState = "in_progress",
        applicationStatus = applicationStatus,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    private fun detail(application: GoalApplicationDto?) = CareerGoalDetailDto(
        id = 1,
        title = "Data Engineer",
        company = "Contoso",
        location = null,
        jobDescription = "Required: SQL.",
        status = "active",
        isAnalyzed = true,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        application = application,
        progress = GoalProgressDto(state = "in_progress"),
    )

    private fun application(status: String) = GoalApplicationDto(
        savedJobId = 7,
        status = status,
        listingTitle = "Data Engineer",
    )

    private fun viewModel(
        goal: CareerGoalDetailDto = detail(null),
        saved: suspend (List<String>?) -> List<SavedJobDto> = { listOf(savedJob(7)) },
        onLink: (suspend (Int, Int) -> CareerGoalListItemDto)? = null,
    ): CareerGoalDetailViewModel {
        val goals = FakeCareerGoalRepository().apply {
            getGoalResult = { goal }
            linkApplicationResult = onLink ?: { _, _ -> listItem("saved") }
        }
        val jobs = FakeJobHuntRepository().apply { listSavedResult = saved }
        return CareerGoalDetailViewModel(1, goals, jobs)
    }

    @Test
    fun `the picker is not fetched until the student asks for it`() {
        // Most visits to this screen are not about linking. Fetching everyone's job list on
        // every open would make the common case pay for the rare one.
        val viewModel = viewModel()

        assertNull(viewModel.linkableJobs.value)
    }

    @Test
    fun `asking to link lists the student's own tracked jobs`() {
        val viewModel = viewModel(saved = { listOf(savedJob(7), savedJob(9, title = "Analyst")) })

        viewModel.loadLinkableJobs()

        assertEquals(2, viewModel.linkableJobs.value?.size)
        assertEquals(7, viewModel.linkableJobs.value?.first()?.id)
    }

    @Test
    fun `jobs already at a later stage are still offered`() {
        // A goal is routinely created for a job the student already applied to. Filtering
        // those out would leave them permanently unable to connect the two.
        val viewModel = viewModel(saved = { listOf(savedJob(7, status = "interview")) })

        viewModel.loadLinkableJobs()

        assertEquals("interview", viewModel.linkableJobs.value?.first()?.status)
    }

    @Test
    fun `an empty tracker is reported as empty, not as a failure`() {
        val viewModel = viewModel(saved = { emptyList() })

        viewModel.loadLinkableJobs()

        assertNotNull("the request succeeded -- the list is genuinely empty", viewModel.linkableJobs.value)
        assertTrue(viewModel.linkableJobs.value!!.isEmpty())
        assertNull(viewModel.analysisError.value)
    }

    @Test
    fun `a failed fetch does not claim the tracker is empty`() {
        // An empty list here would read as "you have saved no jobs", which is a statement about
        // the student's tracker rather than about this request.
        val viewModel = viewModel(saved = { throw IOException("offline") })

        viewModel.loadLinkableJobs()

        assertNull(viewModel.linkableJobs.value)
        assertNotNull("and the reason is shown", viewModel.analysisError.value)
    }

    @Test
    fun `picking a job links it and closes the picker`() {
        var linkedTo: Int? = null
        val goals = FakeCareerGoalRepository().apply {
            getGoalResult = { detail(application("saved")) }
            linkApplicationResult = { _, savedJobId -> linkedTo = savedJobId; listItem("saved") }
        }
        val jobs = FakeJobHuntRepository().apply { listSavedResult = { listOf(savedJob(7)) } }
        val viewModel = CareerGoalDetailViewModel(1, goals, jobs)
        viewModel.loadLinkableJobs()
        assertNotNull(viewModel.linkableJobs.value)

        viewModel.linkApplication(7)

        assertEquals(7, linkedTo)
        assertNull("a stale list must not stay on screen after linking", viewModel.linkableJobs.value)
        val state = viewModel.uiState.value as CareerGoalDetailUiState.Success
        assertEquals("saved", state.goal.application?.status)
    }

    @Test
    fun `a refused link leaves the goal exactly as it was`() {
        // The server refuses a job that belongs to someone else, or one another goal already
        // claimed. Neither may produce a link on screen that does not exist on the server.
        val goals = FakeCareerGoalRepository().apply {
            getGoalResult = { detail(null) }
            linkApplicationResult = { _, _ -> throw IOException("refused") }
        }
        val jobs = FakeJobHuntRepository().apply { listSavedResult = { listOf(savedJob(7)) } }
        val viewModel = CareerGoalDetailViewModel(1, goals, jobs)

        viewModel.linkApplication(7)

        val state = viewModel.uiState.value as CareerGoalDetailUiState.Success
        assertNull("no fabricated link", state.goal.application)
        assertNotNull(viewModel.analysisError.value)
    }

    @Test
    fun `dismissing the picker clears it without touching the goal`() {
        val viewModel = viewModel()
        viewModel.loadLinkableJobs()

        viewModel.clearLinkableJobs()

        assertNull(viewModel.linkableJobs.value)
        assertTrue(viewModel.uiState.value is CareerGoalDetailUiState.Success)
    }
}
