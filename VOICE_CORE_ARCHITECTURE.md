# Shared Voice Core (VA-1) — architecture note

Scope: VA-1 only. No realtime transport (VA-2), no speech provider (VA-3), no tool registry
(VA-4). This note exists because the locked spec requires an explanation of how the existing
voice-interview feature relates to the new Voice Core, and what migration remains.

## What VA-1 added

`app/src/main/java/com/aiplacementos/app/voice/core/`

| File | Responsibility |
|---|---|
`VoiceAudioConfig.kt` | One declared, validated audio format (default 16 kHz mono PCM16) plus observed AEC/NS/AGC status |
`VoiceSessionState.kt` | Typed states, events, errors, and the legal-transition table |
`VoiceAudioPorts.kt` | The seams: `PcmCapture`, `PcmPlayback`, `AudioFocusController`, `AudioRouteMonitor`, `MicrophonePermission` |
`AndroidVoiceAudio.kt` | Platform implementations: `AudioRecord` capture, `AudioTrack` playback, `AudioFocusRequest` focus, `AudioDeviceCallback` routing, effect attachment |
`VoiceSession.kt` | Sole owner of the native resources; lifecycle, cancellation, idempotent cleanup, stale-callback rejection |

Nothing in `voice/core/` imports an Android UI type, a repository, a provider, or a network
client. That is principle 5 (Voice Core supplies infrastructure) kept honest by a test.

## Why there are now two recorders, and why that is not a mistake

The existing `voice/AudioRecorder.kt` uses `MediaRecorder` to produce an **AAC/M4A file**. The
new `AudioRecordPcmCapture` uses `AudioRecord` to produce **PCM frames**. Both are kept.

This is not duplication left behind out of caution — they are not substitutable:

- The backend's `POST /interviews/{id}/answer/audio` validates `audio/mp4` content types and
  checks that bytes 4–8 are `ftyp`, i.e. an MP4 container. It accepts a *file*, and that
  contract is explicitly out of scope to change in VA-1.
- `MediaRecorder` structurally cannot provide PCM frames. It encodes to a container and writes
  to a file descriptor; there is no API to read samples out of it. So streaming speech
  recognition, VAD and barge-in cannot be built on it at all — they need the samples.
- Converting PCM to AAC in-app to feed the existing endpoint would mean adding an encoder and
  changing the recording format of a working feature. The brief forbids doing that without a
  verified, tested design, and rightly: the interview upload is a path students depend on.

So the division is by output type, not by age: **file-based capture for the file-based upload
contract, PCM capture for the streaming work that starts in VA-2.**

## How the interview flow relates to the Core today

Unchanged. `VoiceInterviewScreen` → `VoiceInterviewViewModel` → `VoiceInterviewManager` →
`AudioRecorder`/`AudioPlayer` → `InterviewRepository` still works exactly as before. VA-1 added
no call into that path and changed none of its files.

That was a deliberate choice over the alternative of migrating it now. The spec says the
interview becomes "one consumer/mode of the shared Voice Core", and it will — but the brief
also says not to migrate the interview UI until the shared interfaces preserve its behaviour,
and two of the behaviours it depends on are not yet expressible in the Core:

1. **File playback.** `AudioPlayer` plays a downloaded WAV through `MediaPlayer`. The Core's
   `PcmPlayback` plays frames. Routing the interview through the Core would mean decoding the
   downloaded WAV to PCM first — new code on a working path, for no VA-1 benefit.
2. **Turn semantics.** The interview's phases (`LOADING_AUDIO`, `READY_TO_RECORD`, `RECORDING`,
   `SUBMITTING`) are conversation states, not audio states. The Core deliberately models only
   audio; the conversation machine arrives in VA-2.

Migrating before those exist would either widen the Core with interview-specific concepts or
produce an adapter that has to be unpicked in VA-2.

## What the Core already fixes

`VOICE_READINESS_CHECKLIST.md` records two concrete gaps. Both are addressed in the Core:

- **No audio focus handling** → `VoiceSession` requests focus on open, stops capture *and*
  playback on loss, distinguishes transient from permanent, returns to `Ready` on regain, and
  abandons focus during cleanup. Four tests cover it; mutating the focus-loss stop is caught.
- **No protection against configuration changes or process death mid-turn** → ownership moved
  out of a composition-scoped object. `VoiceSession` holds the resources, `close()` is
  idempotent and safe from any state, and a callback from a previous session is rejected by
  generation rather than by luck. *Partial:* the Core makes correct cleanup possible and
  testable; whether a given screen survives rotation depends on where it holds its session,
  and the interview screen's own handling is unchanged in VA-1.

## Migration plan (not VA-1 work)

| When | Step |
|---|---|
VA-2 | Add the conversation state machine over `VoiceSession`; add a WAV→PCM decode path so file playback can be expressed through `PcmPlayback` |
VA-3 | Route `AudioRecordPcmCapture` frames into an `STTProvider`; the interview's AAC upload keeps its own path until a tested migration exists |
VA-9 | Rebuild the interview as a Core consumer, preserving answer semantics, idempotency and the AI boundary |
later | Retire `voice/AudioRecorder.kt` only once the interview no longer uses the file endpoint — a backend contract change, separately approved |

Until then two audio paths exist by design, with a documented boundary, rather than two
architectures nobody owns.

## Explicitly not in the Core

Career logic, LLM orchestration, provider selection, personal-data queries, backend mutation,
VAD, turn detection, network transport, foreground service. The Core provides the cancellation
and playback-stop primitives those layers will call; it does not call them.
