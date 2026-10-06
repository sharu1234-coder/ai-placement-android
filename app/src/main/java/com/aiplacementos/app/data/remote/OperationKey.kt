package com.aiplacementos.app.data.remote

import java.util.UUID

/**
 * One key per logical operation the student started -- never one per network attempt.
 *
 * The distinction is the whole point. OkHttp re-sends a request whose connection failed after it
 * was written, because it cannot tell that from a connection that was already dead, and it must
 * keep doing so: Phase 24 measured what happens when that retry is switched off, and a stale
 * pooled connection turned into "Couldn't reach the server" on the first tap after the phone had
 * been idle. So the retry stays, and the server collapses the duplicate instead -- which only
 * works if the second arrival carries the same key as the first.
 *
 * It does, for free, on the automatic retry: OkHttp resends the same [okhttp3.Request], headers
 * included. What this file exists to protect against is the *other* way the key can change --
 * minting a new one somewhere that runs more than once per intent, like a ViewModel's `init` or
 * a Compose effect that re-runs after a configuration change. Hence [newKey] is called where the
 * intent begins and the result is held, not called where the request is sent.
 *
 * Where to hold it, in order of preference:
 *
 *  - a field, when the operation cannot outlive the object holding it;
 *  - `SavedStateHandle`, when the operation must survive the ViewModel being recreated;
 *  - `PendingOperationStore`, only when it must survive process death *and* needs reconciliation
 *    afterwards -- Career Path generation is the one operation in this app that does.
 *
 * The prefix makes a key recognisable in a server log, and the length stays inside the 64
 * characters the server accepts (`and-` plus 32 hex is 36).
 */
object OperationKey {
    private const val PREFIX = "and-"

    fun newKey(): String = (PREFIX + UUID.randomUUID().toString().replace("-", "")).take(64)
}

/**
 * Holds the key for an operation until that operation succeeds, one key per logical identity.
 *
 * The identity is the operation's own mutation-defining inputs -- the skill being added, the
 * challenge being started, the name the search is saved under. It is not decoration, and a
 * single-slot field is not an adequate substitute for two separate reasons:
 *
 *  - The server hashes those same inputs into a request fingerprint, and a key already used for
 *    a *different* fingerprint is answered with 409, not a replay. So a screen that keeps one
 *    key, fails, lets the student correct the form and resend, turns a legitimate second attempt
 *    into an error. Keying by identity means an edited retry is correctly a new operation while
 *    an unedited retry is correctly the same one.
 *  - A student who starts A, fails, starts B, then returns to A must get A's original key back.
 *    With one slot, B evicts A, and A's retry becomes a second logical operation -- which is the
 *    exact duplicate this whole mechanism exists to prevent.
 *
 * Bounded by the distinct operations the student actually touched on one screen, and each entry
 * is removed as soon as its operation succeeds. Operations whose inputs cannot vary (a refresh
 * of a fixed id, a generate that takes no parameters) use the default identity and read as a
 * plain key/consume pair.
 *
 * In-memory by default. That survives rotation, because a ViewModel does; it does not survive
 * process death, which is correct for an operation the student has to tap to start again --
 * there is nothing in flight to deduplicate against.
 *
 * An operation that really can outlive its ViewModel passes [restore] and [persist] to keep the
 * whole identity-to-key map somewhere that does survive. Checkout is the case that needs it:
 * Razorpay's Checkout is a separate Activity, so this ViewModel can be recreated mid-purchase,
 * and a fresh key on return would be a second live order. The accessors are plain lambdas rather
 * than a SavedStateHandle parameter so that this file stays free of lifecycle types -- the
 * ViewModel that owns the handle supplies the two lines that read and write it.
 *
 * An operation whose own `init` restarts it after process death holds its single key directly in
 * `SavedStateHandle`, as the assessment and interview screens do; there is only ever one identity
 * on those screens, so a map would buy nothing.
 */
class OperationKeyHolder(
    private val restore: () -> Map<String, String> = { emptyMap() },
    private val persist: (Map<String, String>) -> Unit = {},
) {
    private val keys = LinkedHashMap<String, String>().apply { putAll(restore()) }

    fun keyFor(identity: String = DEFAULT): String {
        keys[identity]?.let { return it }
        return OperationKey.newKey().also {
            keys[identity] = it
            persist(keys)
        }
    }

    /** Called once the operation genuinely succeeded, so the next deliberate one is new. */
    fun consume(identity: String = DEFAULT) {
        if (keys.remove(identity) != null) {
            persist(keys)
        }
    }

    private companion object {
        const val DEFAULT = ""
    }
}
