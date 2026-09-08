package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.ApplicationReadinessDto
import com.aiplacementos.app.data.remote.ReadinessDimensionDto
import com.aiplacementos.app.data.remote.SkillMatchAnalysisDto
import com.aiplacementos.app.data.remote.SkillMatchRowDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ApplicationReadinessViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a premium student gets the breakdown`() {
        val repository = FakeJobHuntRepository().apply {
            readinessResult = { listingId ->
                ApplicationReadinessDto(
                    listingId = listingId,
                    overall = 0.82f,
                    band = "strong_candidate",
                    dimensions = listOf(
                        ReadinessDimensionDto("skills", "Skills", 0.9f, "9 of 10 proven"),
                    ),
                )
            }
        }
        val state = ApplicationReadinessViewModel(repository, 7).uiState.value

        assertTrue(state is ReadinessUiState.Success)
        assertEquals(82, (state as ReadinessUiState.Success).readiness.overallPercent())
    }

    @Test
    fun `a paywall is its own state, not an error`() {
        // A price is not a failure. Rendering the two the same way makes a paywall look broken
        // and, in the other direction, makes a real outage look like an upsell.
        val repository = FakeJobHuntRepository().apply {
            readinessResult = { throw httpException(403) }
        }
        val state = ApplicationReadinessViewModel(repository, 7).uiState.value

        assertTrue(state is ReadinessUiState.PaywallRequired)
    }

    @Test
    fun `a real outage is an error, not a paywall`() {
        val repository = FakeJobHuntRepository().apply {
            readinessResult = { throw httpException(502) }
        }
        val state = ApplicationReadinessViewModel(repository, 7).uiState.value

        assertTrue(state is ReadinessUiState.Failed)
    }
}

class ReadinessDisplayTest {
    @Test
    fun `bands read as advice rather than as a verdict`() {
        // Deliberately no "Not ready": adverts over-state their requirements, and whether to
        // apply anyway is the student's call.
        assertEquals("You're a strong candidate!", readinessBandLabel("strong_candidate"))
        assertEquals("Worth applying", readinessBandLabel("worth_applying"))
        assertEquals("A stretch, but possible", readinessBandLabel("stretch"))
        assertEquals("Worth preparing first", readinessBandLabel("prepare_first"))
    }

    @Test
    fun `an unknown band produces no label rather than a guess`() {
        assertNull(readinessBandLabel("some_new_band"))
        assertNull(readinessBandLabel(null))
    }

    @Test
    fun `an unjudged dimension has no percentage at all`() {
        // The value of null here is the whole point: a student with no mock interviews has
        // not failed interviews, and 0% would say they had.
        assertNull(dimensionPercent(null))
        assertEquals(90, dimensionPercent(0.9f))
    }

    @Test
    fun `an out-of-range score is clamped rather than shown as 120 percent`() {
        assertEquals(100, dimensionPercent(1.4f))
        assertEquals(0, dimensionPercent(-0.2f))
    }

    @Test
    fun `an overall of null stays null rather than becoming zero`() {
        val empty = ApplicationReadinessDto(listingId = 1, overall = null)
        assertNull(empty.overallPercent())
    }

    @Test
    fun `the unjudged label never reads as a failure`() {
        val label = unjudgedDimensionLabel()
        assertTrue(label.contains("Not checked"))
        assertTrue(!label.contains("0"))
    }
}

class ReadinessGapDestinationTest {
    @Test
    fun `each gap kind routes to the feature that actually closes it`() {
        assertEquals(ReadinessGapDestination.Roadmap, gapDestination("roadmap"))
        assertEquals(ReadinessGapDestination.SkillPassport, gapDestination("skill_passport"))
        assertEquals(ReadinessGapDestination.Resume, gapDestination("resume"))
        assertEquals(ReadinessGapDestination.Interview, gapDestination("interview"))
        assertEquals(ReadinessGapDestination.Profile, gapDestination("education"))
    }

    @Test
    fun `an unknown action falls back to the one destination that cannot mislead`() {
        // The Roadmap can absorb any gap. Sending a student to the Skill Passport for
        // something it does not cover would be worse than sending them somewhere general.
        assertEquals(ReadinessGapDestination.Roadmap, gapDestination("something_new"))
    }
}

class SkillMatchAnalysisViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun row(skill: String, status: String, strength: String) =
        SkillMatchRowDto(skill = skill, status = status, strength = strength)

    @Test
    fun `analysis loads`() {
        val repository = FakeJobHuntRepository().apply {
            skillAnalysisResult = {
                SkillMatchAnalysisDto(
                    totalScore = 0.92f,
                    rows = listOf(row("python", "verified_evidence", "strong_match")),
                    verifiedSkillCount = 1,
                )
            }
        }
        val state = SkillMatchAnalysisViewModel(repository, 1).uiState.value

        assertTrue(state is SkillMatchUiState.Success)
        assertEquals(92, (state as SkillMatchUiState.Success).analysis.matchPercent())
    }

    @Test
    fun `rows are ordered strongest ground first`() {
        val sorted = sortedAnalysisRows(
            listOf(
                row("aws", "missing", "skill_gap"),
                row("linux", "unclear", "unclear"),
                row("python", "verified_evidence", "strong_match"),
                row("sql", "on_resume", "match"),
            )
        )
        assertEquals(listOf("python", "sql", "aws", "linux"), sorted.map { it.skill })
    }

    @Test
    fun `an unknown status sorts last rather than to the top`() {
        // A future bucket must not be able to push itself above verified evidence on a screen
        // that was never designed for it.
        val sorted = sortedAnalysisRows(
            listOf(
                row("mystery", "some_new_bucket", "unclear"),
                row("python", "verified_evidence", "strong_match"),
            )
        )
        assertEquals(listOf("python", "mystery"), sorted.map { it.skill })
    }

    @Test
    fun `badges say how a skill reads to a recruiter`() {
        assertEquals("Strong match", matchStrengthLabel("strong_match"))
        // A resume-only skill is a credible match on a CV screen even though it is only a
        // claim to us. "Weak" would understate what the student actually has.
        assertEquals("Match", matchStrengthLabel("match"))
        assertEquals("Skill gap", matchStrengthLabel("skill_gap"))
        assertEquals("Unclear", matchStrengthLabel("unclear"))
    }

    @Test
    fun `an unrecognised badge renders as nothing rather than a raw enum`() {
        assertEquals("", matchStrengthLabel("great_v2"))
    }

    @Test
    fun `captions name where our confidence comes from`() {
        assertEquals("Verified evidence", skillProvenanceCaption("verified_evidence"))
        assertEquals("On your resume", skillProvenanceCaption("on_resume"))
        assertEquals("Not found", skillProvenanceCaption("missing"))
        assertEquals("We can't tell yet", skillProvenanceCaption("unclear"))
    }
}

class SavedJobProvenanceLabelTest {
    @Test
    fun `a saved search is named when we still have it`() {
        assertEquals(
            "Saved search: Dubai Data Analyst",
            savedJobSourceLabel("saved_search", "Dubai Data Analyst"),
        )
    }

    @Test
    fun `a deleted saved search falls back without claiming the job was saved manually`() {
        // The row keeps its history. It just can no longer name the search.
        assertEquals("Saved search", savedJobSourceLabel("saved_search", null))
    }

    @Test
    fun `every other source has its own words`() {
        assertEquals("Job alert", savedJobSourceLabel("job_alert", null))
        assertEquals("Recommended for you", savedJobSourceLabel("recommended", null))
        assertEquals("Manually saved", savedJobSourceLabel("manual", null))
    }

    @Test
    fun `an unknown source falls back to the safest true statement`() {
        assertEquals("Saved", savedJobSourceLabel("some_new_source", null))
    }
}

class AppliedOnLabelTest {
    @Test
    fun `an applied date reads naturally`() {
        assertEquals("Applied on 5 Sep 2026", appliedOnLabel("2026-09-05T10:00:00"))
    }

    @Test
    fun `a job that has not been applied to shows nothing`() {
        assertNull(appliedOnLabel(null))
        assertNull(appliedOnLabel(""))
    }

    @Test
    fun `an unparseable date shows nothing rather than a placeholder`() {
        assertNull(appliedOnLabel("not a date"))
        assertNull(appliedOnLabel("2026-99-99"))
    }
}
