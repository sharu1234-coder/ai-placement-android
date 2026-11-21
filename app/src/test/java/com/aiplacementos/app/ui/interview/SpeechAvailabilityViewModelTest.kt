package com.aiplacementos.app.ui.interview

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.SpeechCapabilitiesDto
import com.aiplacementos.app.fakes.FakeCapabilitiesRepository
import com.aiplacementos.app.fakes.httpException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The propagation step: a server answer, or a failure to get one, becoming what a screen may
 * offer.
 */
class SpeechAvailabilityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a capable server allows a spoken interview`() {
        val repository = FakeCapabilitiesRepository(
            Result.success(SpeechCapabilitiesDto(transcribe = true, synthesize = true, realtimeVoice = true)),
        )

        val viewModel = SpeechAvailabilityViewModel(repository)

        assertTrue(viewModel.availability.value.canInterviewByVoice)
        assertTrue(viewModel.availability.value.resolved)
    }

    @Test
    fun `the production provider -- no speech -- allows no spoken interview`() {
        val repository = FakeCapabilitiesRepository(Result.success(SpeechCapabilitiesDto()))

        val viewModel = SpeechAvailabilityViewModel(repository)

        assertFalse(viewModel.availability.value.canInterviewByVoice)
        assertTrue(viewModel.availability.value.resolved)
    }

    @Test
    fun `a failure to reach the server allows no spoken interview and reports no error`() {
        val repository = FakeCapabilitiesRepository(Result.failure(IOException("offline")))

        val viewModel = SpeechAvailabilityViewModel(repository)

        assertFalse(viewModel.availability.value.canInterviewByVoice)
        // Not resolved, so a screen explains nothing it does not actually know.
        assertFalse(viewModel.availability.value.resolved)
    }

    @Test
    fun `a backend without the capability route allows no spoken interview`() {
        val repository = FakeCapabilitiesRepository(Result.failure(httpException(404)))

        val viewModel = SpeechAvailabilityViewModel(repository)

        assertFalse(viewModel.availability.value.canInterviewByVoice)
    }

    @Test
    fun `the server is asked once per load`() {
        val repository = FakeCapabilitiesRepository(Result.success(SpeechCapabilitiesDto()))

        val viewModel = SpeechAvailabilityViewModel(repository)
        assertEquals(1, repository.calls)

        viewModel.load()
        assertEquals(2, repository.calls)
    }
}
