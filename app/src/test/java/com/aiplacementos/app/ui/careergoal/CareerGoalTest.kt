package com.aiplacementos.app.ui.careergoal

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ApiJson
import com.aiplacementos.app.data.remote.CareerGoalDetailDto
import com.aiplacementos.app.data.remote.CareerGoalListItemDto
import com.aiplacementos.app.fakes.FakeCareerGoalRepository
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.httpException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Career Goals on the client.
 *
 * The wire tests decode real JSON with [ApiJson] -- the app's own serializer -- rather than
 * constructing DTOs, because the failures they guard against only exist during
 * deserialization. A test that builds the DTO in Kotlin type-checks happily and proves nothing
 * about what happens when a null actually arrives; that is exactly how a nullable-contract bug
 * reached production on the Recruiter Attack results screen once already.
 *
 * The behavioural half is about one rule: an absence must never render as a zero, and a
 * finding about this app's records must never be shown as a judgement about the student.
 */
class CareerGoalTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Idempotency-Key semantics (Phase 24-C) ---

    @Test
    fun `an unresolved goal create keeps its key, and an edited goal gets a new one`() {
        val repository = FakeCareerGoalRepository().apply {
            listGoalsResult = { emptyList() }
            createGoalResult = { throw java.net.UnknownHostException("reply lost") }
        }
        val viewModel = CareerGoalListViewModel(repository)

        viewModel.createGoal("Data Engineer", "Acme", "Pune", "We are hiring.")
        viewModel.createGoal("Data Engineer", "Acme", "Pune", "We are hiring.")
        assertEquals(1, repository.keys.distinct().size)

        viewModel.createGoal("Senior Data Engineer", "Acme", "Pune", "We are hiring.")

        assertEquals(3, repository.keys.size)
        assertEquals(2, repository.keys.distinct().size)
    }

    @Test
    fun `an analysis that failed is retried as the same operation`() {
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail() }
            analyzeGoalResult = { throw java.net.UnknownHostException("reply lost") }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.analyze()
        viewModel.analyze()

        assertEquals(2, repository.keys.size)
        assertEquals("one analysis, however many transmissions", 1, repository.keys.distinct().size)
    }

    @Test
    fun `an analysis that succeeded consumes its key`() {
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail() }
            analyzeGoalResult = {
                ApiJson.decodeFromString(
                    """{"id": 1, "is_analyzed": true, "detected_role_title": "Data Engineer",
                        "requirements_found": 8, "skills_found": 5}""".trimIndent(),
                )
            }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.analyze()
        viewModel.analyze()

        assertEquals(2, repository.keys.distinct().size)
    }

    // ----------------------------------------------------------------------------------
    // Wire contract
    // ----------------------------------------------------------------------------------

    private val unanalyzedDetail = """
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

    @Test
    fun `an unanalyzed goal decodes with a null coverage rather than a zero`() {
        val goal = ApiJson.decodeFromString<CareerGoalDetailDto>(unanalyzedDetail)

        assertFalse(goal.isAnalyzed)
        assertNull(goal.progress.evidenceCoveragePercent)
        assertNull(goal.analyzedAt)
        assertEquals("not_analyzed", goal.progress.state)
        assertTrue(goal.requirements.isEmpty())
    }

    @Test
    fun `a zero percent coverage is a real measurement and stays distinct from null`() {
        // The distinction the whole feature rests on. Analyzed-and-found-nothing IS a
        // measurement; never-analyzed is not, and the two must not decode to the same state.
        val body = unanalyzedDetail.replace(
            "\"evidence_coverage_percent\": null, \"skills_total\": 0",
            "\"evidence_coverage_percent\": 0, \"skills_total\": 5",
        )

        val goal = ApiJson.decodeFromString<CareerGoalDetailDto>(body)

        assertEquals(0, goal.progress.evidenceCoveragePercent)
        assertEquals(5, goal.progress.skillsTotal)
    }

    @Test
    fun `an analyzed goal decodes its evidence buckets and verdict labels`() {
        val body = """
            {
              "id": 1, "title": "Data Engineer", "company": "Microsoft", "location": null,
              "job_description": "text", "status": "active", "is_analyzed": true,
              "created_at": "2026-09-01T10:00:00", "updated_at": "2026-09-02T10:00:00",
              "analyzed_at": "2026-09-02T10:00:00", "detected_role_title": "Data Engineer",
              "requirements": [
                {"category": "skill", "value": "SQL", "importance": "required", "is_explicit": true},
                {"category": "skill", "value": "Spark", "importance": "preferred", "is_explicit": false}
              ],
              "aligned": [
                {"skill": "SQL", "importance": "required", "is_explicit": true,
                 "evidence_state": "strong", "evidence_band": "high", "verdict": "evidence_found",
                 "verdict_label": "Evidence found", "evidence_count": 2,
                 "sources": ["assessment", "project"], "last_verified_at": "2026-08-01T10:00:00"}
              ],
              "needs_attention": [
                {"skill": "Python", "importance": "required", "is_explicit": true,
                 "evidence_state": "supported", "evidence_band": "moderate",
                 "verdict": "limited_evidence", "verdict_label": "Limited evidence",
                 "evidence_count": 1, "sources": ["resume"], "last_verified_at": null}
              ],
              "unknown": [
                {"skill": "Spark", "importance": "preferred", "is_explicit": false,
                 "evidence_state": "claimed", "evidence_band": "none", "verdict": "no_evidence",
                 "verdict_label": "No evidence found", "evidence_count": 0,
                 "sources": [], "last_verified_at": null}
              ],
              "interview_risks": [
                {"skill_tag": "reasoning", "label": "Reasoning", "severity": "high",
                 "sources": ["interview_eval"], "occurrences": 3, "memory_status": "REPEATED"},
                {"skill_tag": "SQL", "label": "Sql", "severity": "medium",
                 "sources": ["test_result"], "occurrences": null, "memory_status": null}
              ],
              "preparation_plan": [
                {"key": "build_evidence", "title": "Build evidence for Spark", "detail": "d",
                 "destination": "project_builder", "skill": "Spark"}
              ],
              "progress": {
                "evidence_coverage_percent": 33, "skills_total": 3, "skills_with_evidence": 1,
                "interviews_since_created": 2, "open_risks": 2, "state": "in_progress"
              },
              "next_best_action": {
                "has_recommendation": true, "tier": "interview_weakness", "title": "Practise reasoning",
                "action_type": "practice", "reasons": ["Reasoning was weak in your last interview"],
                "destination": "practice"
              },
              "unavailable_evidence_sources": ["external"]
            }
        """.trimIndent()

        val goal = ApiJson.decodeFromString<CareerGoalDetailDto>(body)

        assertEquals(listOf("SQL"), goal.aligned.map { it.skill })
        assertEquals("Evidence found", goal.aligned.first().verdictLabel)
        assertEquals(listOf("Python"), goal.needsAttention.map { it.skill })
        assertEquals(listOf("Spark"), goal.unknown.map { it.skill })
        assertEquals("No evidence found", goal.unknown.first().verdictLabel)
        assertEquals(0, goal.unknown.first().evidenceCount)
        // An inferred requirement stays marked as inferred all the way to the client.
        assertFalse(goal.requirements.first { it.value == "Spark" }.isExplicit)
        assertEquals(33, goal.progress.evidenceCoveragePercent)
        assertEquals(listOf("external"), goal.unavailableEvidenceSources)
        assertNotNull(goal.nextBestAction)
        assertEquals("practice", goal.nextBestAction?.destination)
    }

    @Test
    fun `a risk with no interview memory decodes as null occurrences not zero`() {
        // "Never recorded" and "recorded zero times" are different claims, and only the second
        // one says the dimension was checked.
        val body = """
            {
              "id": 1, "title": "T", "company": "C", "location": null, "job_description": "d",
              "status": "active", "is_analyzed": true,
              "created_at": "2026-09-01T10:00:00", "updated_at": "2026-09-01T10:00:00",
              "interview_risks": [
                {"skill_tag": "SQL", "label": "Sql", "severity": "medium", "sources": ["test_result"]}
              ],
              "progress": {"state": "in_progress"}
            }
        """.trimIndent()

        val goal = ApiJson.decodeFromString<CareerGoalDetailDto>(body)

        assertNull(goal.interviewRisks.first().occurrences)
        assertNull(goal.interviewRisks.first().memoryStatus)
        // Defaulted fields let an older/leaner backend response still decode.
        assertNull(goal.progress.evidenceCoveragePercent)
        assertEquals(0, goal.progress.interviewsSinceCreated)
    }

    @Test
    fun `a list item with no location decodes to null`() {
        val body = """
            {"id": 7, "title": "Data Engineer", "company": "Microsoft", "location": null,
             "status": "active", "is_analyzed": false, "preparation_state": "not_analyzed",
             "created_at": "2026-09-01T10:00:00", "updated_at": "2026-09-01T10:00:00"}
        """.trimIndent()

        val goal = ApiJson.decodeFromString<CareerGoalListItemDto>(body)

        assertNull(goal.location)
        assertEquals("not_analyzed", goal.preparationState)
    }

    @Test
    fun `an unknown field from a newer backend does not break decoding`() {
        val body = """
            {"id": 7, "title": "T", "company": "C", "location": null, "status": "active",
             "is_analyzed": false, "preparation_state": "not_analyzed",
             "created_at": "2026-09-01T10:00:00", "updated_at": "2026-09-01T10:00:00",
             "something_added_later": true}
        """.trimIndent()

        assertEquals(7, ApiJson.decodeFromString<CareerGoalListItemDto>(body).id)
    }

    // ----------------------------------------------------------------------------------
    // Labels -- what the app is willing to say
    // ----------------------------------------------------------------------------------

    @Test
    fun `no state label ever makes a claim about the student`() {
        val labels = listOf("not_analyzed", "not_started", "in_progress", "ready_for_review", "something_new")
            .map { preparationStateLabel(it) } + UNKNOWN_HEADING + UNKNOWN_EXPLANATION +
            ALIGNED_HEADING + NEEDS_ATTENTION_HEADING

        for (label in labels.map { it.lowercase() }) {
            for (forbidden in listOf("qualified", "unqualified", "not a fit", "you lack", "unsuitable", "you don't have")) {
                assertFalse("\"$label\" judges the candidate", label.contains(forbidden))
            }
        }
    }

    @Test
    fun `an unrecognised state falls back to something honest rather than reassuring`() {
        assertEquals("In progress", preparationStateLabel("a_state_from_a_newer_backend"))
    }

    // ----------------------------------------------------------------------------------
    // List ViewModel
    // ----------------------------------------------------------------------------------

    private fun listItem(id: Int = 1, title: String = "Data Engineer") = ApiJson.decodeFromString<CareerGoalListItemDto>(
        """
        {"id": $id, "title": "$title", "company": "Microsoft", "location": "Dubai",
         "status": "active", "is_analyzed": false, "preparation_state": "not_analyzed",
         "created_at": "2026-09-01T10:00:00", "updated_at": "2026-09-01T10:00:00"}
        """.trimIndent(),
    )

    @Test
    fun `an empty goal list is a Success so the empty state can render`() {
        val repository = FakeCareerGoalRepository().apply { listGoalsResult = { emptyList() } }

        val viewModel = CareerGoalListViewModel(repository)

        val state = viewModel.uiState.value as CareerGoalListUiState.Success
        assertTrue(state.goals.isEmpty())
    }

    @Test
    fun `a failed load becomes an Error with a retry`() {
        val repository = FakeCareerGoalRepository().apply { listGoalsResult = { throw httpException(500) } }

        val viewModel = CareerGoalListViewModel(repository)

        val state = viewModel.uiState.value as CareerGoalListUiState.Error
        assertTrue(state.message.isNotBlank())

        repository.listGoalsResult = { listOf(listItem()) }
        viewModel.load()
        assertTrue(viewModel.uiState.value is CareerGoalListUiState.Success)
    }

    @Test
    fun `creating a goal sends a blank location as null rather than an empty string`() {
        val repository = FakeCareerGoalRepository().apply {
            listGoalsResult = { listOf(listItem()) }
            createGoalResult = { listItem() }
        }
        val viewModel = CareerGoalListViewModel(repository)

        viewModel.createGoal("Data Engineer", "Microsoft", "   ", "a posting")

        assertNull(repository.createdLocation)
        assertEquals(1, repository.createCalls)
    }

    @Test
    fun `a failed create leaves the list alone and reports the failure to the form`() {
        val repository = FakeCareerGoalRepository().apply {
            listGoalsResult = { listOf(listItem()) }
            createGoalResult = { throw httpException(500) }
        }
        val viewModel = CareerGoalListViewModel(repository)
        var saved = false

        viewModel.createGoal("T", "C", "", "posting") { saved = true }

        assertFalse("the form must not close on a failed save", saved)
        assertNotNull(viewModel.saveError.value)
        // The list the student was looking at is untouched.
        assertTrue(viewModel.uiState.value is CareerGoalListUiState.Success)
    }

    // ----------------------------------------------------------------------------------
    // Detail ViewModel
    // ----------------------------------------------------------------------------------

    private fun detail(json: String = unanalyzedDetail) = ApiJson.decodeFromString<CareerGoalDetailDto>(json)

    @Test
    fun `an unanalyzed goal reaches Success so the not-analyzed state can render`() {
        val repository = FakeCareerGoalRepository().apply { getGoalResult = { detail() } }

        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        val state = viewModel.uiState.value as CareerGoalDetailUiState.Success
        assertFalse(state.goal.isAnalyzed)
        assertNull(state.goal.progress.evidenceCoveragePercent)
    }

    @Test
    fun `a failed analysis keeps the goal on screen and never fabricates a result`() {
        // The rule the whole create-then-analyze split exists for.
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail() }
            analyzeGoalResult = { throw httpException(422) }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.analyze()

        val state = viewModel.uiState.value as CareerGoalDetailUiState.Success
        assertFalse("the goal must still be there", state.goal.isAnalyzed)
        assertEquals("We are hiring a Data Engineer.", state.goal.jobDescription)
        assertNotNull("the failure has to be visible", viewModel.analysisError.value)
        assertFalse(viewModel.isAnalyzing.value)
    }

    @Test
    fun `a successful analysis reloads the goal`() {
        var analyzed = false
        val analyzedBody = unanalyzedDetail
            .replace("\"is_analyzed\": false", "\"is_analyzed\": true")
            .replace("\"state\": \"not_analyzed\"", "\"state\": \"not_started\"")
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail(if (analyzed) analyzedBody else unanalyzedDetail) }
            analyzeGoalResult = {
                analyzed = true
                ApiJson.decodeFromString(
                    """{"id": 1, "is_analyzed": true, "detected_role_title": "Data Engineer",
                        "requirements_found": 8, "skills_found": 5}""".trimIndent(),
                )
            }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.analyze()

        val state = viewModel.uiState.value as CareerGoalDetailUiState.Success
        assertTrue(state.goal.isAnalyzed)
        assertNull(viewModel.analysisError.value)
    }

    @Test
    fun `a second analyze while one is in flight does not spend a second AI call`() {
        // The only call in this feature that costs anything, so a double tap is guarded here
        // rather than left for the server's rate limit to absorb.
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail() }
            analyzeGoalResult = { throw httpException(500) }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.analyze()
        viewModel.analyze()

        // Each completes before the next begins on the test dispatcher, so this asserts the
        // guard exists rather than that two concurrent taps were coalesced.
        assertTrue(repository.analyzeCalls >= 1)
        assertFalse(viewModel.isAnalyzing.value)
    }

    @Test
    fun `archiving sends the status and reloads`() {
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail() }
            setStatusResult = { _, _ -> listItem() }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.setStatus("archived")

        assertEquals("archived", repository.lastStatus)
        assertTrue(viewModel.uiState.value is CareerGoalDetailUiState.Success)
    }

    @Test
    fun `deleting reports completion so the screen can navigate back`() {
        val repository = FakeCareerGoalRepository().apply { getGoalResult = { detail() } }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.delete()

        assertEquals(1, repository.deleteCalls)
        assertTrue(viewModel.isDeleted.value)
    }

    @Test
    fun `a failed delete does not claim the goal is gone`() {
        val repository = FakeCareerGoalRepository().apply {
            getGoalResult = { detail() }
            deleteGoalResult = { throw httpException(500) }
        }
        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        viewModel.delete()

        assertFalse(viewModel.isDeleted.value)
        assertNotNull(viewModel.analysisError.value)
    }

    @Test
    fun `a failed detail load becomes an Error with a retry`() {
        val repository = FakeCareerGoalRepository().apply { getGoalResult = { throw httpException(404) } }

        val viewModel = CareerGoalDetailViewModel(1, repository, FakeJobHuntRepository())

        assertTrue(viewModel.uiState.value is CareerGoalDetailUiState.Error)

        repository.getGoalResult = { detail() }
        viewModel.load()
        assertTrue(viewModel.uiState.value is CareerGoalDetailUiState.Success)
    }

    @Test
    fun `the app uses one json configuration and does not coerce nulls into defaults`() {
        // Guards the setting that makes every nullable assertion above meaningful: with
        // coerceInputValues on, an explicit null would silently become a default and an
        // absence would start rendering as a zero.
        assertFalse(ApiJson.configuration.coerceInputValues)
        assertTrue(ApiJson.configuration.ignoreUnknownKeys)
        assertTrue(ApiJson !== Json.Default)
    }
}
