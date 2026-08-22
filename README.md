# AI Placement OS — Android

Kotlin + Jetpack Compose Android app. See `../PROJECT_HANDOFF.md` for full architecture, schemas, and navigation contracts.

## Setup

Open in Android Studio, or build from the command line:

```bash
./gradlew assembleDebug
```

`local.properties` (not committed) must point `sdk.dir` at your Android SDK install.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Navigation Compose
- minSdk 26, compileSdk/targetSdk 35

## Status

Stage 10 complete. The flow now runs Login/Signup → JD paste → resume upload → skill gap
analysis → Prepare Me blueprint → test-taking → results → text interview → **voice
interview** → results end to end against the real backend.

- New `voice/` package (per `PROJECT_HANDOFF.md` section 8.1): `AudioRecorder.kt` wraps
  `MediaRecorder` (records AAC/M4A), `AudioPlayer.kt` wraps `MediaPlayer`, and
  `VoiceInterviewManager.kt` orchestrates one turn -- download + play a question's
  synthesized audio, record the spoken answer, upload it for transcription. Held in a
  `remember { }` in `VoiceInterviewScreen` so recomposition doesn't recreate the underlying
  recorder/player.
- `VoiceInterviewScreen` requests `RECORD_AUDIO` at runtime (declared in
  `AndroidManifest.xml`) via `ActivityResultContracts.RequestPermission()`, showing a
  dedicated "grant access" state if denied, before any interview content loads.
- `VoiceInterviewViewModel` drives a turn-based state machine per question: play question
  audio → ready to record → recording → submitting (transcribing) → next question. Its
  `VoiceTurnPhase` enum plus `VoiceInterviewUiState.Turn` is a deliberate third exception to
  the `UiState<T>` pattern (alongside `TestTakingUiState`/`TextInterviewUiState`), since a
  voice turn has more sub-phases than any generic shape models.
- Audio moves over Retrofit as raw bytes: `GET .../questions/{id}/audio` returns
  `ResponseBody` (no JSON converter involved) and is cached to a local file for `MediaPlayer`
  to play; `POST .../answer/audio` is a multipart upload built the same way
  `ResumeUploadScreen`'s PDF upload already was.
- **Which interview the final evaluation screen shows changed**: previously (Stage 9)
  `VoiceInterviewScreen` was an inert placeholder and `InterviewEvaluationScreen` always
  showed the *text* interview's results. Now that voice is real, `VoiceInterviewScreen`
  creates its own separate `Interview` (mode="voice") and the evaluation screen at the end
  of the flow shows *that* one instead -- the doc's flow diagram (`Text Interview → Voice
  Interview → Evaluation`) implies a single evaluation after both stages, and the backend
  models each mode as a separate `Interview`/`InterviewEvaluation` with no combined view, so
  showing the more-recently-completed (voice) interview's evaluation was the more direct
  reading. The text interview's own evaluation remains available via
  `GET /interviews/{id}/evaluation` for that interview's id -- just not surfaced in this nav
  chain.
- **Not verified on-device**: this environment has no emulator or physical device with
  microphone/speaker hardware, so `AudioRecorder`/`AudioPlayer`/permission flow are verified
  by compilation and code review only, not by an actual recording round-trip -- unlike the
  backend's STT/TTS, which *was* verified live against real audio (see the backend README).
- Manual composition root (`NetworkModule`, no DI framework yet) — Retrofit + OkHttp +
  kotlinx.serialization, Jetpack DataStore for token storage.
- Route arguments thread a created resource id through each linear flow (`jdId` through
  Prepare Me, `blueprintId` → `assessmentId` through test-taking, `interviewId` through the
  interview flow) — see the `*Route()` helpers in `navigation/Screen.kt`.
- Not yet built: roadmap update screen, Hilt/Koin DI, encrypted token storage, HTTPS before
  release.
