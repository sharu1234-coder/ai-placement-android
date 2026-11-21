package com.aiplacementos.app.ui.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.domain.repository.CapabilitiesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Asks the server what it may offer, once, for whichever screen is about to offer it.
 *
 * Shared by every screen that presents a voice choice so the fail-safe rule lives in one
 * place. The initial value is [SpeechAvailability.Unavailable], not null and not "assume
 * voice": a screen composed before the answer arrives must render the typed path, because the
 * alternative is a microphone button that appears for a frame and then has to be taken away --
 * or worse, is tapped in that frame.
 */
class SpeechAvailabilityViewModel(private val capabilitiesRepository: CapabilitiesRepository) : ViewModel() {
    private val _availability = MutableStateFlow(SpeechAvailability.Unavailable)
    val availability: StateFlow<SpeechAvailability> = _availability.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // runCatching, not a try/catch that reports an error: there is no error state to
            // show here. Failing to learn the capabilities is not a failure of anything the
            // student asked for -- it just means the typed path is the one on offer.
            _availability.value = SpeechAvailability.fromResult(
                runCatching { capabilitiesRepository.getSpeechCapabilities() },
            )
        }
    }
}
