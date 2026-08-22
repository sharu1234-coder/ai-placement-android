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

Stage 8 complete. The full "Prepare Me for This Job" flow is wired end to end against the
real backend: Login/Signup → JD paste → resume upload → skill gap analysis → Prepare Me
blueprint → test-taking (adaptive difficulty, MCQ + free-text questions, per-question
feedback) → results screen with per-skill scores and priority weaknesses.

- Manual composition root (`NetworkModule`, no DI framework yet) — Retrofit + OkHttp +
  kotlinx.serialization, Jetpack DataStore for token storage.
- `UiState<T>` (Loading/Empty/Error/Success) is the standard ViewModel→Compose pattern;
  `TestTakingUiState` in `ui/tests/TestTakingViewModel.kt` is a deliberate exception —
  a test-taking session alternates between showing a question and showing feedback on the
  answer just given, which the generic shape doesn't model.
- Route arguments thread a created resource id through each linear flow (`jdId` through
  Prepare Me, then `blueprintId` → `assessmentId` through the test-taking flow) — see the
  `*Route()` helpers in `navigation/Screen.kt`.
- Not yet built: interview screens (Stage 9/10 — currently placeholders), roadmap update
  screen, Hilt/Koin DI, encrypted token storage, HTTPS before release.
