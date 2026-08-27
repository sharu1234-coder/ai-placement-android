package com.aiplacementos.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.database.entity.NotificationEntity
import com.aiplacementos.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationCenterViewModel(private val repository: NotificationRepository) : ViewModel() {
    val notifications: StateFlow<List<NotificationEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun markRead(notification: NotificationEntity) {
        if (notification.read) return
        viewModelScope.launch { repository.markRead(notification.id) }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead() }
    }
}
