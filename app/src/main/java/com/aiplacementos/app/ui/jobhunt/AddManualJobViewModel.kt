package com.aiplacementos.app.ui.jobhunt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.OperationKeyHolder
import com.aiplacementos.app.data.remote.SavedJobDto
import com.aiplacementos.app.domain.repository.JobHuntRepository
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * The form for a job the student found somewhere this app cannot search.
 *
 * Every field lives in the state rather than in the composable, which is what makes the
 * failure path honest: a refused submit leaves everything the student typed exactly where it
 * was, and the only thing that changes is the message above the button. A form that cleared
 * itself on a network blip would cost them the job description they had just pasted.
 */
data class AddManualJobUiState(
    val title: String = "",
    val company: String = "",
    val location: String = "",
    val url: String = "",
    val description: String = "",
    /** Where this application actually is today. A student who finds this product after
     * already interviewing must be able to say so rather than record something untrue. */
    val stage: ApplicationStage = ApplicationStage.Saved,
    val titleError: String? = null,
    val submitError: String? = null,
    val isSubmitting: Boolean = false,
    /** Set exactly once, when the server has confirmed the job is tracked. The screen keys its
     * navigation off this rather than off the submit call returning, so it can never move on
     * from a request that failed. */
    val added: SavedJobDto? = null,
) {
    /** Whether the button does anything. Deliberately not the same question as "is the form
     * valid" -- the title check below produces a message, this only greys the control. */
    val canSubmit: Boolean get() = !isSubmitting && title.isNotBlank()
}

/** Lenient on purpose: this reads an *error* body, and a handler that throws is worse than the
 * error it was handling. Same reasoning as JobHuntErrors.errorJson. */
private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * The server's own sentence for a refused field, or null.
 *
 * Preferred over a hardcoded string because only the server knows why it refused -- which link
 * schemes it will store, which length it stops at. Returns null for FastAPI's own schema
 * validation, where `detail` is a list of field errors rather than a sentence: those are the
 * cases the client already prevents, and rendering their raw shape would be worse than the
 * generic fallback.
 */
internal fun HttpException.fieldMessage(): String? = runCatching {
    val body = response()?.errorBody()?.string().orEmpty()
    errorJson.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content?.takeIf {
        it.isNotBlank()
    }
}.getOrNull()

class AddManualJobViewModel(
    private val jobHuntRepository: JobHuntRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddManualJobUiState())
    val uiState: StateFlow<AddManualJobUiState> = _uiState.asStateFlow()

    fun onTitleChange(value: String) = update {
        // The error clears as soon as they start fixing it, rather than staying on screen
        // until the next submit and reading as though the new text were wrong too.
        it.copy(title = value, titleError = null, submitError = null)
    }

    fun onCompanyChange(value: String) = update { it.copy(company = value, submitError = null) }

    fun onLocationChange(value: String) = update { it.copy(location = value, submitError = null) }

    fun onUrlChange(value: String) = update { it.copy(url = value, submitError = null) }

    fun onDescriptionChange(value: String) = update {
        it.copy(description = value, submitError = null)
    }

    fun onStageChange(stage: ApplicationStage) = update { it.copy(stage = stage) }

    /**
     * One key per distinct listing being added.
     *
     * The form's own isSubmitting flag already stops a second concurrent tap; this covers the
     * case that flag cannot see -- the request arrived, the listing was created, and the reply
     * was lost on the way back, so the transport asks again by itself. Keyed by the values
     * sent, so correcting a field and resending is a new listing while resending the same form
     * is the same one.
     */
    private val createKeys = OperationKeyHolder()

    fun submit() {
        val current = _uiState.value
        // One in-flight request at a time. Two taps on a form with no link would otherwise
        // produce two tracked applications, and nothing about them would distinguish the
        // accident from a student genuinely tracking two similar roles.
        if (current.isSubmitting) return
        if (current.title.isBlank()) {
            _uiState.value = current.copy(titleError = "Enter the job title.")
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isSubmitting = true, submitError = null)
            try {
                val title = current.title.trim()
                // Blank becomes absent rather than "". The server treats whitespace the
                // same way, but sending it would still be the client asserting a company
                // named one space.
                val company = current.company.trim().ifBlank { null }
                val location = current.location.trim().ifBlank { null }
                val url = current.url.trim().ifBlank { null }
                val description = current.description.trim().ifBlank { null }
                val status = current.stage.apiValue
                val identity = listOf(title, company, location, url, description, status)
                    .joinToString("\u0000") { it.orEmpty() }
                val saved = jobHuntRepository.addManualJob(
                    title = title,
                    company = company,
                    location = location,
                    url = url,
                    description = description,
                    status = status,
                    idempotencyKey = createKeys.keyFor(identity),
                )
                createKeys.consume(identity)
                update { it.copy(isSubmitting = false, added = saved) }
            } catch (e: Exception) {
                update {
                    it.copy(
                        isSubmitting = false,
                        // Everything they typed is still in `it`. Only the message is added.
                        submitError = (e as? HttpException)?.fieldMessage()
                            ?: e.toUserMessage("add this job"),
                    )
                }
            }
        }
    }

    private fun update(transform: (AddManualJobUiState) -> AddManualJobUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
