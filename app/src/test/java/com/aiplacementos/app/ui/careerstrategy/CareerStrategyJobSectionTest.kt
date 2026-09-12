package com.aiplacementos.app.ui.careerstrategy

import com.aiplacementos.app.data.remote.CareerStrategyDto
import com.aiplacementos.app.data.remote.JobApplicationStateDto
import com.aiplacementos.app.data.remote.JobConditionsDto
import com.aiplacementos.app.data.remote.JobStrategyDto
import com.aiplacementos.app.data.remote.ReadinessBiggestGapDto
import com.aiplacementos.app.data.remote.JobBlockerDto
import com.aiplacementos.app.data.remote.StrategySeriesDto
import com.aiplacementos.app.fakes.FakeCareerStrategyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * D1 and D2: the two Career Strategy sections that were computed, gated, serialised and then
 * never shown.
 *
 * The job-specific strategy had a backend endpoint, a DTO, a repository, an `analyzeJob` call
 * and five passing tests -- and no screen invoked it. That is the failure mode this file is
 * built around: a feature can be unit-tested from end to end and still be invisible, so these
 * tests assert the things that only break when the wiring is real. The counters on the fake
 * repository exist for the same reason -- a screen that re-requests the same advert on every
 * recomposition renders identically and costs twice as much.
 *
 * D3's fields are tested here too, on the client side of the wire: the point of carrying salary
 * period, the match rationale and readiness's own biggest gap through the backend is lost if the
 * client drops them again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CareerStrategyJobSectionTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModelWith(repository: FakeCareerStrategyRepository): CareerStrategyViewModel =
        CareerStrategyViewModel(repository).also { it.load() }

    private fun strategy(
        isPremium: Boolean = true,
        lockedSections: List<String> = emptyList(),
        diminishingReturns: List<StrategySeriesDto> = emptyList(),
    ) = CareerStrategyDto(
        isPremium = isPremium,
        lockedSections = lockedSections,
        state = "FOCUS_ON_INTERVIEW",
        stateReason = "Interview communication is what is holding you back.",
        hasRole = true,
        signalCount = 12,
        diminishingReturns = diminishingReturns,
    )

    // -- D1: the job section actually reaches the screen ---------------------------------------

    @Test
    fun `analysing a job asks the server for that exact listing`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { strategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(listingId = id, jobTitle = "Data Engineer", decision = "APPLY_NOW", headline = "Go.")
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(512)

        assertEquals(512, repository.lastRequestedListingId)
        val loaded = viewModel.jobStrategy.value as StrategySectionState.Loaded
        assertEquals(512, loaded.data.listingId)
    }

    @Test
    fun `opening one advert costs exactly one job request`() {
        """The N+1 guard. Nothing about the rendered output changes if this fires twice, so the
        count is the only thing that can catch a re-request on resume or recomposition."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { strategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(listingId = id, jobTitle = "X", decision = "APPLY_NOW", headline = "Go.")
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(7)

        assertEquals(1, repository.jobStrategyCallCount)
    }

    @Test
    fun `a failed job analysis does not damage the strategy underneath it`() {
        """The "no silent fallback" rule. If the job request fails, the student must see that the
        job could not be analysed -- not a generic strategy quietly standing in for a verdict
        about this advert, which would read as an answer to a question we never answered."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { strategy() }
            jobStrategyResult = { throw IllegalStateException("boom") }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(9)

        assertTrue(viewModel.jobStrategy.value is StrategySectionState.Error)
        // The generic strategy is still there and still itself.
        val main = viewModel.uiState.value as CareerStrategyUiState.Success
        assertEquals("FOCUS_ON_INTERVIEW", main.strategy.state)
    }

    @Test
    fun `a job section that was never asked for stays idle`() {
        """Opening Career Strategy from the drawer must not fire a job request at all."""
        val repository = FakeCareerStrategyRepository().apply { strategyResult = { strategy() } }
        val viewModel = viewModelWith(repository)

        assertEquals(StrategySectionState.Idle, viewModel.jobStrategy.value)
        assertEquals(0, repository.jobStrategyCallCount)
    }

    // -- D3 on the client side: the carried-through facts ---------------------------------------

    @Test
    fun `salary keeps its period all the way to the label`() {
        """The whole reason salary crosses the wire as four fields. 500 per hour rendered as
        500 per year would misstate the offer by about two thousand times."""
        val label = salaryLabel(
            JobConditionsDto(salaryMin = 500, salaryMax = 900, salaryCurrency = "INR", salaryPeriod = "hour"),
        )
        assertNotNull(label)
        assertTrue(label!!.contains("per hour"))
        assertTrue(!label.contains("year"))
    }

    @Test
    fun `an unstated salary period is shown as unstated, never as annual`() {
        val label = salaryLabel(JobConditionsDto(salaryMin = 600000, salaryCurrency = "INR"))
        assertNotNull(label)
        assertTrue(label!!.contains("period not stated"))
        assertTrue(!label.contains("per year"))
    }

    @Test
    fun `a predicted salary is labelled as an estimate`() {
        """Presenting a provider's guess as the employer's published figure would be fabricated
        data about a real company."""
        val label = salaryLabel(
            JobConditionsDto(
                salaryMin = 800000, salaryCurrency = "INR", salaryPeriod = "year", salaryIsPredicted = true,
            ),
        )
        assertTrue(label!!.contains("estimated", ignoreCase = true))
    }

    @Test
    fun `a job with no salary data shows no salary line at all`() {
        """Absent is absent. A zero or a placeholder range would invent an offer."""
        assertNull(salaryLabel(JobConditionsDto(location = "Pune")))
    }

    @Test
    fun `the match rationale and unknown factors survive deserialisation`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { strategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(
                    listingId = id, jobTitle = "Data Engineer", decision = "FIX_BEFORE_APPLYING",
                    headline = "You are close.",
                    matchRationale = "Strong skill overlap and a good experience fit",
                    unknownFactors = listOf("This listing doesn't state a salary"),
                    componentScores = mapOf("experience_fit" to 0.8f),
                )
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(3)

        val loaded = viewModel.jobStrategy.value as StrategySectionState.Loaded
        assertEquals("Strong skill overlap and a good experience fit", loaded.data.matchRationale)
        assertEquals(listOf("This listing doesn't state a salary"), loaded.data.unknownFactors)
        assertEquals(0.8f, loaded.data.componentScores["experience_fit"])
    }

    @Test
    fun `readiness biggest gap arrives typed rather than as an untyped blob`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { strategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(
                    listingId = id, jobTitle = "X", decision = "FIX_BEFORE_APPLYING", headline = "Close.",
                    biggestGap = ReadinessBiggestGapDto(
                        kind = "skill", skill = "Kafka", title = "Close your Kafka gap",
                        detail = "Kafka is named in this listing.", action = "roadmap",
                    ),
                )
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(4)

        val gap = (viewModel.jobStrategy.value as StrategySectionState.Loaded).data.biggestGap
        assertNotNull(gap)
        assertEquals("Kafka", gap!!.skill)
        assertEquals("roadmap", gap.action)
    }

    @Test
    fun `a condition blocker keeps the position the server gave it`() {
        """The backend deliberately puts "this is in a city you ruled out" ahead of "you are
        missing Kafka". Re-sorting on the client would undo exactly that fix."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { strategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(
                    listingId = id, jobTitle = "X", decision = "FIX_BEFORE_APPLYING",
                    headline = "Worth settling one thing first.",
                    blockers = listOf(
                        JobBlockerDto(
                            kind = "condition", subject = "location", title = "Where this job is",
                            detail = "This role is in Hyderabad.", destination = "job_hunt",
                        ),
                        JobBlockerDto(
                            kind = "skill", subject = "Kafka", title = "Kafka is not on your profile",
                            detail = "This listing asks for Kafka.", destination = "roadmap",
                        ),
                    ),
                )
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(5)

        val blockers = (viewModel.jobStrategy.value as StrategySectionState.Loaded).data.blockers
        assertEquals("condition", blockers.first().kind)
        assertEquals("location", blockers.first().subject)
    }

    @Test
    fun `no job decision label advises against applying`() {
        """Whether to apply is the student's call. The four outcomes describe preparation and
        priority, never permission."""
        for (decision in listOf("APPLY_NOW", "FIX_BEFORE_APPLYING", "NOT_A_CURRENT_PRIORITY", "INSUFFICIENT_INFORMATION")) {
            val (headline, support) = jobDecisionLabel(decision)
            val text = "$headline $support".lowercase()
            for (banned in listOf("do not apply", "don't apply", "should not apply", "avoid this")) {
                assertTrue("'$banned' leaked into $decision", !text.contains(banned))
            }
            for (banned in listOf("probability", "chance of", "likelihood", "guaranteed")) {
                assertTrue("'$banned' leaked into $decision", !text.contains(banned))
            }
        }
    }


    // -- application state and deciding factors ------------------------------------------------

    @Test
    fun `a job already applied to carries its application state to the client`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { strategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(
                    listingId = id, jobTitle = "Data Engineer", decision = "APPLY_NOW",
                    headline = "You applied to this on 2026-08-20. You can make a credible application.",
                    application = JobApplicationStateDto(
                        isSaved = true, appliedAt = "2026-08-20T10:00:00Z", status = "rejected",
                    ),
                )
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(11)

        val data = (viewModel.jobStrategy.value as StrategySectionState.Loaded).data
        // A rejection does not un-apply. applied_at is the authority.
        assertEquals("rejected", data.application.status)
        assertTrue(data.application.appliedAt != null)
        assertTrue(data.headline.startsWith("You applied to this on"))
    }

    @Test
    fun `an undocumented tracker status is carried but never turned into a decision`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { strategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(
                    listingId = id, jobTitle = "X", decision = "FIX_BEFORE_APPLYING", headline = "Close.",
                    application = JobApplicationStateDto(isSaved = true, appliedAt = null, status = "closed"),
                )
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(12)

        val data = (viewModel.jobStrategy.value as StrategySectionState.Loaded).data
        assertEquals("closed", data.application.status)
        assertNull(data.application.appliedAt)
        // The decision came from the server and owes nothing to the status string.
        assertEquals("FIX_BEFORE_APPLYING", data.decision)
    }

    @Test
    fun `a job the student never saved reports no application state`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { strategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(listingId = id, jobTitle = "X", decision = "APPLY_NOW", headline = "Go.")
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(13)

        val data = (viewModel.jobStrategy.value as StrategySectionState.Loaded).data
        assertTrue(!data.application.isSaved)
        assertNull(data.application.appliedAt)
        assertNull(data.application.status)
    }

    @Test
    fun `deciding factors arrive in server order and are not reworded`() {
        """The client ranks nothing here. The server decided which components mattered and how to
        say it; re-sorting or rephrasing would put a second opinion on the same screen."""
        val factors = listOf(
            "Skills: your skills line up well with what this advert asks for.",
            "Location: this one is not where you said you want to work.",
        )
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { strategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(
                    listingId = id, jobTitle = "X", decision = "FIX_BEFORE_APPLYING",
                    headline = "Worth settling one thing first.", decidingFactors = factors,
                )
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(14)

        val data = (viewModel.jobStrategy.value as StrategySectionState.Loaded).data
        assertEquals(factors, data.decidingFactors)
    }

    // -- D2: diminishing returns -----------------------------------------------------------------

    @Test
    fun `diminishing returns reach the screen for a premium student`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = {
                strategy(
                    diminishingReturns = listOf(
                        StrategySeriesDto(
                            subject = "SQL", verdict = "PLATEAUED", observationCount = 4,
                            isSufficient = true, mean = 0.91f, latest = 0.92f,
                            reason = "Four results, all above 88%. More practice here is not moving the number.",
                        ),
                    ),
                )
            }
        }
        val viewModel = viewModelWith(repository)

        val loaded = viewModel.uiState.value as CareerStrategyUiState.Success
        assertEquals(1, loaded.strategy.diminishingReturns.size)
        assertEquals("SQL", loaded.strategy.diminishingReturns.first().subject)
    }

    @Test
    fun `multiple diminishing-return insights all arrive`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = {
                strategy(
                    diminishingReturns = listOf(
                        StrategySeriesDto("SQL", "PLATEAUED", 4, true, latest = 0.92f, reason = "Flat."),
                        StrategySeriesDto("Python", "PLATEAUED", 5, true, latest = 0.9f, reason = "Flat."),
                    ),
                )
            }
        }
        val viewModel = viewModelWith(repository)

        val loaded = viewModel.uiState.value as CareerStrategyUiState.Success
        assertEquals(listOf("SQL", "Python"), loaded.strategy.diminishingReturns.map { it.subject })
    }

    @Test
    fun `a free student gets no diminishing-return content, only the lock`() {
        """The gate is the server's: the list is empty because it was never serialised, and
        `locked_sections` is what tells the screen to offer an upgrade instead of rendering an
        ambiguous empty state."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = {
                strategy(isPremium = false, lockedSections = listOf("diminishing_returns", "queue"))
            }
        }
        val viewModel = viewModelWith(repository)

        val loaded = (viewModel.uiState.value as CareerStrategyUiState.Success).strategy
        assertTrue(loaded.diminishingReturns.isEmpty())
        assertTrue(loaded.lockedSections.contains("diminishing_returns"))
    }

    @Test
    fun `an insufficient series carries no numbers to render`() {
        """Below the backend's observation floor every statistic is null. A mean of one result is
        that result, and showing it as a trend would be the fabrication this layer prevents."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = {
                strategy(
                    diminishingReturns = listOf(
                        StrategySeriesDto(
                            subject = "SQL", verdict = "INSUFFICIENT_DATA", observationCount = 1,
                            isSufficient = false,
                            reason = "One result so far. That is not a trend yet.",
                        ),
                    ),
                )
            }
        }
        val viewModel = viewModelWith(repository)

        val series = (viewModel.uiState.value as CareerStrategyUiState.Success)
            .strategy.diminishingReturns.first()
        assertTrue(!series.isSufficient)
        assertNull(series.mean)
        assertNull(series.trend)
        assertNull(series.latest)
    }

    @Test
    fun `no diminishing-return data and no lock means nothing to show`() {
        val repository = FakeCareerStrategyRepository().apply { strategyResult = { strategy() } }
        val viewModel = viewModelWith(repository)

        val loaded = (viewModel.uiState.value as CareerStrategyUiState.Success).strategy
        assertTrue(loaded.diminishingReturns.isEmpty())
        assertTrue(!loaded.lockedSections.contains("diminishing_returns"))
    }
}
