package com.aiplacementos.app.ui.careerpath

import com.aiplacementos.app.data.local.CurrentPathBefore
import com.aiplacementos.app.data.local.PendingCareerPathGeneration
import com.aiplacementos.app.data.local.PendingOperationStore
import com.aiplacementos.app.data.local.isCompletedBy
import com.aiplacementos.app.domain.repository.CareerPathRepository
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/**
 * How long, and how often, to look for a generation's result once its response has been lost.
 *
 * The deadline has to outlast the longest the server can still be working on the request: once
 * it passes, the student may send a new one, and a new request overlapping the old one is F12-03's
 * race. select_role() makes one structured AI call, for the skill explanations
 * (career_path/service._sync_path_children). On the dev stack each call is up to 3 attempts
 * (app/ai/retry.py _MAX_ATTEMPTS) of up to 240s each (core/config.py ollama_timeout_seconds), and
 * generate_structured asks once more after output that fails its schema: 2 x 3 x 240s = 24 min.
 * Thirty minutes clears that. For scale: 76-132s is what a generation measured on that stack, and
 * Phase 13 watched a single AI call spend the full 3 x 240s before failing.
 *
 * Not covered, and F12-06's to bound: a brand-new custom role makes a second AI call (its role
 * definition) before this one, and the production provider stacks its SDK's own retries under
 * these.
 *
 * Checks back off instead of polling: the first is immediate on every resume (a student returning
 * after a minute usually finds the result already there), then 5s, 10s, 15s and every 30s after
 * that. Results typically land one to two minutes in, which the close early checks cover; the
 * long tail is a server that is struggling, and asking it every few seconds only adds load.
 */
data class GenerationRecoveryTiming(
    val deadlineMillis: Long = 30 * 60_000L,
    val checkIntervalsMillis: List<Long> = listOf(5_000L, 10_000L, 15_000L, 30_000L),
) {
    fun intervalAfter(checksSoFar: Int): Long =
        checkIntervalsMillis[minOf(checksSoFar, checkIntervalsMillis.lastIndex)]
}

/** How a generation whose response was lost turned out. */
sealed interface GenerationOutcome {
    /** The requested role is the student's current path, built by this request. */
    data class Completed(val pathId: Int) : GenerationOutcome

    /** The deadline passed without the result appearing. Not a failure: nothing confirmed one. */
    data object Unconfirmed : GenerationOutcome
}

/**
 * Remembers a Career Path generation before it is sent, and finds its outcome afterwards from
 * the server's own state -- GET /career-path/current, the endpoint that already answers "what is
 * the student's current path" -- so a lost response is never mistaken for a failed request.
 *
 * One class so that the role-selection screen and app launch resolve a pending generation by the
 * same rules. It holds no state of its own: [PendingOperationStore] is the only record.
 */
class CareerPathGenerationRecovery(
    private val careerPathRepository: CareerPathRepository,
    private val store: PendingOperationStore,
    private val timing: GenerationRecoveryTiming = GenerationRecoveryTiming(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) },
) {
    /** The generation still waiting on an outcome, if any -- whichever process sent it. */
    suspend fun pending(): PendingCareerPathGeneration? = store.careerPathGeneration()

    /**
     * Records [requestedRole] as sent. Called immediately before the request goes out.
     *
     * Reads the current path first, because that is what the result is later told apart from.
     * If that read cannot reach the server, neither would the request: the IOException propagates
     * and nothing is recorded or sent. Any other failure to read it still lets the request go --
     * see [CurrentPathBefore.Unknown].
     */
    suspend fun begin(requestedRole: String): PendingCareerPathGeneration {
        val before = try {
            careerPathRepository.getCurrent()
                ?.let { CurrentPathBefore.Path(it.id, it.updatedAt) }
                ?: CurrentPathBefore.None
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            CurrentPathBefore.Unknown
        }
        val pending = PendingCareerPathGeneration(
            operationId = UUID.randomUUID().toString(),
            requestedRole = requestedRole,
            startedAtMillis = clock(),
            before = before,
        )
        store.saveCareerPathGeneration(pending)
        return pending
    }

    /**
     * The outcome is known -- the request answered, or its result was found -- so there is
     * nothing left to look for.
     *
     * Best effort: if the record cannot be cleared it only outlives its outcome, and the next
     * check finds the same result again. Failing here instead would crash the screen over it.
     */
    suspend fun end(pending: PendingCareerPathGeneration) {
        try {
            store.clearCareerPathGeneration(pending.operationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Deliberately ignored -- see above.
        }
    }

    /**
     * Looks for [pending]'s result until it appears or the deadline passes, then ends the record
     * either way.
     *
     * The deadline is read before each check, so the last check always happens after it: a
     * result that lands in the final interval is still found. A check that cannot be answered --
     * no connection, an error from the server -- is simply not a result; it says nothing about
     * how the generation went.
     */
    suspend fun awaitOutcome(pending: PendingCareerPathGeneration): GenerationOutcome {
        var checks = 0
        while (true) {
            val lastCheck = isPastDeadline(pending)
            val current = try {
                careerPathRepository.getCurrent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (current != null && pending.isCompletedBy(current)) {
                end(pending)
                return GenerationOutcome.Completed(current.id)
            }
            if (lastCheck) {
                end(pending)
                return GenerationOutcome.Unconfirmed
            }
            pause(timing.intervalAfter(checks++))
        }
    }

    /**
     * App launch: ends a record whose deadline has passed, and touches nothing else -- one read
     * of the local record, no request and no loop.
     *
     * Past the deadline the server has stopped working on the request, so a new one can no
     * longer overlap it, and a record nobody resolved would otherwise outlive it indefinitely. A
     * record still inside its deadline is deliberately left alone, even if its result is already
     * on the server: the role-selection screen is what can show the student that result, and after
     * process death it is restored at the same moment this runs. Ending the record here first
     * would leave that screen with nothing pending and a plain catalog.
     */
    suspend fun resolveAtLaunch() {
        val pending = pending() ?: return
        if (isPastDeadline(pending)) end(pending)
    }

    /** Also true when the clock reads earlier than the send time: a clock moved backwards cannot
     * be trusted to say how long the server has had, so the record is treated as finished with. */
    fun isPastDeadline(pending: PendingCareerPathGeneration): Boolean {
        val age = clock() - pending.startedAtMillis
        return age < 0 || age >= timing.deadlineMillis
    }
}

/**
 * True only for failures that prove the request never reached the server: no route, no DNS
 * answer, no TCP connection. Every other IOException -- a connection reset or aborted when the
 * app left the foreground, a read timeout, a stream cut short -- can happen after the server has
 * the whole request, so it proves nothing about the outcome.
 *
 * The suppressed check matters: when a request fails on a reused connection, OkHttp retries it on
 * a new one and throws the last attempt's exception, attaching the earlier failures as suppressed.
 * A socket aborted mid-request followed by a reconnect that cannot get through (the app is in the
 * background) surfaces as a ConnectException -- about the retry, not the request that got out.
 */
internal fun IOException.provesRequestNotSent(): Boolean =
    isConnectFailure() && suppressed.all { it.isConnectFailure() }

private fun Throwable.isConnectFailure(): Boolean =
    this is ConnectException || this is UnknownHostException || this is NoRouteToHostException
