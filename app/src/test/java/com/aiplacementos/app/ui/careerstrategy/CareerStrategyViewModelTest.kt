package com.aiplacementos.app.ui.careerstrategy

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.CareerStrategyDto
import com.aiplacementos.app.data.remote.JobStrategyDto
import com.aiplacementos.app.data.remote.StrategyBottleneckDto
import com.aiplacementos.app.data.remote.StrategyHistoryDto
import com.aiplacementos.app.data.remote.StrategySnapshotDto
import com.aiplacementos.app.data.remote.TargetComparisonDto
import com.aiplacementos.app.fakes.FakeCareerStrategyRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun sampleBottleneck(
    category: String = "INTERVIEW_GAP",
    title: String = "Interview clarity",
    recommendedAction: String = "Run a mock interview focused on clarity",
    destination: String = "interview",
    impact: String? = "very_high",
    effort: String? = "low",
    evidence: List<String> = listOf("Interview clarity has been flagged 3 times."),
    description: String? = "Your technical evidence is strong; your interview signal is not.",
) = StrategyBottleneckDto(
    id = "$category:clarity",
    category = category,
    subject = "interview:clarity",
    subjectKind = "interview_dimension",
    title = title,
    destination = destination,
    recommendedAction = recommendedAction,
    description = description,
    impact = impact,
    effort = effort,
    evidence = evidence,
)

internal fun sampleStrategy(
    state: String = "FOCUS_ON_INTERVIEW",
    isPremium: Boolean = true,
    lockedSections: List<String> = emptyList(),
    bottleneck: StrategyBottleneckDto? = sampleBottleneck(),
    stateReason: String = "Your technical side is holding up. How you come across is not.",
    signalCount: Int = 12,
    readinessPercent: Int? = 64,
    readinessReasons: List<String> = emptyList(),
) = CareerStrategyDto(
    isPremium = isPremium,
    lockedSections = lockedSections,
    state = state,
    stateReason = stateReason,
    readinessReasons = readinessReasons,
    strategyVersion = 2,
    hasRole = true,
    displayRoleName = "Data Engineer",
    roleSource = "target_role",
    readinessPercent = readinessPercent,
    signalCount = signalCount,
    bottleneck = bottleneck,
)

class CareerStrategyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModelWith(repository: FakeCareerStrategyRepository) =
        CareerStrategyViewModel(repository)

    // -- main screen state ---------------------------------------------------------------------

    @Test
    fun `shows Success with the strategy when one is available`() {
        val repository = FakeCareerStrategyRepository().apply { strategyResult = { sampleStrategy() } }
        val state = viewModelWith(repository).uiState.value

        assertTrue(state is CareerStrategyUiState.Success)
        assertEquals("FOCUS_ON_INTERVIEW", (state as CareerStrategyUiState.Success).strategy.state)
    }

    @Test
    fun `no target role is a real screen, not an error`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = {
                sampleStrategy(state = "FOCUS_ON_TARGET_ROLE", bottleneck = null, readinessPercent = null)
            }
        }
        val state = viewModelWith(repository).uiState.value
        assertTrue(state is CareerStrategyUiState.NoRole)
    }

    @Test
    fun `not enough data is a real screen, not an error`() {
        """A student with nothing recorded has not failed at anything. Turning this into an
        error would be the app blaming them for its own lack of data."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = {
                sampleStrategy(
                    state = "INSUFFICIENT_INFORMATION",
                    bottleneck = null,
                    readinessPercent = null,
                    signalCount = 1,
                )
            }
        }
        val state = viewModelWith(repository).uiState.value

        assertTrue(state is CareerStrategyUiState.InsufficientData)
        assertEquals(1, (state as CareerStrategyUiState.InsufficientData).signalCount)
    }

    @Test
    fun `the backend's own reason is shown, never a fabricated one`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = {
                sampleStrategy(
                    state = "FOCUS_ON_TARGET_ROLE",
                    bottleneck = null,
                    stateReason = "Choose a target role and the rest follows from it.",
                )
            }
        }
        val state = viewModelWith(repository).uiState.value as CareerStrategyUiState.NoRole
        assertEquals("Choose a target role and the rest follows from it.", state.reason)
    }

    @Test
    fun `a blank reason falls back to something usable rather than an empty screen`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy(state = "FOCUS_ON_TARGET_ROLE", bottleneck = null, stateReason = "") }
        }
        val state = viewModelWith(repository).uiState.value as CareerStrategyUiState.NoRole
        assertTrue(state.reason.isNotBlank())
    }

    @Test
    fun `a free response is still a Success, with the gated sections named`() {
        """The strategy screen is free. What is Premium are sections within it, and the client
        must render the free version rather than a paywall over everything."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = {
                sampleStrategy(
                    isPremium = false,
                    lockedSections = listOf("queue", "evidence", "contradictions"),
                    bottleneck = sampleBottleneck(impact = null, effort = null, evidence = emptyList(), description = null),
                )
            }
        }
        val state = viewModelWith(repository).uiState.value as CareerStrategyUiState.Success

        assertFalse(state.strategy.isPremium)
        assertTrue(state.strategy.lockedSections.contains("queue"))
        // The free half is genuinely useful: the name of the constraint and one thing to do.
        assertEquals("Interview clarity", state.strategy.bottleneck?.title)
        assertTrue(state.strategy.bottleneck?.recommendedAction?.isNotBlank() == true)
        // And the premium half is genuinely absent, not blanked out client-side.
        assertNull(state.strategy.bottleneck?.impact)
        assertTrue(state.strategy.bottleneck?.evidence.isNullOrEmpty())
    }

    @Test
    fun `unknown readiness stays null rather than becoming zero`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy(readinessPercent = null) }
        }
        val state = viewModelWith(repository).uiState.value as CareerStrategyUiState.Success
        assertNull(state.strategy.readinessPercent)
    }

    // -- error classification -------------------------------------------------------------------

    @Test
    fun `connectivity failure is flagged as a network error`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { throw UnknownHostException("no route") }
        }
        val state = viewModelWith(repository).uiState.value as CareerStrategyUiState.Error
        assertTrue(state.isNetworkError)
        assertTrue(state.message.contains("connection", ignoreCase = true))
    }

    @Test
    fun `timeout gets its own message, not the connectivity one`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { throw SocketTimeoutException("slow") }
        }
        val state = viewModelWith(repository).uiState.value as CareerStrategyUiState.Error
        assertFalse(state.isNetworkError)
        assertTrue(state.message.contains("too long", ignoreCase = true))
    }

    @Test
    fun `expired session asks the student to sign in again`() {
        val repository = FakeCareerStrategyRepository().apply { strategyResult = { throw httpException(401) } }
        val state = viewModelWith(repository).uiState.value as CareerStrategyUiState.Error
        assertTrue(state.message.contains("session", ignoreCase = true))
    }

    // -- premium sections -------------------------------------------------------------------------

    @Test
    fun `a 403 on history is a paywall, not an error`() {
        """The server refusing on entitlement grounds is correct behaviour. Rendering "something
        went wrong" for it would be both wrong and alarming."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            historyResult = { throw httpException(403) }
        }
        val viewModel = viewModelWith(repository)
        viewModel.loadHistory()

        assertEquals(StrategySectionState.Locked, viewModel.history.value)
    }

    @Test
    fun `a 403 on target comparison is a paywall, not an error`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            targetsResult = { throw httpException(403) }
        }
        val viewModel = viewModelWith(repository)
        viewModel.loadTargets()

        assertEquals(StrategySectionState.Locked, viewModel.targets.value)
    }

    @Test
    fun `a real server failure on history is still an error`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            historyResult = { throw httpException(500) }
        }
        val viewModel = viewModelWith(repository)
        viewModel.loadHistory()

        assertTrue(viewModel.history.value is StrategySectionState.Error)
    }

    @Test
    fun `history loads with its own change lines so no per-row request is needed`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            historyResult = {
                StrategyHistoryDto(
                    snapshots = listOf(
                        StrategySnapshotDto(
                            id = 2, sequence = 2, state = "FOCUS_ON_INTERVIEW", strategyVersion = 2,
                            createdAt = "2026-09-10T10:00:00Z",
                            changeSummary = listOf("Your main focus changed from SQL to Interview clarity."),
                        ),
                    ),
                )
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.loadHistory()

        val loaded = viewModel.history.value as StrategySectionState.Loaded
        assertEquals(1, loaded.data.snapshots.size)
        assertTrue(loaded.data.snapshots.first().changeSummary.isNotEmpty())
    }

    @Test
    fun `a locked target comparison arrives as data, not as a 403`() {
        """The targets endpoint is free-callable and returns a locked shell, so the screen has
        something to render an upgrade prompt around."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            targetsResult = { TargetComparisonDto(isPremium = false, locked = true, maxTargets = 3) }
        }
        val viewModel = viewModelWith(repository)
        viewModel.loadTargets()

        val loaded = viewModel.targets.value as StrategySectionState.Loaded
        assertTrue(loaded.data.locked)
        assertTrue(loaded.data.targets.isEmpty())
    }

    @Test
    fun `setting targets sends exactly the role ids given`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            setTargetsResult = { TargetComparisonDto(isPremium = true, maxTargets = 3) }
        }
        val viewModel = viewModelWith(repository)
        viewModel.setTargets(listOf(4, 7))

        assertEquals(listOf(4, 7), repository.lastSetRoleIds)
        assertTrue(viewModel.targets.value is StrategySectionState.Loaded)
    }

    // -- job strategy ------------------------------------------------------------------------------

    @Test
    fun `job analysis requests the listing the student chose`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(
                    listingId = id, jobTitle = "Data Engineer", decision = "FIX_BEFORE_APPLYING",
                    headline = "You are close.",
                )
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(42)

        assertEquals(42, repository.lastRequestedListingId)
        val loaded = viewModel.jobStrategy.value as StrategySectionState.Loaded
        assertEquals("FIX_BEFORE_APPLYING", loaded.data.decision)
    }

    @Test
    fun `unclear skills stay separate from missing skills`() {
        """"We don't know" is not "you don't have it". Merging them on the client would undo the
        distinction the backend goes out of its way to preserve."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(
                    listingId = id, jobTitle = "Data Engineer", decision = "INSUFFICIENT_INFORMATION",
                    headline = "Add your skills and we can tell you.",
                    missingSkills = emptyList(), unclearSkills = listOf("Kafka"),
                )
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(7)

        val loaded = viewModel.jobStrategy.value as StrategySectionState.Loaded
        assertEquals("INSUFFICIENT_INFORMATION", loaded.data.decision)
        assertTrue(loaded.data.missingSkills.isEmpty())
        assertEquals(listOf("Kafka"), loaded.data.unclearSkills)
        // And no readiness number is shown for a decision that refused to stand behind one.
        assertNull(loaded.data.readinessPercent)
    }

    @Test
    fun `a missing listing reports that it is gone rather than showing an empty card`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            jobStrategyResult = { throw httpException(404) }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(99)

        val error = viewModel.jobStrategy.value as StrategySectionState.Error
        assertTrue(error.message.contains("couldn't find", ignoreCase = true))
    }

    @Test
    fun `job analysis behind the paywall is locked, not broken`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            jobStrategyResult = { throw httpException(403) }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(7)

        assertEquals(StrategySectionState.Locked, viewModel.jobStrategy.value)
    }

    @Test
    fun `clearing job analysis returns the section to idle`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy() }
            jobStrategyResult = { id ->
                JobStrategyDto(listingId = id, jobTitle = "X", decision = "APPLY_NOW", headline = "Go.")
            }
        }
        val viewModel = viewModelWith(repository)
        viewModel.analyzeJob(1)
        viewModel.clearJobStrategy()

        assertEquals(StrategySectionState.Idle, viewModel.jobStrategy.value)
    }


    @Test
    fun `readiness arrives with the working shown`() {
        """Career Twin's own explanation of its own number, carried through. A percentage with
        nothing behind it is a grade the student has no way to check."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = {
                sampleStrategy(
                    readinessPercent = 64,
                    readinessReasons = listOf("3 verified, 1 strong out of 6.", "2 skills still need real evidence."),
                )
            }
        }
        val viewModel = viewModelWith(repository)

        val strategy = (viewModel.uiState.value as CareerStrategyUiState.Success).strategy
        assertEquals(64, strategy.readinessPercent)
        assertEquals(2, strategy.readinessReasons.size)
    }

    @Test
    fun `a free student still gets readiness and its reasons`() {
        """The free tier has to be genuinely useful. These come from /career-twin, which is not
        gated, so withholding them here would hide from one screen what the same student reads on
        another -- an inconsistency, not a paywall."""
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = {
                sampleStrategy(
                    isPremium = false,
                    lockedSections = listOf("queue", "diminishing_returns", "contradictions"),
                    readinessReasons = listOf("3 verified, 1 strong out of 6."),
                )
            }
        }
        val viewModel = viewModelWith(repository)

        val strategy = (viewModel.uiState.value as CareerStrategyUiState.Success).strategy
        assertTrue(strategy.readinessReasons.isNotEmpty())
        assertEquals(64, strategy.readinessPercent)
        // And nothing gated came with them.
        assertTrue(strategy.queue.isEmpty())
        assertTrue(strategy.diminishingReturns.isEmpty())
    }

    @Test
    fun `unknown readiness carries no reasons and no zero`() {
        val repository = FakeCareerStrategyRepository().apply {
            strategyResult = { sampleStrategy(readinessPercent = null) }
        }
        val viewModel = viewModelWith(repository)

        val strategy = (viewModel.uiState.value as CareerStrategyUiState.Success).strategy
        assertNull(strategy.readinessPercent)
        assertTrue(strategy.readinessReasons.isEmpty())
    }

    @Test
    fun `sections start idle so nothing loads until the student asks`() {
        """Each of these costs a real request. Loading them with the screen would triple the
        cost of opening it for data most students will not scroll to."""
        val repository = FakeCareerStrategyRepository().apply { strategyResult = { sampleStrategy() } }
        val viewModel = viewModelWith(repository)

        assertEquals(StrategySectionState.Idle, viewModel.history.value)
        assertEquals(StrategySectionState.Idle, viewModel.targets.value)
        assertEquals(StrategySectionState.Idle, viewModel.jobStrategy.value)
    }
}
