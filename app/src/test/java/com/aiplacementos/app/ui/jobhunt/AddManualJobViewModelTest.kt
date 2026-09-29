package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobListingDto
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Adding a job the student found somewhere this app cannot search.
 *
 * Two properties carry most of the weight here, and both are about not losing what the student
 * typed. A refused submit must leave every field exactly where it was -- the job description
 * they pasted is the most expensive thing on the screen to retype -- and a second tap while a
 * request is in flight must not produce a second tracked application, because a job with no
 * link has no identity that would let the server recognise the duplicate.
 */
class AddManualJobViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun savedJob(id: Int = 1, status: String = "saved") = SavedJobDto(
        id = id,
        status = status,
        createdAt = "2026-09-25T10:00:00",
        updatedAt = "2026-09-25T10:00:00",
        source = "manual",
        listing = JobListingDto(
            id = id,
            provider = "manual",
            url = null,
            title = "Data Engineer",
            fetchedAt = "2026-09-25T10:00:00",
        ),
    )

    private fun viewModel(
        onAdd: suspend (com.aiplacementos.app.fakes.ManualJobSubmission) -> SavedJobDto = {
            savedJob()
        },
    ): Pair<AddManualJobViewModel, FakeJobHuntRepository> {
        val repository = FakeJobHuntRepository().apply { addManualJobResult = onAdd }
        return AddManualJobViewModel(repository) to repository
    }

    private fun httpError(code: Int, body: String) = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
    )

    @Test
    fun `the form starts empty and the button does nothing`() {
        val (viewModel, _) = viewModel()

        assertEquals("", viewModel.uiState.value.title)
        assertFalse("nothing to submit yet", viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `a title alone is enough to add a job`() {
        // Everything else is optional because the model says it is. A referral may genuinely
        // arrive with no company name attached yet, and refusing it would lose the application
        // rather than improve the data.
        val (viewModel, repository) = viewModel()
        viewModel.onTitleChange("Campus placement -- SDE")

        viewModel.submit()

        assertEquals(1, repository.manualJobsAdded.size)
        assertEquals("Campus placement -- SDE", repository.manualJobsAdded.single().title)
        assertNull(repository.manualJobsAdded.single().company)
    }

    @Test
    fun `an empty title is refused without a network call`() {
        val (viewModel, repository) = viewModel()

        viewModel.submit()

        assertTrue("nothing was sent", repository.manualJobsAdded.isEmpty())
        assertNotNull(viewModel.uiState.value.titleError)
        assertNull(viewModel.uiState.value.added)
    }

    @Test
    fun `a whitespace-only title is refused too`() {
        val (viewModel, repository) = viewModel()
        viewModel.onTitleChange("   ")

        viewModel.submit()

        assertTrue(repository.manualJobsAdded.isEmpty())
        assertNotNull(viewModel.uiState.value.titleError)
    }

    @Test
    fun `the title error clears as soon as they start fixing it`() {
        val (viewModel, _) = viewModel()
        viewModel.submit()
        assertNotNull(viewModel.uiState.value.titleError)

        viewModel.onTitleChange("D")

        assertNull("a message about the old value must not sit over the new one", viewModel.uiState.value.titleError)
    }

    @Test
    fun `blank optional fields are sent as absent, not as empty strings`() {
        val (viewModel, repository) = viewModel()
        viewModel.onTitleChange("Data Engineer")
        viewModel.onCompanyChange("   ")
        viewModel.onLocationChange("")
        viewModel.onUrlChange("  ")

        viewModel.submit()

        val sent = repository.manualJobsAdded.single()
        assertNull("not a company named one space", sent.company)
        assertNull(sent.location)
        assertNull(sent.url)
    }

    @Test
    fun `surrounding whitespace is trimmed from what is stored`() {
        val (viewModel, repository) = viewModel()
        viewModel.onTitleChange("  Data Engineer  ")
        viewModel.onCompanyChange(" Contoso ")

        viewModel.submit()

        val sent = repository.manualJobsAdded.single()
        assertEquals("Data Engineer", sent.title)
        assertEquals("Contoso", sent.company)
    }

    @Test
    fun `a student already interviewing can record that stage`() {
        val (viewModel, repository) = viewModel(onAdd = { savedJob(status = "interview") })
        viewModel.onTitleChange("Data Engineer")
        viewModel.onStageChange(ApplicationStage.Interview)

        viewModel.submit()

        assertEquals("interview", repository.manualJobsAdded.single().status)
        assertEquals("interview", viewModel.uiState.value.added?.status)
    }

    @Test
    fun `the screen only moves on once the server has confirmed it`() {
        val (viewModel, _) = viewModel()
        viewModel.onTitleChange("Data Engineer")
        assertNull("nothing confirmed before submitting", viewModel.uiState.value.added)

        viewModel.submit()

        assertNotNull(viewModel.uiState.value.added)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `a failed submit keeps every field the student filled in`() {
        // The whole reason the form's state lives in the view model. Clearing on failure would
        // cost them the job description they had just pasted.
        val (viewModel, _) = viewModel(onAdd = { throw IOException("offline") })
        viewModel.onTitleChange("Data Engineer")
        viewModel.onCompanyChange("Contoso")
        viewModel.onDescriptionChange("Required: SQL, Python, Airflow.")

        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals("Data Engineer", state.title)
        assertEquals("Contoso", state.company)
        assertEquals("Required: SQL, Python, Airflow.", state.description)
        assertNull("and nothing is claimed to have been added", state.added)
        assertNotNull(state.submitError)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `a refused link shows the server's own explanation`() {
        // Only the server knows which schemes it will store. A generic "couldn't add this job
        // (422)" would leave the student with no idea which field to fix.
        val (viewModel, _) = viewModel(
            onAdd = {
                throw httpError(
                    422,
                    """{"detail":"That link could not be saved. Enter a full http:// or https:// web address, or leave it empty."}""",
                )
            },
        )
        viewModel.onTitleChange("Data Engineer")
        viewModel.onUrlChange("javascript:alert(1)")

        viewModel.submit()

        assertEquals(
            "That link could not be saved. Enter a full http:// or https:// web address, or leave it empty.",
            viewModel.uiState.value.submitError,
        )
        assertEquals("the link they typed is still there to correct", "javascript:alert(1)", viewModel.uiState.value.url)
    }

    @Test
    fun `a validation error whose detail is a list falls back to the generic message`() {
        // FastAPI's own schema validation answers with a list of field errors rather than a
        // sentence. Rendering that raw would be worse than the fallback, and these are the
        // cases the client already prevents.
        val (viewModel, _) = viewModel(
            onAdd = { throw httpError(422, """{"detail":[{"loc":["body","title"],"msg":"too long"}]}""") },
        )
        viewModel.onTitleChange("Data Engineer")

        viewModel.submit()

        assertEquals(
            "Some of that information isn't valid. Please check it and try again.",
            viewModel.uiState.value.submitError,
        )
    }

    @Test
    fun `an unreadable error body still produces a message`() {
        val (viewModel, _) = viewModel(onAdd = { throw httpError(500, "<html>gateway</html>") })
        viewModel.onTitleChange("Data Engineer")

        viewModel.submit()

        assertNotNull(viewModel.uiState.value.submitError)
    }

    @Test
    fun `retrying after a failure adds the job once, not twice`() {
        var attempts = 0
        val (viewModel, repository) = viewModel(
            onAdd = {
                attempts++
                if (attempts == 1) throw IOException("offline") else savedJob()
            },
        )
        viewModel.onTitleChange("Data Engineer")

        viewModel.submit()
        assertNotNull(viewModel.uiState.value.submitError)
        viewModel.submit()

        assertEquals(2, repository.manualJobsAdded.size)
        assertNotNull("the second attempt succeeded", viewModel.uiState.value.added)
        assertNull("and the failure message is gone", viewModel.uiState.value.submitError)
    }

    @Test
    fun `a double tap sends one request, not two`() {
        // A job with no link has no identity the server could use to recognise a duplicate, so
        // this guard is the only thing standing between an impatient tap and two tracked
        // applications for one job.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val (viewModel, repository) = viewModel(
            onAdd = {
                gate.await()
                savedJob()
            },
        )
        viewModel.onTitleChange("Data Engineer")

        viewModel.submit()
        viewModel.submit()
        viewModel.submit()

        assertEquals(1, repository.manualJobsAdded.size)
        assertTrue(viewModel.uiState.value.isSubmitting)
        assertFalse("and the button is greyed while it is in flight", viewModel.uiState.value.canSubmit)
        gate.complete(Unit)
    }
}
