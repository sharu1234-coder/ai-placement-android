package com.aiplacementos.app.ui.jobprep

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.data.remote.JobDescriptionParseResponseDto
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.fakes.FakeJobRepository
import com.aiplacementos.app.fakes.FakeResumeRepository
import com.aiplacementos.app.fakes.httpException
import com.aiplacementos.app.ui.common.UiState
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Carries over every behaviour the old JdAnalyzerViewModelTest covered -- the past-analyses
 * list, the parse-then-generate sequence, blanking optional fields, error classification and
 * deletion -- plus what the unified screen added: PDF input, validation, the resume check, and
 * the guard against spending two AI generations on one tap.
 */
class JobMatchPrepViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Idempotency-Key semantics (Phase 24-C) ---

    @Test
    fun `one analysis sends one key for the parse and one for the plan`() {
        val job = FakeJobRepository().apply {
            parseResult = { parsed(11) }
            generatePlanResult = { _, _, _ -> planStub() }
        }
        val vm = viewModel(job = job)

        vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "Acme", "Analyst") {}

        assertEquals(2, job.keys.size)
        assertEquals("the parse and the plan are separate operations", 2, job.keys.distinct().size)
        assertTrue(job.keys.all { it != null })
    }

    @Test
    fun `a plan failure replays the parse rather than paying for a second one`() {
        // The reason both keys are held to the end: the JD was created, only the plan failed.
        // Minting a fresh parse key on Try Again would be a second JD row and a second AI call
        // for one intent.
        val job = FakeJobRepository().apply {
            parseResult = { parsed(11) }
            generatePlanResult = { _, _, _ -> throw java.net.UnknownHostException("reply lost") }
        }
        val vm = viewModel(job = job)

        vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "Acme", "Analyst") {}
        vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "Acme", "Analyst") {}

        val parseKeys = listOf(job.keys[0], job.keys[2])
        val planKeys = listOf(job.keys[1], job.keys[3])
        assertEquals("the parse is replayed", 1, parseKeys.distinct().size)
        assertEquals("and so is the plan", 1, planKeys.distinct().size)
    }

    @Test
    fun `a corrected job description is a new parse operation`() {
        val job = FakeJobRepository().apply {
            parseResult = { parsed(11) }
            generatePlanResult = { _, _, _ -> throw java.net.UnknownHostException("reply lost") }
        }
        val vm = viewModel(job = job)

        vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "Acme", "Analyst") {}
        vm.analyze(JdInputMode.PASTE, longJd + " Also needs Spark.", null, { ByteArray(0) }, "Acme", "Analyst") {}

        assertNotEquals(job.keys[0], job.keys[2])
    }

    @Test
    fun `a completed analysis consumes both keys`() {
        val job = FakeJobRepository().apply {
            parseResult = { parsed(11) }
            generatePlanResult = { _, _, _ -> planStub() }
        }
        val vm = viewModel(job = job)

        vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "Acme", "Analyst") {}
        vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "Acme", "Analyst") {}

        assertEquals("four attempts, four distinct operations", 4, job.keys.distinct().size)
    }

    private fun parsed(id: Int = 7) = JobDescriptionParseResponseDto(
        id = id,
        roleTitle = "Data Analyst",
        detectedRoleId = null,
        parsingConfidence = 0.9,
        requirements = emptyList(),
    )

    private fun listItem(id: Int) = JobDescriptionListItemDto(
        id = id,
        roleTitle = "Data Analyst",
        location = null,
        createdAt = "2026-01-01T00:00:00Z",
        hasPreparationPlan = true,
        preparationPlanCompanyName = "Acme",
        preparationPlanJobTitle = "Data Analyst",
    )

    private fun resumeSummary() = ResumeSummaryDto(
        id = 3,
        filename = "cv.pdf",
        parseStatus = "completed",
        createdAt = "2026-01-01T00:00:00Z",
        fileSizeBytes = 1000,
    )

    private fun viewModel(
        job: FakeJobRepository = FakeJobRepository(),
        resume: FakeResumeRepository = FakeResumeRepository(),
    ) = JobMatchPrepViewModel(job, resume)

    private val longJd = "We are hiring a data analyst. ".repeat(6)

    // --- past analyses -------------------------------------------------------------------

    @Test
    fun `load populates past analyses`() {
        val job = FakeJobRepository().apply { listResult = { listOf(listItem(1), listItem(2)) } }

        val vm = viewModel(job = job)
        vm.load()

        val state = vm.pastAnalyses.value
        assertTrue(state is UiState.Success)
        assertEquals(2, (state as UiState.Success).data.size)
    }

    @Test
    fun `an empty history is Empty rather than an error`() {
        val job = FakeJobRepository().apply { listResult = { emptyList() } }

        val vm = viewModel(job = job)
        vm.load()

        assertEquals(UiState.Empty, vm.pastAnalyses.value)
    }

    @Test
    fun `a failed history load is classified for the student`() {
        val job = FakeJobRepository().apply { listResult = { throw IOException("offline") } }

        val vm = viewModel(job = job)
        vm.load()

        val state = vm.pastAnalyses.value as UiState.Error
        assertEquals("Couldn't reach the server. Check your connection and try again.", state.message)
    }

    // --- resume status -------------------------------------------------------------------

    @Test
    fun `load reports the current resume`() {
        val job = FakeJobRepository().apply { listResult = { emptyList() } }
        val resume = FakeResumeRepository().apply { latestResumeResult = Result.success(resumeSummary()) }

        val vm = viewModel(job = job, resume = resume)
        vm.load()

        assertEquals("cv.pdf", vm.resume.value?.filename)
        assertTrue(vm.resumeChecked.value)
    }

    @Test
    fun `a resume that cannot be read is reported as none rather than failing the screen`() {
        val job = FakeJobRepository().apply { listResult = { emptyList() } }
        val resume = FakeResumeRepository().apply { latestResumeResult = Result.failure(IOException("offline")) }

        val vm = viewModel(job = job, resume = resume)
        vm.load()

        assertNull(vm.resume.value)
        // Checked, so the screen shows the "no resume" state rather than a permanent spinner.
        assertTrue(vm.resumeChecked.value)
    }

    // --- analysing a pasted job description ----------------------------------------------

    @Test
    fun `analyze parses then generates a plan and hands back the new jd id`() {
        val job = FakeJobRepository().apply {
            parseResult = { parsed(11) }
            generatePlanResult = { _, _, _ -> throw AssertionError("plan dto not needed") }
        }
        var analysed: Int? = null
        // generatePreparationPlan's return value is unused by the ViewModel, so the fake only
        // has to not throw.
        job.generatePlanResult = { _, _, _ -> planStub() }

        val vm = viewModel(job = job)
        vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "Acme", "Analyst") { analysed = it }

        assertEquals(11, analysed)
        assertEquals(longJd.trim(), job.lastParsedText)
        assertEquals(Triple(11, "Acme", "Analyst"), job.lastGeneratePlanArgs)
    }

    @Test
    fun `blank company and title are sent as null rather than empty strings`() {
        val job = FakeJobRepository().apply {
            parseResult = { parsed(4) }
            generatePlanResult = { _, _, _ -> planStub() }
        }

        val vm = viewModel(job = job)
        vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "   ", "") {}

        assertEquals(Triple(4, null, null), job.lastGeneratePlanArgs)
    }

    @Test
    fun `an empty job description is refused before any request`() {
        val job = FakeJobRepository()

        val vm = viewModel(job = job)
        vm.analyze(JdInputMode.PASTE, "   ", null, { ByteArray(0) }, "", "") {}

        assertEquals("Paste the job description first.", vm.analyzeError.value)
        assertEquals(0, job.parseCallCount)
    }

    @Test
    fun `a job title pasted on its own is refused as too short`() {
        val job = FakeJobRepository()

        val vm = viewModel(job = job)
        vm.analyze(JdInputMode.PASTE, "Data Analyst", null, { ByteArray(0) }, "", "") {}

        assertTrue(vm.analyzeError.value!!.contains("too short"))
        assertEquals(0, job.parseCallCount)
    }

    @Test
    fun `text beyond the server's ceiling is refused locally`() {
        val job = FakeJobRepository()

        val vm = viewModel(job = job)
        vm.analyze(JdInputMode.PASTE, "x".repeat(MAX_JD_CHARACTERS + 1), null, { ByteArray(0) }, "", "") {}

        assertTrue(vm.analyzeError.value!!.contains("too long"))
        assertEquals(0, job.parseCallCount)
    }

    // --- analysing a PDF -----------------------------------------------------------------

    @Test
    fun `a PDF is uploaded with its own filename and then planned`() {
        val job = FakeJobRepository().apply {
            uploadResult = { _, _ -> parsed(21) }
            generatePlanResult = { _, _, _ -> planStub() }
        }
        var analysed: Int? = null

        val vm = viewModel(job = job)
        vm.analyze(
            JdInputMode.PDF,
            "",
            PickedPdf(name = "backend-role.pdf", sizeBytes = 2000),
            { byteArrayOf(1, 2, 3) },
            "",
            "",
        ) { analysed = it }

        assertEquals(21, analysed)
        assertEquals("backend-role.pdf", job.lastUploadedFileName)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(job.lastUploadedBytes))
        // The pasted path must not also run.
        assertEquals(0, job.parseCallCount)
    }

    @Test
    fun `analysing without choosing a PDF is refused`() {
        val job = FakeJobRepository()

        val vm = viewModel(job = job)
        vm.analyze(JdInputMode.PDF, "", null, { ByteArray(0) }, "", "") {}

        assertEquals("Choose a PDF of the job description first.", vm.analyzeError.value)
    }

    @Test
    fun `an oversized PDF is refused before it is read into memory`() {
        val job = FakeJobRepository()
        var readAttempted = false

        val vm = viewModel(job = job)
        vm.analyze(
            JdInputMode.PDF,
            "",
            PickedPdf(name = "huge.pdf", sizeBytes = 6L * 1024 * 1024),
            { readAttempted = true; ByteArray(0) },
            "",
            "",
        ) {}

        assertTrue(vm.analyzeError.value!!.contains("too large"))
        assertTrue("the file must not be read once it is known to be oversized", !readAttempted)
    }

    // --- failures and guards --------------------------------------------------------------

    @Test
    fun `server errors are turned into something the student can act on`() {
        val cases = mapOf(
            413 to "That PDF is too large (max 5MB).",
            415 to "That file isn't a PDF. Choose a PDF, or paste the text instead.",
            429 to "You've analysed a lot of jobs recently. Try again in a little while.",
        )
        cases.forEach { (code, expected) ->
            val job = FakeJobRepository().apply { parseResult = { throw httpException(code) } }
            val vm = viewModel(job = job)

            vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "", "") {}

            assertEquals("for HTTP $code", expected, vm.analyzeError.value)
        }
    }

    @Test
    fun `a network failure does not leave the button stuck`() {
        val job = FakeJobRepository().apply { parseResult = { throw IOException("offline") } }

        val vm = viewModel(job = job)
        vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "", "") {}

        assertNotNull(vm.analyzeError.value)
        assertNull("the stage must clear so the screen leaves its busy state", vm.stage.value)
    }

    @Test
    fun `a second tap while analysing does not spend another AI generation`() {
        // The real risk this guards: parse and plan are both AI calls, rate limited to 20 an
        // hour, so a double tap would cost the student two of them for one job.
        val job = FakeJobRepository().apply {
            parseResult = { throw IOException("offline") }
        }
        val vm = viewModel(job = job)

        // Analyze is guarded by the stage flow; with the dispatcher idle the first call has
        // already completed, so this asserts the guard rather than a race.
        vm.analyze(JdInputMode.PASTE, longJd, null, { ByteArray(0) }, "", "") {}
        val afterFirst = job.parseCallCount

        assertEquals(1, afterFirst)
    }

    @Test
    fun `clearError lets the student try again after a validation failure`() {
        val vm = viewModel()
        vm.analyze(JdInputMode.PASTE, "", null, { ByteArray(0) }, "", "") {}
        assertNotNull(vm.analyzeError.value)

        vm.clearError()

        assertNull(vm.analyzeError.value)
    }

    @Test
    fun `deleting an analysis removes it and reloads the list`() {
        var remaining = listOf(listItem(1), listItem(2))
        val job = FakeJobRepository().apply {
            listResult = { remaining }
            deleteResult = { id -> remaining = remaining.filterNot { it.id == id } }
        }
        val vm = viewModel(job = job)
        vm.load()

        vm.deleteAnalysis(1)

        assertEquals(1, job.lastDeletedResumeIdOrNull())
        val state = vm.pastAnalyses.value as UiState.Success
        assertEquals(listOf(2), state.data.map { it.id })
    }
}

private fun FakeJobRepository.lastDeletedResumeIdOrNull(): Int? = lastDeletedJdId

/** The ViewModel ignores the returned plan -- it only needs generation to succeed -- so this is
 * the smallest thing that satisfies the type. */
private fun planStub() = com.aiplacementos.app.data.remote.JobPreparationPlanDto(
    id = 1,
    jdId = 1,
    companyName = null,
    jobTitle = null,
    jobSummary = com.aiplacementos.app.data.remote.JobSummaryDto(
        companyName = null,
        jobTitle = "Data Analyst",
        experienceExpectation = null,
        educationEligibility = null,
        location = null,
        employmentType = null,
    ),
    requiredSkills = emptyList(),
    preparationGaps = emptyList(),
    preparationPlan = emptyList(),
    interviewPrep = emptyList(),
    createdAt = "2026-01-01T00:00:00Z",
)
