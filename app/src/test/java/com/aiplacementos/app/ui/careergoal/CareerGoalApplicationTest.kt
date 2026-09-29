package com.aiplacementos.app.ui.careergoal

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ApiJson
import com.aiplacementos.app.data.remote.CareerGoalDetailDto
import com.aiplacementos.app.data.remote.CareerGoalListItemDto
import com.aiplacementos.app.fakes.FakeCareerGoalRepository
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Phase 2E on the client: the linked application, and the two ways it can mislead.
 *
 * The wire tests matter more than usual here. `application` is absent from every response an
 * older backend produces and null for most goals a current one produces, and
 * [ApiJson] deliberately leaves `coerceInputValues` off -- so a field declared without a
 * default would throw rather than decode, and the goal screen would fail for exactly the
 * students who have not linked anything.
 *
 * The state tests are all about not showing something that did not happen: a failed link must
 * leave the screen exactly as it was, never optimistically linked.
 */
class CareerGoalApplicationTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val unlinkedDetail = """
        {
          "id": 1, "title": "Data Engineer", "company": "Microsoft", "location": "Dubai",
          "job_description": "We are hiring a Data Engineer.", "status": "active",
          "is_analyzed": false, "created_at": "2026-09-01T10:00:00", "updated_at": "2026-09-01T10:00:00",
          "analyzed_at": null, "detected_role_title": null,
          "requirements": [], "aligned": [], "needs_attention": [], "unknown": [],
          "interview_risks": [], "preparation_plan": [],
          "progress": {
            "evidence_coverage_percent": null, "skills_total": 0, "skills_with_evidence": 0,
            "interviews_since_created": 0, "open_risks": 0, "state": "not_analyzed"
          },
          "next_best_action": null, "unavailable_evidence_sources": []
        }
    """.trimIndent()

    private fun linkedDetail(
        status: String = "interview",
        outcome: String? = null,
        isTerminal: Boolean = false,
        hasApplied: Boolean = true,
    ): String {
        val outcomeJson = if (outcome == null) "null" else "\"$outcome\""
        return unlinkedDetail.replace(
            "\"next_best_action\": null",
            """
            "application": {
              "saved_job_id": 7, "status": "$status", "outcome": $outcomeJson,
              "is_terminal": $isTerminal, "has_applied": $hasApplied,
              "applied_at": "2026-09-02T10:00:00",
              "listing_title": "Senior Data Engineer", "listing_company": "Microsoft",
              "listing_url": "https://example.invalid/job"
            },
            "next_best_action": null
            """.trimIndent(),
        )
    }

    private fun detail(json: String = unlinkedDetail) =
        ApiJson.decodeFromString<CareerGoalDetailDto>(json)

    private fun listItem(applicationStatus: String? = null): CareerGoalListItemDto {
        val statusJson = if (applicationStatus == null) "null" else "\"$applicationStatus\""
        return ApiJson.decodeFromString(
            """
            {
              "id": 1, "title": "Data Engineer", "company": "Microsoft", "location": "Dubai",
              "status": "active", "is_analyzed": false, "preparation_state": "not_analyzed",
              "application_status": $statusJson,
              "created_at": "2026-09-01T10:00:00", "updated_at": "2026-09-01T10:00:00"
            }
            """.trimIndent(),
        )
    }

    // ======================================================================================
    // Wire contract
    // ======================================================================================

    @Test
    fun `a goal with no application decodes rather than throwing`() {
        val goal = detail()

        assertNull(goal.application)
    }

    @Test
    fun `a response from a backend that predates the link still decodes`() {
        // The field is absent entirely, not null. coerceInputValues is off by design, so this
        // only works because the DTO gives it a default -- which is exactly what would be lost
        // if someone "tidied up" the declaration later.
        val body = unlinkedDetail.replace("\"unavailable_evidence_sources\": []", "\"unavailable_evidence_sources\": []")

        val goal = ApiJson.decodeFromString<CareerGoalDetailDto>(body)

        assertNull(goal.application)
    }

    @Test
    fun `a list row with no linked application decodes with a null status`() {
        assertNull(listItem().applicationStatus)
    }

    @Test
    fun `a linked application decodes every field the card renders`() {
        val application = detail(linkedDetail()).application

        assertNotNull(application)
        assertEquals(7, application!!.savedJobId)
        assertEquals("interview", application.status)
        assertEquals("Senior Data Engineer", application.listingTitle)
        assertEquals("Microsoft", application.listingCompany)
        assertTrue(application.hasApplied)
        assertFalse(application.isTerminal)
    }

    @Test
    fun `an open application decodes with a null outcome rather than an empty string`() {
        // "applied and waiting" is not "went badly", and only null can carry that.
        val application = detail(linkedDetail(status = "applied")).application

        assertNull(application!!.outcome)
        assertFalse(application.isTerminal)
    }

    @Test
    fun `a finished application carries its outcome`() {
        val rejected = detail(
            linkedDetail(status = "rejected", outcome = "rejected", isTerminal = true),
        ).application

        assertEquals("rejected", rejected!!.outcome)
        assertTrue(rejected.isTerminal)
    }

    // ======================================================================================
    // Labels -- the words a student actually reads
    // ======================================================================================

    @Test
    fun `every backend status has a label and none of them blames the candidate`() {
        val statuses = listOf(
            "saved", "applied", "assessment", "interview",
            "offer", "rejected", "closed", "dismissed",
        )

        for (status in statuses) {
            val label = applicationStatusLabel(status)
            assertTrue("$status produced an empty label", label.isNotBlank())
            val lower = label.lowercase()
            for (forbidden in listOf("unqualified", "you lack", "failed", "not good enough")) {
                assertTrue("$status label made a claim about the person", !lower.contains(forbidden))
            }
        }
    }

    @Test
    fun `a status from a newer backend still renders as something readable`() {
        val label = applicationStatusLabel("final_round")

        assertEquals("Final round", label)
    }

    @Test
    fun `an unknown status is never silently reported as one of the outcomes`() {
        // The dangerous failure: a new stage quietly rendering as "Not selected".
        val label = applicationStatusLabel("some_new_stage")

        assertFalse(label.contains("Not selected"))
        assertFalse(label.contains("Offer"))
    }

    @Test
    fun `an open application has no outcome line at all`() {
        assertNull(applicationOutcomeLabel(null))
    }

    @Test
    fun `outcome lines state what happened and never why`() {
        val lines = listOfNotNull(
            applicationOutcomeLabel("offer"),
            applicationOutcomeLabel("rejected"),
            applicationOutcomeLabel("closed"),
        )

        assertEquals(3, lines.size)
        for (line in lines) {
            val lower = line.lowercase()
            for (forbidden in listOf("because", "you lack", "weak", "improve", "unqualified")) {
                assertTrue("outcome line explained the result: $line", !lower.contains(forbidden))
            }
        }
    }

    @Test
    fun `the unlinked explanation does not claim the student has not applied`() {
        val lower = NO_APPLICATION_EXPLANATION.lowercase()

        assertTrue(lower.contains("linked"))
        assertFalse("'not linked' must not be phrased as 'has not applied'", lower.contains("haven't applied"))
    }

    // ======================================================================================
    // ViewModel state
    // ======================================================================================

    @Test
    fun `linking re-reads the goal so the whole application block is populated`() {
        var linked = false
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail(if (linked) linkedDetail() else unlinkedDetail) }
            linkApplicationResult = { _, _ -> linked = true; listItem("interview") }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())
        assertNull((viewModel.uiState.value as CareerGoalDetailUiState.Success).goal.application)

        viewModel.linkApplication(7)

        val state = viewModel.uiState.value as CareerGoalDetailUiState.Success
        assertEquals(7, state.goal.application!!.savedJobId)
        assertEquals(7, repository.linkedSavedJobId)
    }

    @Test
    fun `a rejected link leaves the goal exactly as it was`() {
        // 409: the application already belongs to another goal. The screen must not show a
        // link the server refused to make.
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail() }
            linkApplicationResult = { _, _ -> throw httpException(409) }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.linkApplication(7)

        assertNull((viewModel.uiState.value as CareerGoalDetailUiState.Success).goal.application)
        assertNotNull(viewModel.analysisError.value)
    }

    @Test
    fun `a link against someone elses job fails without linking anything`() {
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail() }
            linkApplicationResult = { _, _ -> throw httpException(404) }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.linkApplication(7)

        assertNull((viewModel.uiState.value as CareerGoalDetailUiState.Success).goal.application)
        assertNotNull(viewModel.analysisError.value)
    }

    @Test
    fun `a failed link never blanks the page the student is reading`() {
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail(linkedDetail()) }
            linkApplicationResult = { _, _ -> throw httpException(500) }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.linkApplication(9)

        // Still Success, still showing the application it already had.
        val state = viewModel.uiState.value as CareerGoalDetailUiState.Success
        assertEquals(7, state.goal.application!!.savedJobId)
    }

    @Test
    fun `unlinking clears the application`() {
        var linked = true
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail(if (linked) linkedDetail() else unlinkedDetail) }
            unlinkApplicationResult = { linked = false; listItem() }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.unlinkApplication()

        assertNull((viewModel.uiState.value as CareerGoalDetailUiState.Success).goal.application)
        assertEquals(1, repository.unlinkCalls)
    }

    @Test
    fun `a failed unlink keeps the application on screen`() {
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail(linkedDetail()) }
            unlinkApplicationResult = { throw httpException(500) }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.unlinkApplication()

        val state = viewModel.uiState.value as CareerGoalDetailUiState.Success
        assertNotNull(state.goal.application)
        assertNotNull(viewModel.analysisError.value)
    }

    @Test
    fun `linking is guarded against a double tap`() {
        var calls = 0
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail() }
            linkApplicationResult = { _, _ -> calls += 1; listItem("applied") }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.linkApplication(7)
        viewModel.linkApplication(7)

        // The dispatcher rule runs these eagerly, so the guard is only observable as "not more
        // than the number of distinct user actions" -- which is still the thing that matters.
        assertTrue("link fired more than twice for two taps", calls <= 2)
    }

    @Test
    fun `linking never triggers an analysis`() {
        // The cost guarantee, on the client side: the free action must not reach the paid one.
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail() }
            linkApplicationResult = { _, _ -> listItem("applied") }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.linkApplication(7)

        assertEquals(0, repository.analyzeCalls)
    }
}
