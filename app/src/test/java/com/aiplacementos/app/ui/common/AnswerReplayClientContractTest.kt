package com.aiplacementos.app.ui.common

import com.aiplacementos.app.MainDispatcherRule
import com.aiplacementos.app.data.remote.InterviewAnswerDto
import com.aiplacementos.app.data.remote.InterviewDto
import com.aiplacementos.app.data.remote.InterviewQuestionDto
import com.aiplacementos.app.data.remote.NextInterviewQuestionResponseDto
import com.aiplacementos.app.data.remote.NextRecruiterAttackTurnResponseDto
import com.aiplacementos.app.data.remote.RecruiterAttackTurnDto
import com.aiplacementos.app.fakes.FakeInterviewRepository
import com.aiplacementos.app.fakes.FakeRecruiterAttackRepository
import com.aiplacementos.app.ui.interview.TextInterviewUiState
import com.aiplacementos.app.ui.interview.TextInterviewViewModel
import com.aiplacementos.app.ui.recruiterattack.RecruiterAttackSessionUiState
import com.aiplacementos.app.ui.recruiterattack.RecruiterAttackSessionViewModel
import androidx.lifecycle.SavedStateHandle
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Phase 31, from the client's side of the same change.
 *
 * The server now answers a resent answer with the answer it stored, so the replay arrives here as
 * an ordinary 200 and no client code had to change. That is the point, and it is also the thing
 * most easily broken later: the tempting "fix" for the original complaint was to make the client
 * treat 409 as success, which would have hidden every genuine conflict -- a student answering a
 * question differently would be told their new answer was saved when the old one still stands.
 *
 * So these tests pin both halves at the client boundary: a success is shown as success, and a 409
 * is still an error. Nothing here asserts a key or a retry, because Phase 31 added neither.
 */
class AnswerReplayClientContractTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun conflict() = HttpException(
        Response.error<Any>(409, "".toResponseBody("application/json".toMediaType()))
    )

    private fun interview() =
        InterviewDto(id = 1, type = "technical", mode = "text", jdId = null, status = "in_progress")

    private fun question(id: Int = 101) =
        InterviewQuestionDto(id = id, orderIndex = 0, questionText = "Explain X", basedOn = "DSA")

    private fun turn(id: Int = 7, answer: String? = null) = RecruiterAttackTurnDto(
        id = id, orderIndex = 0, questionText = "Which part did you write?",
        basedOn = "Payments service", answerText = answer,
    )

    // --- the reconciled replay: an ordinary success, shown as success ---

    @Test
    fun `an interview answer the server reconciles is not shown as an error`() {
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> interview() }
            nextQuestionResult = { NextInterviewQuestionResponseDto(completed = false, question = question()) }
            // Exactly what a reconciled replay looks like over the wire: 200, carrying the answer
            // that was already stored.
            submitAnswerResult = { _, questionId, text -> InterviewAnswerDto(questionId, text) }
        }
        val viewModel = TextInterviewViewModel(0, "technical", repository, SavedStateHandle())

        viewModel.submitAnswer("My answer.")

        val state = viewModel.uiState.value
        assertFalse("a reconciled replay was surfaced as a failure", state is TextInterviewUiState.Error)
    }

    @Test
    fun `an attack answer the server reconciles is not shown as an error`() {
        val repository = FakeRecruiterAttackRepository().apply {
            nextTurnResult = { NextRecruiterAttackTurnResponseDto(completed = false, turn = turn()) }
            answerResult = { _, turnId, text -> turn(turnId, text) }
        }
        val viewModel = RecruiterAttackSessionViewModel(1, repository)

        viewModel.submitAnswer("I wrote the ledger.")

        assertFalse(
            "a reconciled replay was surfaced as a failure",
            viewModel.uiState.value is RecruiterAttackSessionUiState.Error,
        )
    }

    // --- a genuine conflict: still an error ---

    @Test
    fun `a genuine conflict on an interview answer is still an error`() {
        val repository = FakeInterviewRepository().apply {
            startResult = { _, _, _ -> interview() }
            nextQuestionResult = { NextInterviewQuestionResponseDto(completed = false, question = question()) }
            submitAnswerResult = { _, _, _ -> throw conflict() }
        }
        val viewModel = TextInterviewViewModel(0, "technical", repository, SavedStateHandle())

        viewModel.submitAnswer("A different answer.")

        val state = viewModel.uiState.value
        assertTrue(
            "a 409 that means the answer could not be changed must stay an error -- telling the " +
                "student it was saved would be a lie about what the server holds",
            state is TextInterviewUiState.Error,
        )
    }

    @Test
    fun `a genuine conflict on an attack answer is still an error`() {
        val repository = FakeRecruiterAttackRepository().apply {
            nextTurnResult = { NextRecruiterAttackTurnResponseDto(completed = false, turn = turn()) }
            answerResult = { _, _, _ -> throw conflict() }
        }
        val viewModel = RecruiterAttackSessionViewModel(1, repository)

        viewModel.submitAnswer("A different answer.")

        assertTrue(
            "a 409 that means the answer could not be changed must stay an error",
            viewModel.uiState.value is RecruiterAttackSessionUiState.Error,
        )
    }

    // --- the classifier itself ---

    @Test
    fun `409 is still classified as a conflict and conflicts are not retryable`() {
        val kind = conflict().classify()
        assertEquals(ErrorKind.CONFLICT, kind)
        assertFalse("a conflict became retryable, which would re-send a refused answer", kind.isRetryable)
    }

    @Test
    fun `no production code turns a 409 into a success`() {
        // The source-level half of the same guarantee. The behavioural tests above cover the two
        // screens they construct; this one covers the rest of the app, including screens added
        // later, because the failure mode is a single `code() == 409 -> ` branch anywhere that
        // swallows the refusal instead of reporting it.
        val main = File(repoRoot(), "app/src/main/java/com/aiplacementos/app")
        val offenders = main.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) ->
                        val code = line.substringBefore("//")
                        "409" in code && Regex("""(->|\{)\s*(true|Unit|return@|success)""").containsMatchIn(code)
                    }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }
            .toList()

        assertEquals(
            "a 409 is being treated as a success somewhere. Phase 31 made the server return the " +
                "stored answer for a replay precisely so the client would not have to: a client " +
                "that reinterprets 409 cannot tell a replay from a refused edit.",
            emptyList<String>(),
            offenders,
        )
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/java/com/aiplacementos/app").isDirectory) return dir
            dir = dir.parentFile
        }
        throw IllegalStateException("could not find the app sources from ${File(".").absolutePath}")
    }
}
