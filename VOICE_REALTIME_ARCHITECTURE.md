# Realtime Voice Session (VA-2) — architecture note

Scope: VA-2 only. No agent orchestration, no Tool Registry, no model selection, no provider
choice (VA-3 onward). This note records the decisions that were made against the repository
rather than from preference, and the things that are still unproven.

## What VA-2 added

`app/src/main/java/com/aiplacementos/app/voice/realtime/`

| File | Responsibility |
|---|---|
`RealtimeTransport.kt` | The provider-neutral seam: session config, client/server events, typed errors. Names no vendor, no URL, no HTTP client |
`VoiceActivityDetector.kt` | On-device VAD behind an interface, plus turn detection and backchannel rejection |
`RealtimeConversationState.kt` | The spec's conversation state machine, typed errors, outbound events |
`WavPcm.kt` | WAV → PCM decode, downmix, resample — the VA-1 compatibility gap |
`RealtimeVoiceSession.kt` | Owns the conversation: routes audio both ways, barge-in, reconnect/resume, operation outcomes |
`WebSocketRealtimeTransport.kt` | The first concrete transport, plus the wire codec |

Plus one amendment to VA-1 — see **The duplex correction** below.

## Transport: WebSocket on the existing OkHttp

Decided by inspecting the repository, as the brief required:

- **OkHttp 4.12.0 is already a dependency** (`app/build.gradle.kts:219`) and ships a complete
  WebSocket implementation. A realtime transport therefore costs **no new dependency**.
- **MockWebServer is already a test dependency** (`app/build.gradle.kts:259`, used by three
  existing tests) and can serve a genuine WebSocket upgrade. So the transport is tested across
  a real HTTP boundary on a loopback socket, which §8.2 of the locked spec requires for
  critical paths and which a mock cannot satisfy.
- **The backend has no WebSocket code at all** — the only matches for "websocket" in
  `ai-placement-backend/app` are a skill string in `services/career_path/role_catalog.py`. So
  no server contract was being adopted or broken; the protocol below is ours to define. (The
  gateway was written afterwards, in the closure step, *against this codec* rather than designed
  in parallel — see "Still open".)
- **WebRTC was not chosen.** It would mean a multi-megabyte native dependency plus a signalling
  service that does not exist, and the blueprint's own ₹0 rule argues against adding
  infrastructure to prototype. It remains available behind `RealtimeTransport` unchanged.

### Wire protocol

Control messages are JSON text frames; audio is binary. Audio is not base64-wrapped in an
envelope because that inflates the most frequent message type by a third.

Server audio frames are `[version:1][idLength:1][generationId][PCM16LE]`. The generation id
travels *with* the audio rather than being inferred from the preceding text frame, so a chunk
that arrives after an interruption can be identified and dropped instead of being played
against whatever generation happens to be current.

### Credentials

The access token is an `Authorization` header. The resume token is sent as a message after the
socket opens. Neither is ever a query parameter: URLs reach server logs, proxy logs and crash
reports. Both rules are enforced by tests, and a mutant that moves the resume token into the
URL is caught.

The transport takes an **http(s)** URL and does no `ws://` rewriting. The first draft did, and
the repository's own `Android16MigrationGuardTest` rejected it: no production source may contain
a literal scheme or host, because every base URL has to come from the per-flavour
`BuildConfig.API_BASE_URL` or a dev endpoint ships inside the release build. The guard was right,
and requiring the caller to pass the flavour URL through unmodified is the better design anyway —
it means the prod flavour's https-and-no-cleartext guarantee cannot be undone by string
manipulation here.

## The duplex correction (an amendment to VA-1)

VA-1 asserted that capture and playback were mutually exclusive — `START_PLAYBACK` was legal
only from `Ready`, and a test named `playback cannot start while capturing` pinned it.

**That invariant was wrong for the product, not merely conservative.** Barge-in requires the
microphone to stay open *while the agent speaks*, because the only way to detect that the user
interrupted is to hear them do it. A half-duplex session can offer a stop button; it cannot
offer an interruption, and interruption is a P0 requirement in all three specification
documents.

VA-2 therefore added `VoiceSessionState.Duplex` and four transition-table entries. Stopping one
direction returns to the other (`Duplex` → `Playing` on stop-capture, `Duplex` → `Capturing` on
stop-playback) rather than collapsing to `Ready`, so barge-in does not close the microphone and
the user's turn ending does not cut off the reply. The VA-1 tests that encoded the old rule were
rewritten to state the new one and say why.

## VAD: why on-device energy detection, and what remains unproven

The brief asked to prefer on-device VAD, to evaluate Silero as a candidate, and not to install
dependencies or download models. Those settle the design rather than conflict:

| Option | Device requirement | Status |
|---|---|---|
| **Energy + ZCR (shipped)** | None. Microseconds per 20 ms frame, on the capture thread | Implemented behind `VoiceActivityDetector` |
| **Silero VAD** | ONNX Runtime AAR (tens of MB) + model file — both new dependencies needing authorisation | **Candidate, not evaluated.** Its accuracy cannot be compared honestly without running it |
| `android.media.audiofx` | Already used by the Core | Offers `NoiseSuppressor`/`AcousticEchoCanceler`; **there is no platform VAD API** |
| `SpeechRecognizer` | Google services | Does endpointing, but owns the microphone (conflicts with the Core's `AudioRecord`) and is a full recogniser, not a detector |
| `AlwaysOnHotwordDetector` | System voice-interaction service | Keyword spotting, not general VAD; not available to a normal app |

So the honest comparison is: **there is no platform VAD to compare against**, and Silero cannot
be benchmarked without installing it. The shipped detector is dependency-free and sufficient to
drive turn-taking; swapping in Silero later means implementing two methods.

**NEEDS TEST:** every threshold in `VadConfig` and `TurnConfig` — the 9 dB speech margin, the
700 ms end-of-turn silence, the 400 ms barge-in threshold — is a starting point chosen for
plausibility, not a measured value. Noisy-room and accented-speech accuracy is unmeasured. No
latency figure in this phase was measured on a device, and none is claimed.

## Ordering decisions worth knowing

- **Barge-in stops local playback before the service is told.** The user judges an interruption
  by when the voice stops; doing it in the other order spends a network round trip talking over
  them. A mutant that reverses the order is caught.
- **Audio is streamed whether or not the local VAD called it speech.** Trimming to detected
  speech saves bandwidth but clips word onsets — the detector needs a few frames to be sure, and
  those frames are the start of the word. On-device VAD drives turn-taking; it does not gate the
  stream.
- **Playback is drained by a single consumer from one queue.** Chunks cannot be `launch`ed
  individually: coroutines started concurrently finish in whatever order the dispatcher chooses,
  and speech reassembled out of order is noise. `GenerationDone` travels through the same queue
  so it cannot overtake the audio it terminates.
- **A turn spoken entirely over the agent is not committed.** Asking the service to answer an
  interjection it was never meant to hear is the false-interruption failure in a different
  costume.

## Unknown mutation outcomes

An operation that was in flight when the connection died becomes `UNKNOWN`, **before** any
reconnect is attempted, and nothing re-sends it. A reconnect does not retroactively reveal what
happened; only an authoritative `OperationOutcome` may resolve an operation. A cancel request
likewise leaves the operation `IN_FLIGHT`, because a cancel that races a completing mutation
does not undo it.

`PendingOperation` carries a human description for exactly this case: "something may or may not
have happened" is not a usable message, "your note may or may not have been saved" is.

## Two generation counters, not one

`sessionGeneration` identifies the audio session (bumped on start and stop); `connectionGeneration`
identifies the connection (bumped on every connect, including each reconnect attempt).

They were one counter until mutation testing exposed the consequence: capture is never restarted
across a reconnect, so bumping a shared counter on reconnect invalidated the microphone sink and
the session went **permanently deaf the moment the network recovered** — silently, because the
connection looked healthy. Both the split and a regression test for it are in place.

### The frame that arrives during teardown

`sessionGeneration` is bumped at the *top* of `stop()`, several statements before `audio.close()`.
That ordering is deliberate, and the window it opens is the reason the sink's generation check is
not redundant with the Voice Core's own:

* The Core's guard is bumped by `open()` and `close()` — **not** by `stopCapture()`. A frame still
  in flight while the recorder is being stopped therefore passes it and is handed to this layer's
  sink. (`VoiceSessionTest > a frame still in flight while capture stops reaches the sink` asserts
  exactly that, so the claim is tested rather than assumed.)
* `AudioRecordPcmCapture.stop()` joins its reader thread with a 500 ms timeout, so a frame already
  read by that thread can be delivered inside the window.

In that window this layer's check is the only thing between a stale frame and a still-open socket.
`RealtimeVoiceSessionTest > a microphone frame delivered while stopping never reaches the transport`
drives it deterministically: the fake recorder delivers from inside `stop()`, through the sink the
Core registered, and the test asserts both that the frame was really delivered and that neither the
VAD nor the transport ever saw it. Mutant **M4** deletes this check, and that test is what now
catches it. The fake drops nothing of its own accord — a test double that re-implements the guard
is how a deleted guard comes to pass its own test.

## What is explicitly not in VA-2

Intent classification, tool selection, tool execution, authorization, model choice, personal-data
retrieval, world search, memory, speech recognition, speech synthesis. `ToolCallStarted` and
`Thinking` are rendered because the service reports them; nothing here decides to call a tool.

The backend side of VA-2 is the **transport gateway only** (`app/api/voice_session.py`,
`app/voice/`): it authenticates, negotiates the audio format, accounts for what crossed the socket,
and carries nothing. It deliberately never emits a transcript, a generation or an operation outcome,
because inventing one would be worse than the silence.

## Still open

- **The gateway is not deployed and has never met a device.** It is tested through the real ASGI
  WebSocket in-process, and the Kotlin codec is checked against the Python contract by a
  cross-repository parity test — but a live Android-to-FastAPI socket, physical-device audio and
  any end-to-end speech interaction are **NEEDS TEST** and are not claimed. Real-network latency
  and carrier NAT behaviour are untested.
- **Session state is process-local and so is resume.** The registry is an in-memory singleton:
  resume tokens are redeemable only on the worker that issued them, so a multi-worker or
  multi-instance deployment provides **no cross-worker session recovery** — a reconnect landing on
  another worker is a new session, not a resumed one. Nothing persists it, which is also why no
  migration was needed; making resume survive a worker change needs shared state and is a
  deployment-phase decision, not a client one.
- **Resampling is linear with a box pre-filter**, not a polyphase FIR. Adequate for intelligible
  speech playback; **not** adequate to feed a recogniser. If PCM ever flows the other way, into
  STT, this needs replacing.
- **The interview feature is unchanged.** `WavPcm` now makes file playback expressible through
  `PcmPlayback`, which was the blocking gap recorded in VA-1 — but migrating the interview UI is
  VA-9 work and its AAC upload contract is untouched.
- **Everything device-dependent is NEEDS TEST**, including whether `VOICE_COMMUNICATION` capture
  plus simultaneous `AudioTrack` playback behaves on real hardware, which is the assumption the
  duplex correction rests on.
