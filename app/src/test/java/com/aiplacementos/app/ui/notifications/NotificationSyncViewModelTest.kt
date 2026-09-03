package com.aiplacementos.app.ui.notifications

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.PendingNotificationDto
import com.aiplacementos.app.fakes.FakeNotificationRepository
import com.aiplacementos.app.fakes.FakeNotificationSyncRepository
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun samplePending(id: Int = 1, kind: String = "mission_completed") = PendingNotificationDto(
    id = id, kind = kind, title = "Mission Complete!", body = "You did it.", createdAt = "2026-09-03T11:20:26Z",
)

class NotificationSyncViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `syncing records each pending event locally and acks all of them`() {
        val syncRepository = FakeNotificationSyncRepository().apply {
            pendingResult = { listOf(samplePending(1), samplePending(2, "readiness_milestone")) }
        }
        val notificationRepository = FakeNotificationRepository()
        val viewModel = NotificationSyncViewModel(syncRepository, notificationRepository)

        viewModel.sync()

        assertEquals(2, notificationRepository.recorded.size)
        assertEquals(listOf(1, 2), syncRepository.lastAcknowledgedIds)
        assertEquals(2, viewModel.newlySynced.value.size)
    }

    @Test
    fun `nothing pending records nothing and acks nothing`() {
        val syncRepository = FakeNotificationSyncRepository().apply { pendingResult = { emptyList() } }
        val notificationRepository = FakeNotificationRepository()
        val viewModel = NotificationSyncViewModel(syncRepository, notificationRepository)

        viewModel.sync()

        assertTrue(notificationRepository.recorded.isEmpty())
        assertEquals(null, syncRepository.lastAcknowledgedIds)
    }

    @Test
    fun `a failed poll degrades to nothing synced rather than crashing`() {
        val syncRepository = FakeNotificationSyncRepository().apply {
            pendingResult = { throw UnknownHostException("no route") }
        }
        val notificationRepository = FakeNotificationRepository()
        val viewModel = NotificationSyncViewModel(syncRepository, notificationRepository)

        viewModel.sync()

        assertTrue(notificationRepository.recorded.isEmpty())
        assertTrue(viewModel.newlySynced.value.isEmpty())
    }

    @Test
    fun `a failed ack still leaves the events recorded locally and exposed as newly synced`() {
        val syncRepository = FakeNotificationSyncRepository().apply {
            pendingResult = { listOf(samplePending()) }
            acknowledgeResult = { throw UnknownHostException("no route") }
        }
        val notificationRepository = FakeNotificationRepository()
        val viewModel = NotificationSyncViewModel(syncRepository, notificationRepository)

        viewModel.sync()

        assertEquals(1, notificationRepository.recorded.size)
        assertEquals(1, viewModel.newlySynced.value.size)
    }

    @Test
    fun `consumed clears newlySynced`() {
        val syncRepository = FakeNotificationSyncRepository().apply { pendingResult = { listOf(samplePending()) } }
        val viewModel = NotificationSyncViewModel(syncRepository, FakeNotificationRepository())

        viewModel.sync()
        viewModel.consumed()

        assertTrue(viewModel.newlySynced.value.isEmpty())
    }
}
