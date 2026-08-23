# AI Placement OS — Android

Kotlin + Jetpack Compose Android app. See `../PROJECT_HANDOFF.md` for full architecture, schemas, and navigation contracts.

## Setup

Open in Android Studio, or build from the command line:

```bash
./gradlew assembleDevDebug     # day-to-day dev build, points at your local backend
./gradlew assembleProdDebug    # sanity-checks the prod flavor compiles; API_BASE_URL is a
                                # placeholder until a production backend is deployed
```

`local.properties` (not committed) must point `sdk.dir` at your Android SDK install.

## Environments (dev / prod flavors)

Two Gradle product flavors, `dev` and `prod` (flavor dimension `"environment"`), so
publishing can point at a production backend and a production Firebase project without
restructuring the app -- see `app/build.gradle.kts`.

- `dev` — `applicationIdSuffix = ".dev"` (installs side-by-side with a `prod` build on the
  same device), `API_BASE_URL` from `local.properties`' `API_BASE_URL` key (falls back to
  the emulator loopback address).
- `prod` — no suffix (this is the applicationId that ships to the Play Store),
  `API_BASE_URL` from `local.properties`' `PROD_API_BASE_URL` key. Defaults to an
  intentionally-invalid placeholder host until a real production backend exists (M17 in the
  production report) -- a misconfigured prod build should fail loudly (DNS error), not
  silently talk to a dev server.
- Both flavors expose `BuildConfig.ENVIRONMENT` (`"development"`/`"production"`) for any
  future code that needs to branch on it (e.g. gating verbose logging or analytics).

### Firebase setup (not yet wired in)

Per the confirmed architecture: **Firebase is for auxiliary services only** — FCM push
notifications, Crashlytics, Analytics, and App Check. It is **not** a replacement for the
FastAPI + SQLAlchemy + PostgreSQL backend, which remains the system of record for all app
data (users, resumes, jobs, assessments, interviews, roadmaps). No Firebase SDK is in the
project yet; when it's added, the flavor split above is what makes it dev/prod-safe:

1. Create two Firebase projects (e.g. `ai-placement-os-dev`, `ai-placement-os-prod`).
2. In each, register an Android app with the matching applicationId --
   `com.aiplacementos.app.dev` for the dev project, `com.aiplacementos.app` for the prod
   project -- and the debug/release signing SHA-1 fingerprints you'll actually build with.
3. Download each project's `google-services.json` into `app/src/dev/` and `app/src/prod/`
   respectively (gitignored -- see `.gitignore`; these are environment-specific config, kept
   local the same way `local.properties`/`.env` are, not committed).
4. Only then add the Google Services Gradle plugin + Firebase BoM + the specific SDKs
   (Messaging, Crashlytics, Analytics, App Check) to `app/build.gradle.kts` -- applying the
   plugin before a `google-services.json` exists for every flavor being built fails the
   build outright, so this is deliberately left undone until step 3 is complete.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Navigation Compose
- minSdk 26, compileSdk/targetSdk 35

## Status

**Stage 12 — Progress dashboard (2026-08-23)** — replaces the last remaining Stage 2
placeholder (`ProgressDashboardScreen`), wired to the backend's new
`GET /progress/dashboard`:
- Hero card shows the readiness score as a `ScoreRing`, or an honest "complete a test or
  interview" prompt when there's no data yet (`readiness_score: null`) rather than showing
  a fabricated 0%.
- Three `StatTile`s (tests taken / interviews done / roadmap tasks) reuse `AppCard` rather
  than introducing a new component, matching the doc's existing design-system vocabulary.
  `AssistChip`s (top 3) surface the current focus-area skills from `top_weaknesses`.
  "Recent activity" lists every trend point newest-first, reusing the same
  `Icons.AutoMirrored.Filled.Assignment`/`Icons.Filled.RecordVoiceOver` split as elsewhere.
- No new date-parsing dependency: `createdAt` stays a raw ISO string from the backend,
  displayed by truncating at `"T"` -- consistent with `RoadmapTaskDto.dueDate`'s existing
  treatment.
- Verified: `./gradlew assembleDevDebug assembleProdDebug` both BUILD SUCCESSFUL. Backend
  counterpart additionally verified live end-to-end against real Ollama (see
  `ai-placement-backend/README.md`).

**Architecture confirmation + dev/prod flavors (2026-08-23)** — the user explicitly locked
in: FastAPI + SQLAlchemy + PostgreSQL stays the primary backend/database (no Firestore
migration of existing data/models), Firebase is for auxiliary services only (FCM,
Crashlytics, Analytics, App Check), and existing JWT auth stays unless a specific technical
reason emerges to change it. Added the `dev`/`prod` Gradle product flavors described above
so this and the backend's already-existing `AI_PROVIDER` env switch are both real,
buildable "no rebuild needed at publishing" paths, not just stated intent. No Firebase SDK
added yet -- see "Firebase setup" above for exactly what's needed before that's possible.

**Stage 11 — Roadmap screens (2026-08-23)**, wired to the backend's new Weakness
Engine + Roadmap endpoints (`PROJECT_HANDOFF.md` M11), replacing the last two Stage 2
placeholders (`RoadmapScreen`, `RoadmapTaskDetailScreen`):
- `ui/roadmap/RoadmapViewModel.kt` introduces `RoadmapUiState` (a fourth deliberate
  exception to `UiState<T>`, alongside `TestTakingUiState`/`TextInterviewUiState`/
  `VoiceInterviewUiState`): `NoRoadmap` is a real, user-facing state (nothing generated
  yet -- show a "Generate my roadmap" button) distinct from a network `Error`, and
  `Success(roadmap, isBusy)` keeps showing the current roadmap while a reassess is in
  flight instead of flashing a blank loading screen.
- **The bottom-nav "Roadmap" tab never auto-generates.** It calls `GET /roadmap/current`
  scoped to no JD (role-based) and shows a `NoRoadmap` prompt if nothing exists --
  spending an AI call on a blind tab visit isn't the right default. The Prepare Me flow's
  final step (renamed route `prepare-me/roadmap-updated/{jdId}`, previously argument-less)
  *does* auto-generate on load, since that screen's entire purpose is showing the roadmap
  that just got built from the weaknesses the flow produced.
- **`jdId` now threads one step further than before**: `InterviewEvaluationScreen`'s route
  gained `{jdId}` (previously dropped after `VoiceInterviewScreen`) so the roadmap
  generated at the end of the flow is scoped to the same JD the tests and interviews were.
  Reaching the roadmap step now also collapses the whole completed Prepare Me flow off the
  back stack (`popUpTo(Screen.RoleOrJdEntry.route) { inclusive = true }`) -- pressing Back
  from the roadmap goes to Home, not backward through five completed screens.
- **`RoadmapTaskDetailScreen` gained real arguments** (`{taskId}/{jdId}`, previously
  zero-argument dead code that couldn't distinguish one task from another). There's no
  per-task `GET` endpoint on the backend; the detail screen re-fetches the current roadmap
  for the same jd scope and picks the matching task out of it, avoiding a redundant
  endpoint for what's already in the roadmap payload.
- Task status ("pending"/"done"/"skipped") is editable two ways: a checkbox directly in
  `RoadmapScreen`'s list (quick toggle, done/pending only) and full buttons in the detail
  screen (including "Skip"). A failed toggle is swallowed rather than shown as a full-screen
  error -- consistent with treating it as a low-stakes, easily-retried action, not a flow-
  blocking one.
- Not yet built: `ProgressDashboardScreen` remains the Stage 2 placeholder -- the backend's
  `GET /progress/dashboard` (PROJECT_HANDOFF.md section 8.5) wasn't built this pass either;
  both are the next natural piece of scope, not attempted here to keep this change focused.

**Whole-codebase audit + premium visual pass (2026-08-23)** — a full review of `ui/`,
`data/`, `domain/`, `network/`, `voice/`, `navigation/` against `PROJECT_HANDOFF.md`
surfaced and fixed several real issues, most severe first:
- `voice/AudioRecorder.kt`'s `prepare()`/`start()` were never wrapped in try/catch --
  a busy mic, hardware failure, or a permission revoked between the app's own check and
  the call crashed the app outright. Now throws a typed `AudioRecordingException`,
  caught by `VoiceInterviewViewModel` and surfaced as a normal error state. Added a
  `MediaRecorder.OnErrorListener` and a 2-minute `setMaxDuration` (an interrupted/stuck
  recording could otherwise grow unbounded on-device). `voice/AudioPlayer.kt` had the
  same gap for `prepare()`/`start()`, fixed the same way (falls back to "skip this
  question's audio" rather than crashing).
- The mic-permission check in `VoiceInterviewScreen` only ran once on first composition.
  Backgrounding the app mid-interview, revoking the permission in system settings, and
  returning left the UI still showing record controls that would throw
  `SecurityException`. Now re-checked on `ON_RESUME` via a lifecycle observer.
- **Navigation back-stack loop**: `TestTakingScreen`, `TextInterviewScreen`, and
  `VoiceInterviewScreen` each auto-navigate forward when their ViewModel reaches a
  terminal `Completed` state. None of the forward `navigate()` calls used `popUpTo`, so
  pressing Back re-entered the completed screen, whose `LaunchedEffect` immediately
  re-fired on the still-`Completed` state and pushed a duplicate copy of the next screen
  -- Back became effectively broken on those three transitions. Fixed with
  `popUpTo(...) { inclusive = true }` on each.
- **`jdId` was silently dropped before the interview stage** -- both interview
  ViewModels hardcoded `jdId = null`, and the `TextInterview`/`VoiceInterview` routes
  took no arguments at all, even though the backend fully supports JD-specific interview
  questions. This broke PROJECT_HANDOFF.md's stated "Critical requirement": a pasted JD
  should customize tests *and* interview questions, not just tests. `jdId` now threads
  through the entire route chain (`TestFlow → TestTaking → TestResult → TextInterview →
  VoiceInterview`) -- see the `*Route()` helpers in `navigation/Screen.kt`.
- Resume upload read the entire picked file into a `ByteArray` before checking its size
  against the 10MB limit -- a very large PDF risked `OutOfMemoryError` on a
  memory-constrained device before the network call was even attempted.
  `ResumeUploadViewModel` now queries the file's declared size via `ContentResolver`
  first and rejects oversized files without ever reading them.
- **No session restore or token refresh** -- every cold start went to `LoginScreen`
  regardless of a still-valid stored token (`AuthRepository.isLoggedIn()` existed but had
  no call site), and an expired access token had no recovery path even though a valid
  refresh token sat unused in `TokenStore`. Fixed both: `NavGraph` now resolves its real
  start destination from `isLoggedIn()` before rendering, and a new
  `network/TokenAuthenticator.kt` transparently refreshes on a 401 and retries once.
- `HttpLoggingInterceptor`'s `Level.BODY` (debug builds) logged the `Authorization`
  header in full, including the bearer token, to Logcat on every request. Added
  `redactHeader("Authorization")`.
- **No logout UI existed anywhere in the app** -- `SettingsScreen` was still a Stage 2
  placeholder. Built a real one with a working "Log out" action.
- Deleted `ui/resume/ResumeAnalysisScreen.kt`, confirmed orphaned (no call site anywhere
  -- `JobMatchScreen` and `ResumeUploadScreen`'s own inline result view cover that
  ground).

**Premium visual pass**, guided by `UI_UX_REFERENCES.md`/`UI_DESIGN_REFERENCES.md`
(original identity built on a Material 3 foundation, not a clone of any reference app):
- Replaced the stock Material Baseline blue palette with an original indigo/violet
  identity (`ui/theme/Color.kt`) plus app-specific semantic tokens (`SemanticColors.kt`)
  for "matched skill" vs. "skill gap" that don't force those concepts onto MD3's
  alarm-coded `error` role.
- New reusable design-system components named to match the doc's own vocabulary
  (section 9): `ScoreRing` (animated circular score, used for readiness/match/result
  scores) and `SkillBar` (animated labeled progress bar, gap-aware coloring). `AppCard`
  gained an optional tinted `containerColor` for hero cards.
- Applied across the "hero"/results screens: `JobMatchScreen` (skill gap analysis),
  `PrepareMeSummaryScreen`, `TestResultScreen`, `InterviewEvaluationScreen` (which also
  gained display of `speaking_pace`/`filler_word_rate` for voice-mode evaluations --
  previously computed server-side in Stage 10 but never shown), and `HomeScreen`
  (rebuilt from its Stage 2 placeholder state with a real time-of-day greeting and an
  honest "get started" prompt -- no fabricated readiness score, since that data doesn't
  exist until Stage 11's weakness engine is built).

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
- **Audit findings intentionally not fixed**, lower severity: cache files
  (`question_*.wav`/`answer_*.m4a`) are now deleted right after use (fixed), but nothing
  sweeps any left behind by a killed process; `AuthInterceptor`'s per-request
  `runBlocking` read of the token store (the audit itself rated this low-risk in
  practice); no R8/ProGuard shrinking or an explicit release `signingConfig` yet (pre-
  release concern, consistent with everything else marked "before real deployment").
- Not yet built: roadmap update screen, Hilt/Koin DI, encrypted token storage, HTTPS before
  release.
