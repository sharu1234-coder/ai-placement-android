package com.aiplacementos.app.ui.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.SubscriptionStatusDto
import com.aiplacementos.app.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** tier="premium" only when status="active" server-side (see subscription_service.py) -- both
 * are checked defensively rather than trusting tier alone. */
private fun SubscriptionStatusDto.isCurrentlyPremium(): Boolean = tier == "premium" && status == "active"

sealed interface PremiumStatusUiState {
    data object Loading : PremiumStatusUiState
    data class Loaded(val isPremium: Boolean) : PremiumStatusUiState
}

/**
 * The single shared source every PaywallGate call site reads from. Deliberately fail-closed:
 * a network failure or malformed response degrades to "not premium," never the reverse -- the
 * same "server-derived entitlement is authoritative, never trusted/guessed locally" rule
 * SubscriptionsViewModel already follows for the purchase flow itself. Loading also renders as
 * not-premium at the call site (see PaywallGate) rather than flashing gated content before the
 * real entitlement is known.
 */
class PremiumStatusViewModel(private val subscriptionRepository: SubscriptionRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<PremiumStatusUiState>(PremiumStatusUiState.Loading)
    val uiState: StateFlow<PremiumStatusUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val isPremium = runCatching { subscriptionRepository.getStatus() }
                .getOrNull()
                ?.isCurrentlyPremium()
                ?: false
            _uiState.value = PremiumStatusUiState.Loaded(isPremium)
        }
    }
}
