package com.aiplacementos.app.ui.notifications

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.database.entity.NotificationEntity
import com.aiplacementos.app.fakes.FakeNotificationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotificationCenterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `notifications are populated eagerly without waiting for a real subscriber`() {
        val repository = FakeNotificationRepository().apply {
            setNotifications(listOf(NotificationEntity(id = 1, title = "New roadmap", body = null, receivedAt = 0L)))
        }

        val viewModel = NotificationCenterViewModel(repository)

        assertEquals(1, viewModel.notifications.value.size)
    }

    @Test
    fun `markAllRead marks every notification read`() {
        val repository = FakeNotificationRepository().apply {
            setNotifications(
                listOf(
                    NotificationEntity(id = 1, title = "A", body = null, receivedAt = 0L, read = false),
                    NotificationEntity(id = 2, title = "B", body = null, receivedAt = 0L, read = false),
                )
            )
        }
        val viewModel = NotificationCenterViewModel(repository)

        viewModel.markAllRead()

        assertTrue(repository.markAllReadCalled)
        assertTrue(viewModel.notifications.value.all { it.read })
    }
}
