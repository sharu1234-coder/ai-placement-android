package com.aiplacementos.app.ui.jobprep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiplacementos.app.data.remote.JobDescriptionListItemDto
import com.aiplacementos.app.data.remote.ResumeSummaryDto
import com.aiplacementos.app.domain.repository.JobRepository
import com.aiplacementos.app.domain.repository.ResumeRepository
import com.aiplacementos.app.ui.common.UiState
import com.aiplacementos.app.ui.common.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Matches the server's own ceiling on a pasted job description. Checked here so an over-long
 * paste is refused instantly, with a count the student can see, instead of after a round trip. */
const val MAX_JD_CHARACTERS = 20_000

/** The shortest text that could plausibly be a job description. Pasting a job title alone is
 * the common mistake, and it produces a confident-looking analysis of almost nothing. */
private const val MIN_JD_CHARACTERS = 80

/** The server caps JD uploads at 5MB. Checked before reading the file into memory. */
private const val MAX_JD_UPLOAD_BYTES = 5 * 1024 * 1024

/** How the student is supplying the job description. */
enum class JdInputMode { PASTE, PDF }

/**
 * The input half of Job Match & Prep: choose how the job description arrives, confirm which
 * resume it will be compared against, and start the analysis.
 *
 * Analysis is two server calls (parse, then generate the plan) because that is what the API
 * genuinely is -- the screen shows the stage it has reached rather than inventing progress, and
 * the result screen is opened only once a plan actually exists.
 */
class JobMatchPrepViewModel(
    private val jobRepository: JobRepository,
    private val resumeRepository: ResumeRepository,
) : ViewModel() {

    /** Which stage the analysis has reached, so the screen can say something true while it
     * waits. Null means nothing is running. */
    enum class AnalysisStage { READING_PDF, ANALYSING_JOB, BUILDING_PLAN }

    private val _pastAnalyses = MutableStateFlow<UiState<List<JobDescriptionListItemDto>>>(UiState.Loading)
    val pastAnalyses: StateFlow<UiState<List<JobDescriptionListItemDto>>> = _pastAnalyses.asStateFlow()

    private val _resume = MutableStateFlow<ResumeSummaryDto?>(null)
    val resume: StateFlow<ResumeSummaryDto?> = _resume.asStateFlow()

    private val _resumeChecked = MutableStateFlow(false)
    val resumeChecked: StateFlow<Boolean> = _resumeChecked.asStateFlow()

    private val _stage = MutableStateFlow<AnalysisStage?>(null)
    val stage: StateFlow<AnalysisStage?> = _stage.asStateFlow()

    private val _analyzeError = MutableStateFlow<String?>(null)
    val analyzeError: StateFlow<String?> = _analyzeError.asStateFlow()

    /** No init{} load: the screen re-reads on every resume (LifecycleResumeEffect), including
     * first composition, so returning from an upload or a deletion reflects immediately. */
    fun load() {
        loadPastAnalyses()
        loadResume()
    }

    fun loadPastAnalyses() {
        viewModelScope.launch {
            _pastAnalyses.value = UiState.Loading
            try {
                val analyses = jobRepository.listJobDescriptions()
                _pastAnalyses.value = if (analyses.isEmpty()) UiState.Empty else UiState.Success(analyses)
            } catch (e: Exception) {
                _pastAnalyses.value = UiState.Error(e.toUserMessage("load your past analyses"))
            }
        }
    }

    private fun loadResume() {
        viewModelScope.launch {
            try {
                _resume.value = resumeRepository.getLatestResume()
            } catch (e: Exception) {
                // A resume we cannot read is reported as "none selected" rather than as a
                // failure of this screen: analysis works either way, and the result itself
                // says which parts needed a resume.
                _resume.value = null
            } finally {
                _resumeChecked.value = true
            }
        }
    }

    fun clearError() {
        _analyzeError.value = null
    }

    /**
     * Validates, analyses, and generates the plan, then hands the caller the new jd id.
     *
     * Guarded against a second concurrent run: a double tap, or a recomposition that re-fires
     * the click, must not spend two AI generations (and two of the student's twenty per hour).
     */
    fun analyze(
        mode: JdInputMode,
        rawText: String,
        pdf: PickedPdf?,
        // The bytes are read by the caller rather than here: reading them needs a
        // ContentResolver and a Uri, and keeping both out of this class is what lets its
        // behaviour be unit-tested at all.
        readPdfBytes: suspend () -> ByteArray,
        companyName: String,
        jobTitle: String,
        onAnalyzed: (jdId: Int) -> Unit,
    ) {
        if (_stage.value != null) return

        val validationError = validate(mode, rawText, pdf)
        if (validationError != null) {
            _analyzeError.value = validationError
            return
        }

        viewModelScope.launch {
            _analyzeError.value = null
            try {
                val parsed = when (mode) {
                    JdInputMode.PASTE -> {
                        _stage.value = AnalysisStage.ANALYSING_JOB
                        jobRepository.parseJobDescription(rawText.trim())
                    }
                    JdInputMode.PDF -> {
                        _stage.value = AnalysisStage.READING_PDF
                        val picked = requireNotNull(pdf)
                        val bytes = readPdfBytes()
                        _stage.value = AnalysisStage.ANALYSING_JOB
                        jobRepository.uploadJobDescription(picked.name, bytes)
                    }
                }

                _stage.value = AnalysisStage.BUILDING_PLAN
                jobRepository.generatePreparationPlan(
                    parsed.id,
                    companyName.trim().ifBlank { null },
                    jobTitle.trim().ifBlank { null },
                )
                onAnalyzed(parsed.id)
            } catch (e: HttpException) {
                _analyzeError.value = when (e.code()) {
                    413 -> "That PDF is too large (max 5MB)."
                    415 -> "That file isn't a PDF. Choose a PDF, or paste the text instead."
                    422 -> "We couldn't read that job description. It may be a scan -- try pasting the text."
                    429 -> "You've analysed a lot of jobs recently. Try again in a little while."
                    else -> e.toUserMessage("analyse that job description")
                }
            } catch (e: Exception) {
                _analyzeError.value = e.toUserMessage("analyse that job description")
            } finally {
                _stage.value = null
            }
        }
    }

    private fun validate(mode: JdInputMode, rawText: String, pdf: PickedPdf?): String? = when (mode) {
        JdInputMode.PASTE -> {
            val text = rawText.trim()
            when {
                text.isEmpty() -> "Paste the job description first."
                text.length < MIN_JD_CHARACTERS ->
                    "That looks too short to be a full job description. Paste the whole posting."
                text.length > MAX_JD_CHARACTERS ->
                    "That job description is too long (limit ${MAX_JD_CHARACTERS} characters)."
                else -> null
            }
        }
        JdInputMode.PDF -> when {
            pdf == null -> "Choose a PDF of the job description first."
            // The picker filters by type, not size, so this is checked before the file is
            // read into memory rather than after the server rejects it.
            pdf.sizeBytes != null && pdf.sizeBytes > MAX_JD_UPLOAD_BYTES -> "That PDF is too large (max 5MB)."
            else -> null
        }
    }

    fun deleteAnalysis(jdId: Int) {
        viewModelScope.launch {
            try {
                jobRepository.deleteJobDescription(jdId)
                loadPastAnalyses()
            } catch (e: Exception) {
                // One failed delete is not worth replacing the whole list with an error; the
                // item stays and the student can try again.
            }
        }
    }
}

/** What the ViewModel needs to know about a picked PDF: no Uri, deliberately -- see analyze(). */
data class PickedPdf(val name: String, val sizeBytes: Long?)
