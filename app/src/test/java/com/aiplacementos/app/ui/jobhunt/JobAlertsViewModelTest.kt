package com.aiplacementos.app.ui.jobhunt

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.JobAlertDto
import com.aiplacementos.app.data.remote.JobAlertQuotaDto
import com.aiplacementos.app.fakes.FakeJobHuntRepository
import com.aiplacementos.app.fakes.httpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

fun alertDto(
    id: Int = 1,
    name: String = "Remote Python",
    isEnabled: Boolean = false,
    frequency: String = "daily",
    dayOfWeek: Int? = null,
    hourLocal: Int = 9,
    quietStart: Int? = null,
    quietEnd: Int? = null,
    lastRunStatus: String? = null,
    lastNewJobCount: Int? = null,
) = JobAlertDto(
    id = id,
    name = name,
    frequency = frequency,
    dayOfWeek = dayOfWeek,
    hourLocal = hourLocal,
    quietHoursStart = quietStart,
    quietHoursEnd = quietEnd,
    isEnabled = isEnabled,
    lastRunStatus = lastRunStatus,
    lastNewJobCount = lastNewJobCount,
    createdAt = "2026-09-08T10:00:00",
)

private fun repositoryWith(
    alerts: List<JobAlertDto> = listOf(alertDto()),
    quota: JobAlertQuotaDto = JobAlertQuotaDto(used = alerts.size, limit = 1, isPremium = false),
) = FakeJobHuntRepository().apply {
    alertsResult = { alerts }
    alertQuotaResult = { quota }
    alertHistoryResult = { emptyList() }
}

class JobAlertsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `alerts and quota load together`() {
        val vm = JobAlertsViewModel(repositoryWith())
        val state = vm.uiState.value as JobAlertsUiState.Success

        assertEquals(1, state.alerts.size)
        assertEquals(1, state.quota.limit)
    }

    @Test
    fun `a failing history does not block managing alerts`() {
        // History is a nice-to-have. Losing it must not cost the student the switches.
        val repository = repositoryWith().apply {
            alertHistoryResult = { throw RuntimeException("boom") }
        }
        val state = JobAlertsViewModel(repository).uiState.value

        assertTrue(state is JobAlertsUiState.Success)
        assertTrue((state as JobAlertsUiState.Success).history.isEmpty())
        assertEquals(1, state.alerts.size)
    }

    @Test
    fun `toggling consent only reports on after the server agrees`() {
        // A notification switch that shows "on" before the server accepted it is the one
        // control in the app that must never lie.
        val repository = repositoryWith(alerts = listOf(alertDto(isEnabled = false))).apply {
            updateAlertResult = { _, body -> alertDto(isEnabled = body.isEnabled == true) }
        }
        val vm = JobAlertsViewModel(repository)
        vm.setEnabled(1, true)

        val state = vm.uiState.value as JobAlertsUiState.Success
        assertTrue(state.alerts.first().isEnabled)
    }

    @Test
    fun `a failed toggle leaves the switch where it was and says why`() {
        val repository = repositoryWith(alerts = listOf(alertDto(isEnabled = false))).apply {
            updateAlertResult = { _, _ -> throw RuntimeException("boom") }
        }
        val vm = JobAlertsViewModel(repository)
        vm.setEnabled(1, true)

        val state = vm.uiState.value as JobAlertsUiState.Success
        assertFalse("consent must not appear granted after a failure", state.alerts.first().isEnabled)
        assertTrue(state.actionError != null)
    }

    @Test
    fun `deleting removes the alert and frees a slot`() {
        val repository = repositoryWith().apply { deleteAlertResult = { } }
        val vm = JobAlertsViewModel(repository)
        assertFalse((vm.uiState.value as JobAlertsUiState.Success).canCreate)

        vm.delete(1)
        val state = vm.uiState.value as JobAlertsUiState.Success
        assertTrue(state.alerts.isEmpty())
        assertTrue("the freed slot should be usable without a reload", state.canCreate)
    }

    @Test
    fun `a quota refusal becomes an upgrade prompt, not an error`() {
        // 403 here is an entitlement ceiling. Rendering it as a failure would tell the student
        // something is broken when the truth is that something costs money.
        val repository = repositoryWith().apply {
            createAlertResult = { _ -> throw httpException(403) }
        }
        val vm = JobAlertsViewModel(repository)
        vm.create("Another")

        val state = vm.uiState.value as JobAlertsUiState.Success
        assertTrue(state.upgradeMessage != null)
        assertNull(state.actionError)
    }

    @Test
    fun `a blank name creates nothing`() {
        val repository = repositoryWith()
        JobAlertsViewModel(repository).create("   ")
        assertNull(repository.lastCreatedSavedSearch)
    }

    @Test
    fun `an empty list is a real success, not an error`() {
        val repository = repositoryWith(
            alerts = emptyList(),
            quota = JobAlertQuotaDto(used = 0, limit = 1),
        )
        val state = JobAlertsViewModel(repository).uiState.value as JobAlertsUiState.Success

        assertTrue(state.isEmpty)
        assertTrue(state.canCreate)
    }
}

class AlertLabelTest {
    @Test
    fun `a daily alert states its hour`() {
        assertEquals("Daily · 9:00 AM", alertScheduleLabel(alertDto(hourLocal = 9)))
    }

    @Test
    fun `a weekly alert names its day`() {
        val label = alertScheduleLabel(alertDto(frequency = "weekly", dayOfWeek = 0, hourLocal = 8))
        assertEquals("Weekly · Monday · 8:00 AM", label)
    }

    @Test
    fun `an unrecognised frequency is not mislabelled as daily`() {
        // Telling a student "Daily" when the server said something else would be a false
        // statement about how often we will contact them.
        val label = alertScheduleLabel(alertDto(frequency = "hourly"))
        assertTrue(label.startsWith("hourly"))
    }

    @Test
    fun `hours render in twelve-hour form including the midnight and noon edges`() {
        assertEquals("12:00 AM", formatHour(0))
        assertEquals("11:00 AM", formatHour(11))
        assertEquals("12:00 PM", formatHour(12))
        assertEquals("10:00 PM", formatHour(22))
    }

    @Test
    fun `quiet hours only render when both ends are set`() {
        assertNull(quietHoursLabel(alertDto(quietStart = 22, quietEnd = null)))
        assertNull(quietHoursLabel(alertDto()))
        assertEquals(
            "Quiet 10:00 PM – 7:00 AM",
            quietHoursLabel(alertDto(quietStart = 22, quietEnd = 7)),
        )
    }
}

class AlertLastRunLabelTest {
    @Test
    fun `an alert that has never run says nothing at all`() {
        // "0 new jobs" would read as a verdict on the job market rather than as "we have not
        // looked yet".
        assertNull(alertLastRunLabel(alertDto(lastRunStatus = null)))
    }

    @Test
    fun `a successful run reports the count`() {
        assertEquals(
            "3 new jobs last time",
            alertLastRunLabel(alertDto(lastRunStatus = "success", lastNewJobCount = 3)),
        )
        assertEquals(
            "1 new job last time",
            alertLastRunLabel(alertDto(lastRunStatus = "success", lastNewJobCount = 1)),
        )
    }

    @Test
    fun `a provider failure is distinguished from an empty market`() {
        // These are different facts, and a student left to guess will assume the worse one.
        assertEquals("Nothing new last time", alertLastRunLabel(alertDto(lastRunStatus = "no_new_jobs")))
        assertEquals(
            "We couldn't check last time",
            alertLastRunLabel(alertDto(lastRunStatus = "provider_error")),
        )
    }

    @Test
    fun `a run skipped for quiet hours says so`() {
        assertEquals(
            "Skipped — it fell inside your quiet hours",
            alertLastRunLabel(alertDto(lastRunStatus = "skipped_quiet_hours")),
        )
    }

    @Test
    fun `an unrecognised status shows nothing rather than a raw enum`() {
        assertNull(alertLastRunLabel(alertDto(lastRunStatus = "something_new")))
    }
}
