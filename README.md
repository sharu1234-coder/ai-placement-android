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

Stage 9 complete. The flow now runs Login/Signup → JD paste → resume upload → skill gap
analysis → Prepare Me blueprint → test-taking → results → a real text interview → results
end to end against the real backend.

- `TextInterviewScreen` starts a `type="technical"` interview (no `jd_id` -- relies on the
  profile's `target_role_id`, already set during role selection) and drives the
  next-question/answer loop; unlike test-taking there's no per-answer feedback (the backend
  scores the whole transcript once, at evaluation time), so it just advances straight to the
  next question after each submit.
- `InterviewEvaluationScreen` shows the AI-scored breakdown (technical correctness,
  relevance, completeness, reasoning, structure, clarity, grammar, role/JD coverage,
  overall) plus written feedback. `speaking_pace`/`filler_word_rate` are voice-only and
  omitted from the text-mode display.
- `VoiceInterviewScreen` is still a placeholder (Stage 10 -- turn-based STT/LLM/TTS), but is
  now interactive (a "Continue" button) rather than a dead end, so the full flow stays
  navigable end to end per `PROJECT_HANDOFF.md`'s testing strategy.
- Manual composition root (`NetworkModule`, no DI framework yet) — Retrofit + OkHttp +
  kotlinx.serialization, Jetpack DataStore for token storage.
- `UiState<T>` (Loading/Empty/Error/Success) is the standard ViewModel→Compose pattern;
  `TestTakingUiState` and `TextInterviewUiState` are deliberate exceptions — both sessions
  alternate between showing a question and something else (feedback, or a submitting flag)
  that the generic shape doesn't model.
- Route arguments thread a created resource id through each linear flow (`jdId` through
  Prepare Me, `blueprintId` → `assessmentId` through test-taking, `interviewId` through the
  interview flow) — see the `*Route()` helpers in `navigation/Screen.kt`.
- Not yet built: real voice interviews (Stage 10), roadmap update screen, Hilt/Koin DI,
  encrypted token storage, HTTPS before release.
