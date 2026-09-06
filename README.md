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

### Firebase setup (wired in — production readiness item 17)

Per the confirmed architecture: **Firebase is for auxiliary services only** — FCM push
notifications, Crashlytics, Analytics, and App Check. It is **not** a replacement for the
FastAPI + SQLAlchemy + PostgreSQL backend, which remains the system of record for all app
data (users, resumes, jobs, assessments, interviews, roadmaps). **Firestore, Firebase Auth,
and Firebase Storage are deliberately not included anywhere.**

One Firebase project (`ai-placement-os`), two registered Android apps within it --
`com.aiplacementos.app` (prod) and `com.aiplacementos.app.dev` (dev). Each flavor's
`google-services.json` goes in its own source set:

```
app/src/dev/google-services.json
app/src/prod/google-services.json
```

**Both are gitignored** (`.gitignore`) and required to build — unlike `keystore.properties`
(optional, release signing silently no-ops without it), the Google Services Gradle plugin
fails the build outright for any flavor missing its file. If you're setting up a fresh
checkout: ask whoever administers the `ai-placement-os` Firebase project (Project Settings →
General) for both files, or register your own two Android apps there with the exact package
names above and download them yourself.

**One manual step App Check needs**: the Debug provider (used automatically in debug builds
— see `AiPlacementApp.kt`) generates a random per-install debug token, logged to Logcat on
first run (`D/... : Enter this debug secret into the allow list...`). Paste it into Firebase
Console → App Check → the app → "Manage debug tokens", or App Check will reject that
install's requests once server-side enforcement exists. Release builds use Play Integrity
instead, which needs no manual step but does need the app's real release-signing SHA-256
registered in Firebase (Project Settings → your app → SHA certificate fingerprints) once a
real release keystore exists (item 12) — not done yet, since one doesn't.

**App Check is wired end-to-end but not enforced by default (2026-08-30).**
`NetworkModule.kt`'s `AppCheckInterceptor` attaches an `X-Firebase-AppCheck` token to every
outgoing request (failing open — proceeding without the header — if the token fetch fails or
times out, rather than blocking the request over a transient Play Integrity hiccup). The
FastAPI backend now has a Firebase Admin SDK integration (`app_check.py` in that repo) that
can verify this header, but ships with enforcement OFF (`FIREBASE_APP_CHECK_ENABLED=false`)
until a real Firebase service account is configured server-side — see that repo's README for
the backend half. Flipping enforcement on before a build with this interceptor is actually the
one installed on users' devices would reject every request with 401.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Navigation Compose
- minSdk 26, compileSdk/targetSdk 35

## Status

**Master hardening pass — Firebase App Check actually wired end-to-end (2026-08-30).** New
`AppCheckInterceptor` (`network/NetworkModule.kt`) attaches an `X-Firebase-AppCheck` token
(via `Firebase.appCheck.getAppCheckToken` + `Tasks.await`, a 10s timeout) to every outgoing
request — previously the provider was installed (`AiPlacementApp.onCreate`) but nothing ever
read a token or sent it anywhere, so App Check provided zero actual protection despite being
"set up." Fails open (proceeds without the header) on any fetch failure/timeout rather than
blocking the request — a transient Play Integrity hiccup shouldn't brick the app, especially
since the backend's own verification defaults to disabled anyway (see the backend repo's
README/`app_check.py`). Verified via `compileDevDebugKotlin` (real compile, not assumed) since
this touches a static Firebase API surface (`Task<AppCheckToken>`, `Tasks.await`) that's easy
to get subtly wrong from memory. No dedicated unit test — same as `AuthInterceptor`, a static
Firebase singleton dependency isn't practically unit-testable without Robolectric, which this
project doesn't currently pull in; covered by the full Gradle build gate instead.

**Production readiness item 20/20 — Final production deployment readiness audit
(2026-08-24).** Full cross-repo audit and classification (READY/BLOCKER/NEEDS MANUAL
ACTION/FUTURE ENHANCEMENT) lives in `ai-placement-backend`'s `PRODUCTION_READINESS_FINAL.md`
— this entry covers only what changed in *this* repo during that audit. No deployment or
publishing was performed; item 19 (File + AI abuse controls) was backend-only, no Android
changes.
- **Fixed a real gap**: `android:allowBackup="true"` had no `dataExtractionRules`/
  `fullBackupContent` restricting Android's Auto Backup, so `TokenStore`'s DataStore file
  (holding the access/refresh JWTs) could have been swept into a cloud backup or
  device-to-device transfer, extending the tokens' exposure beyond the original device. Added
  `res/xml/data_extraction_rules.xml` (API 31+) and `res/xml/backup_rules.xml` (API < 31,
  minSdk is 26) excluding that one file from both; wired into the manifest.
  **Re-verified by inspecting the actual merged/packaged manifest output** (not just the
  source XML) that both attributes survive manifest merging.
- Added `.github/dependabot.yml` (gradle + github-actions ecosystems, weekly) — neither repo
  had any automated dependency-vulnerability scanning before this audit.
- Re-ran the full verification suite after these changes: `testDevDebugUnitTest`
  `testProdDebugUnitTest` `lintDevDebug` `lintProdDebug` `assembleDevDebug` `assembleProdDebug`
  `assembleProdRelease` — all green, 30 unit tests passing, R8/shrinking still clean.
- **Update (2026-08-28): the token-storage gap flagged below was fixed.** `TokenStore` now
  encrypts the access/refresh JWTs at rest with Google Tink AEAD (AES-256-GCM), its keyset
  wrapped by an Android Keystore-backed master key (`android-keystore://...`) — not
  `androidx.security.crypto`/`EncryptedSharedPreferences`, which Google deprecated in April
  2025 (see `TokenStore.kt`'s own header comment for the rationale). The line below is kept
  for history; it no longer describes the current state.
- ~~Still open (flagged, not fixed here): the token store itself remains unencrypted at
  rest on-device (readable given root or physical device access, independent of the backup
  fix above). Migrating to `androidx.security.crypto` or DataStore+manual crypto is a real,
  moderately-sized change (new dependency, a migration path for already-installed users) —
  judged out of scope for an audit item, called out explicitly in
  `PRODUCTION_READINESS_FINAL.md` as a real gap rather than silently deferred.~~
- Also re-confirmed (not re-assumed): permissions are minimal and each maps to a real shipped
  feature (`INTERNET`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`); no `google-services.json`/
  `keystore.properties`/`*.jks` was ever committed at any point in git history (checked three
  independent ways); the repo is private; its only GitHub secrets are the two Firebase config
  secrets from item 17, nothing else.

**Production readiness item 18/20 — Error/performance observability (2026-08-24).**
Android-only for this repo (backend side: `RequestObservabilityMiddleware` in
`ai-placement-backend`, see that repo's README). `ObservabilityInterceptor`
(`app/src/main/java/.../network/ObservabilityInterceptor.kt`) is a single OkHttp interceptor
added to `NetworkModule`'s client, so it covers every Retrofit call across every repository
without touching any of the ~20 ViewModels individually.
- **Performance**: logs method/path/status/duration for every network call; escalates to a
  warning log if a call takes 3+ seconds (generous on purpose — resume/JD parsing calls run
  real AI inference server-side and can legitimately take well over a second, matching this
  same client's 150s read timeout).
- **Error reporting to Crashlytics** (the first real use of Crashlytics beyond its automatic
  fatal-crash handling — wired in but never called from app code until this item): HTTP 5xx
  responses and IOExceptions other than `UnknownHostException` (no network path at all — not
  an app bug, and the single most common "failure" a mobile client sees) are recorded as
  Crashlytics non-fatals. HTTP 4xx responses are deliberately *not* recorded — most (401
  during token refresh, 404, 422 validation) are expected app-level flows the calling
  ViewModel's own catch block already surfaces to the user, and recording all of them would
  flood the Crashlytics dashboard with non-actionable noise.
- Never lets an observability failure break the actual request: every Crashlytics call is
  wrapped in its own `try/catch(Throwable)`, so if Crashlytics itself isn't reachable (as in
  this class's own unit tests, where Firebase is never initialized), the response/exception
  still flows through to the caller exactly as before.
- 5 new unit tests (`ObservabilityInterceptorTest`, real `MockWebServer` instance — new
  `okhttp-mockwebserver` test dependency, same version as the app's existing `okhttp`) proving
  pass-through correctness under success/4xx/5xx/slow/connection-failure conditions; the
  actual Crashlytics escalation policy (5xx vs 4xx vs `UnknownHostException`) isn't
  independently asserted at the unit level since that needs a reachable Firebase instance a
  plain JVM test can't provide (same honesty as this file's App Check note above). Also added
  `testOptions.unitTests.isReturnDefaultValues = true` (`app/build.gradle.kts`) — without it,
  this interceptor's real `Log.d`/`Log.w` calls crash any plain JVM unit test that exercises
  them.
- **Not implemented (deliberately)**: no Firebase Performance Monitoring SDK — that would be
  a new Firebase product beyond the FCM/Crashlytics/Analytics/App Check scope item 17 locked
  in; flagged as a possible future addition, not added unprompted. No per-ViewModel
  Crashlytics breadcrumbs — the network-layer interceptor already covers the overwhelming
  majority of real failure paths in this app (nearly everything a ViewModel catches
  originates from a Retrofit call).
- `testDevDebugUnitTest`, `testProdDebugUnitTest`, `lintDevDebug`, `assembleDevDebug`,
  `assembleProdDebug` all green. Committed `ab6a4b3`, pushed.

**Production readiness item 17/20 — Firebase auxiliary integration (2026-08-24).**
Android-only (no backend equivalent — the FastAPI backend has no Firebase Admin SDK
integration and none is planned; see the "not enforced end-to-end" note below). Full setup
docs in this file's "Firebase setup" section above; this entry covers what was verified.
- **Caught a real config error before it could break anything, twice.** The first
  `google-services.json` provided registered `com.aiplacements.app` (missing "os") instead
  of this project's real `com.aiplacementos.app` — checked against `app/build.gradle.kts`
  directly rather than assumed, twice (the second file fixed the prod entry's typo but the
  newly-added dev entry repeated it as `com.aiplacements.app.dev`). Held off on writing
  anything or touching Gradle until a third, correctly-matching file arrived, confirmed by
  the same direct check.
- Google Services plugin `4.5.0`, Crashlytics Gradle plugin `3.0.8`, `firebase-bom` pinned to
  `34.18.0` — versions confirmed against Google's actual Maven metadata (not a search-engine
  summary, and not copied from Firebase's own quickstart docs, per the explicit instruction
  not to). **This was pinned back to `34.1.0` for a long time and no longer is**: 34.18.0's
  transitive `play-services-measurement` is compiled with Kotlin 2.2.0 metadata, which the
  project's then-current Kotlin `2.0.21` compiler could not read (`compileDevDebugKotlin`
  failed with "Module was compiled with an incompatible version of Kotlin"), and bumping
  Kotlin was correctly judged the bigger, riskier change at the time. That Kotlin upgrade has
  since happened (Kotlin `2.3.21`, KSP `2.3.11`, AGP `8.13.2`, Gradle `8.14.3`, Room `2.8.4`),
  so the BoM is now on the current release. One knock-on: 34.18.0's Crashlytics references
  `android.os.ProfilingTrigger`, an API 36 class, so `compileSdk` had to move to 36 or R8
  fails the release build with "Missing class". `targetSdk` stays at 35 — see
  `app/build.gradle.kts` for why those two are deliberately different.
- Since BoM 34.x, `-ktx` artifacts are gone (merged into the main modules mid-2025) — used
  `firebase-messaging`/`firebase-crashlytics`/`firebase-analytics`/`firebase-appcheck-*`
  directly, not the old `-ktx` suffixed names.
- `app/src/dev/google-services.json` / `app/src/prod/google-services.json` — one Firebase
  project (`ai-placement-os`), two registered Android apps within it. Both gitignored
  already (confirmed via `git check-ignore -v` before writing either file, and via
  `git status` after — neither ever appeared).
- **FCM**: `AppFirebaseMessagingService` (new) receives messages and shows a system
  notification; `POST_NOTIFICATIONS` requested once at launch (non-blocking — unlike the
  microphone permission gating the voice-interview screen, nothing here is gated on it).
  `onNewToken` logs the token but doesn't send it anywhere yet — **the backend has no
  device-token-registration endpoint**, so nothing can actually trigger a push to a specific
  user until that's built (a backend change, out of scope for this Android-only item).
- **Crashlytics**: plugin + dependency only — auto-initializes, no code needed. Confirmed the
  mapping-upload step genuinely runs: `uploadCrashlyticsMappingFileProdRelease` executed
  (not skipped) during the verification release build below.
- **Analytics**: dependency only — auto-initializes (default events like `session_start`);
  no custom event-logging calls added anywhere, since none were requested and that's
  app-specific product decisions, not "wiring the SDK in."
- **App Check**: `AiPlacementApp.onCreate()` installs the Debug provider for debug builds
  (`BuildConfig.DEBUG`, not the dev/prod flavor — a prod-flavor debug build still needs it)
  and Play Integrity for release builds. **Not enforced anywhere yet** — installing the
  provider only makes the app generate tokens; nothing server-side verifies them, since the
  backend has no Firebase Admin SDK integration (deliberately — Firebase stays
  auxiliary-only, per the architecture). Flagged clearly rather than implied as active
  protection. The Debug provider's per-install debug token needs a one-time manual paste
  into Firebase Console (documented above) before that install's App Check calls succeed.
- Fixed a real, unrelated-looking but caused-by-this-change Lint error along the way:
  transitive `androidx.fragment:fragment:1.1.0` (pulled in by `play-services-basement`/
  `play-services-base`, Firebase's own dependencies) failed Lint's
  `InvalidFragmentVersionForActivityResult` check (needs ≥1.3.0 for the `ActivityResult`
  APIs `MainActivity`/`VoiceInterviewScreen` already use). Fixed by adding a direct
  `androidx.fragment:fragment:1.9.0` dependency to force that floor — current stable,
  confirmed via Google's Maven metadata.
- **Verified for real, not just "it compiled"**: ran `testDevDebugUnitTest`,
  `testProdDebugUnitTest`, `assembleDevDebug`, `assembleProdDebug`, and `lintDevDebug`
  together — all green. Then `assembleProdRelease` (unsigned) separately to re-check R8/
  shrinking (item 13) still works with the new dependencies — it does, and inspecting
  `mapping.txt` confirms `AiPlacementApp`/`AppFirebaseMessagingService` and every Firebase
  API call inside them survive shrinking intact, no custom keep rules needed (Firebase's own
  SDKs ship their own consumer rules, same pattern item 13 found for Retrofit/
  kotlinx.serialization). No device/emulator available in this environment (per item 10), so
  none of this was verified by actually receiving a push or triggering a crash on a running
  app — only by build/lint/unit-test success and static inspection of the shrunk output.
- No unrelated code changed — every file touched is either new (the FCM service, the two
  gitignored config files) or was touched specifically for Firebase wiring, Gradle version
  additions, or the one Lint fix those additions caused.
- **CI needed a follow-up fix**: `.github/workflows/ci.yml` checks out the repo fresh, so the
  gitignored `google-services.json` files were never there — the very first CI run after this
  item's initial push failed for exactly that reason (confirmed by watching the real run, not
  assumed). Fixed by adding two repo secrets, `GOOGLE_SERVICES_JSON_DEV` and
  `GOOGLE_SERVICES_JSON_PROD` (kept separate rather than one shared secret, since the two
  files aren't guaranteed to stay identical), written to their respective flavor source sets
  by a new CI step before any Gradle task runs. **Watched the corrected run on GitHub's real
  runners**: green, 11m24s, all steps including both flavor builds and the R8 release build.

**Production readiness item 14/20 — GitHub Actions CI/CD (2026-08-24).** Backend side is in
`ai-placement-backend/README.md`. Android side: `.github/workflows/ci.yml` — runs on every
push/PR to `master`, no publish/signing step (deliberately: no signing secrets are configured
in CI, so it can never produce a distributable artifact — matches "keep production
deployment/publishing disabled").
- Steps: `gradle/actions/wrapper-validation@v4` (checks `gradle-wrapper.jar`'s checksum before
  anything executes it — catches a tampered wrapper, not just a broken one) →
  `testDevDebugUnitTest testProdDebugUnitTest` (both flavors' unit tests) → `lintDevDebug`
  (Android Lint — already built into AGP, no new tool needed) → `assembleDevDebug
  assembleProdDebug` (both flavors compile) → `assembleProdRelease` (unsigned; re-exercises
  R8/shrinking — item 13 — on every change going forward, the same check that was done
  manually when minification was first turned on).
- No emulator/instrumented-test job. This isn't a shortcut: item 9 already established there
  are zero `androidTest` files in this project (only JVM unit tests), so an emulator job would
  cost real CI minutes (software-emulated Android on a GitHub-hosted runner is slow, no
  hardware acceleration available) to run nothing. Not added, consistent with "don't add
  unnecessary cloud services or paid infrastructure."
- Fixed a real, pre-existing problem this work surfaced: `gradlew` was tracked in git as mode
  `100644` (not executable) — harmless on Windows (where this repo has been developed so far,
  and where `core.fileMode=false` means git ignores the executable bit locally) but would have
  made `./gradlew` fail outright on the Linux CI runner with "Permission denied." Fixed via
  `git update-index --chmod=+x gradlew` (confirmed `100755` in the index before committing);
  the workflow also runs a defensive `chmod +x gradlew` step regardless, at zero cost.
- Both repos are private, so Actions minutes count against the free-tier monthly quota (not
  unlimited like public repos): added a `concurrency` group with `cancel-in-progress: true`
  (a superseded push cancels the in-flight run for the same branch instead of both running to
  completion) and rely on `gradle/actions/setup-gradle@v4`'s built-in dependency/build caching
  to keep repeat-run time down. `permissions: contents: read` is set explicitly (least
  privilege — this workflow never writes back to the repo).
- **Verified the workflow actually runs on GitHub's real runners**, not just that the YAML
  parses — pushed and watched the run via `gh run watch` (see commit below for the result);
  no Docker/Linux environment exists locally to dry-run it first.

**Production readiness item 13/20 — Release optimization / R8 (2026-08-23).**
`app/build.gradle.kts`'s `buildTypes.release` now sets `isMinifyEnabled = true` and
`isShrinkResources = true` (both were `false`/absent). `app/proguard-rules.pro` gained a
comment documenting what was checked, not any functional rule — no custom keep rules turned
out to be necessary for this codebase.
- **Verified empirically, not assumed.** Built a real `assembleProdRelease` (and separately
  `assembleDevRelease`) with minification on, then inspected the actual R8 output
  (`app/build/outputs/mapping/{prodRelease,devRelease}/mapping.txt`) for the three patterns
  most likely to break under shrinking/obfuscation in this codebase:
  - Retrofit API interfaces (e.g. `AuthApi`) — methods, parameter types, and generic
    signatures all present in the mapping (covered by Retrofit's own bundled
    `consumer-rules.pro`).
  - `kotlinx.serialization` `@Serializable` DTOs (e.g. `ResumeAnalysisDto`) — every field and
    the generated `$serializer`/`Companion` classes all present (covered by
    `kotlinx-serialization-json`'s own bundled `consumer-rules.pro`).
  - `HomeViewModel` — the one screen that calls the plain `viewModel()` composable with no
    explicit factory (every other screen passes `viewModelFactory { initializer { ... } }`
    for constructor-injected dependencies), meaning it's the one class actually instantiated
    via reflection (`Class.getDeclaredConstructor().newInstance()` inside
    `ViewModelProvider.NewInstanceFactory`). Its `<init>()` is present in the mapping in both
    flavors — R8 traces the `HomeViewModel::class.java` literal into that reflective call and
    keeps the constructor automatically, without needing a custom rule.
  - Confirmed no dynamic resource lookups exist in the codebase (`getIdentifier` etc. —
    none found), so `isShrinkResources` is safe; checked the resource shrinker's own report
    (`app/build/outputs/mapping/prodRelease/resources.txt`) and everything it removed was
    unused androidx-core notification/call-answer resources pulled in transitively, not
    anything the app itself defines or references.
  - Re-ran `testDevDebugUnitTest` after the change — still green (JVM unit tests run against
    unminified debug bytecode, so this doesn't exercise R8 itself, but confirms the
    `build.gradle.kts` edit didn't break anything else).
- **Size impact measured, not estimated**: a same-commit unminified `assembleProdRelease`
  built to 12.09 MB (unsigned APK); with minify+shrink on, 1.62 MB — most of that gap is
  `material-icons-extended` and unused Compose/AndroidX classes that shrinking strips.
- Deliberately did not add speculative keep rules "just in case" — every rule that would be
  needed is already verified present via the checks above; adding unused ones would only
  make future shrinking less effective for no real safety gain. If a new DTO, Retrofit
  interface, or reflectively-constructed class (another bare `viewModel()` call, for example)
  is added later, re-check `mapping.txt` for it after a release build rather than assuming
  this coverage still holds.
- **Not done**: no instrumented/on-device test of the actual release APK (per item 10, no
  working device or emulator is available in this environment) — the verification above is
  static (R8's own mapping output) and build/test-level, not a live run of the shrunk app.

**Production readiness item 12/20 — Secrets + release signing (2026-08-23).** Backend
secrets hardening (a fail-fast JWT-secret check) is in `ai-placement-backend/README.md`.
Android side: wires `app/build.gradle.kts` to read real release-signing credentials from a
gitignored `keystore.properties` — this session cannot generate a real production keystore
(that's yours to create and hold; losing it means you can never publish an update to an app
already live under the old key), so this makes the *wiring* correct and ready for one.
- `keystore.properties.example` (tracked) documents the expected keys and includes the
  exact `keytool -genkeypair` command to generate a real keystore. `.gitignore` now also
  excludes `keystore.properties`, `*.jks`, `*.keystore`, and `release.keystore` as a
  backstop in case a keystore file itself ever ends up in the project directory.
- Deliberately optional and additive: if `keystore.properties` doesn't exist (true for this
  checkout and every other one that hasn't generated a real keystore yet),
  `hasReleaseSigningConfig` is false and `buildTypes.release` is left exactly as before —
  no `signingConfig` assigned, same unsigned-APK behavior as prior to this change.
- **Verified the wiring actually signs an APK, not just that the Gradle config reads
  without error.** Confirmed the baseline first: `assembleProdRelease` with no
  `keystore.properties` present produces `app-prod-release-unsigned.apk`, matching
  pre-existing behavior exactly (no regression). Then generated a real throwaway test
  keystore (`keytool -genkeypair`), pointed a temporary `keystore.properties` at it,
  rebuilt — the output was `app-prod-release.apk` (no `-unsigned` suffix), and
  `apksigner verify --print-certs` on it showed the exact test certificate's DN
  (`CN=Test, OU=Test, O=Test, L=Test, ST=Test, C=US`) — genuine cryptographic proof the
  signing config is wired correctly end-to-end. Deleted the throwaway keystore and
  `keystore.properties` immediately after (neither was ever committed — `git status` was
  clean throughout, confirmed before and after).
- **Not done, and cannot be done from this session**: no real release keystore exists for
  this app. You'll need to generate one (command in `keystore.properties.example`), store
  it somewhere safe outside version control (a password manager, not just your disk), and
  create your own local `keystore.properties` before `assembleProdRelease` produces a
  signed, Play-Store-installable artifact.

**Production readiness item 11/20 — HTTPS / network security (2026-08-23).** Backend side
(TrustedHostMiddleware, HSTS, proxy requirements) is in
`ai-placement-backend/README.md`. Android side: `usesCleartextTraffic` is now split by
build flavor instead of being unconditionally `true`.
- `app/src/main/AndroidManifest.xml` no longer sets `usesCleartextTraffic` at all; it's
  overridden per flavor via manifest merging: `app/src/dev/AndroidManifest.xml` sets it
  `true` (the dev backend at `10.0.2.2`/a LAN IP runs over plain HTTP, and this flavor is
  never distributed to real users), `app/src/prod/AndroidManifest.xml` sets it explicit
  `false` (matches the platform default on API 28+ anyway, but explicit means it can never
  silently regress if that default ever changes).
- **Verified the split actually works**, not just that it compiles: built both
  `assembleDevDebug` and `assembleProdDebug`, then inspected each flavor's *merged* manifest
  output directly — `devDebug`'s shows `usesCleartextTraffic="true"`, `prodDebug`'s shows
  `usesCleartextTraffic="false"`. This matters because manifest merging across flavor source
  sets is exactly the kind of thing that looks right in source but silently doesn't apply —
  checking the merged output, not just the source files, is what actually confirms it.
- No code changes beyond the manifests — `NetworkModule`'s `API_BASE_URL` per flavor
  (already HTTPS by default for `prod`, per `app/build.gradle.kts`) was already correct;
  this closes the actual enforcement gap (the app previously could technically still be
  told to talk to a plain-HTTP endpoint even in a prod-flavored build).

**Production readiness item 10/20 — Real-device voice readiness (2026-08-23).** The master
prompt scopes this item explicitly as "a checklist, not fabricated hardware claims" — see
`VOICE_READINESS_CHECKLIST.md` for the full writeup. Summary:
- This environment has neither a physical Android device nor a working emulator (the one
  configured AVD failed to boot — missing system image). Confirmed by actually trying:
  `adb devices` (empty) and booting `UE_pixel_6_API_35` (`FATAL | Cannot find AVD system
  path`). `./gradlew assembleDevDebug` does build successfully, which is as far as this
  environment can verify.
- Code review confirms the recording/playback pipeline is sound: runtime `RECORD_AUDIO`
  permission requested (not just declared) with a resume-time re-check, recorded (AAC/M4A)
  and synthesized (WAV) formats agree end-to-end with zero client-side transcoding, upload
  size and network timeouts have realistic margin, common recorder failures (mic busy,
  permission revoked mid-session) are caught rather than crashing, and cached audio is
  deleted immediately after use.
- Two concrete gaps found and documented (not fixed in this pass, to keep the change
  minimal and the checklist accurate about what's actually done vs. recommended): no audio
  focus handling (a call/notification during recording isn't managed), and no protection
  against a config change or process death mid-recording (rotation would orphan an
  in-progress `MediaRecorder`).
- Also flags (cross-referencing, not re-solving) that the backend's TTS
  (`pyttsx3`/Windows SAPI5) is effectively Windows-only as currently configured — a
  deployment-portability finding for item 2's eventual resolution, not this item's.
- **Nothing here claims voice mode has been tested on real hardware** — it hasn't been,
  because none was available. That verification still needs to happen (on the user's own
  device) before voice mode ships to real users.

**Production readiness item 9/20 — Android automated testing (2026-08-23).** The project
had zero automated tests before this item (`testImplementation(libs.junit)` was present in
`build.gradle.kts` from the initial template, but nothing used it). Sets up real JVM unit
test infrastructure and covers the ViewModels behind the most safety-critical / most
recently-added flows.
- Added `kotlinx-coroutines-test` (`app/build.gradle.kts` + `libs.versions.toml`). New
  `MainDispatcherRule` (`app/src/test/.../MainDispatcherRule.kt`) swaps `Dispatchers.Main`
  for an `UnconfinedTestDispatcher` per test, since `viewModelScope` needs a Main dispatcher
  that doesn't exist on a plain JVM (no Android Looper) without it.
- New fakes (`app/src/test/.../fakes/`): `FakeAuthRepository`, `FakeProfileRepository` --
  configurable `Result<T>` fields per method, following the same "depend on the interface,
  inject a fake in tests" pattern the backend already uses for `AIProvider`/
  `StorageProvider`/`EmailProvider`. `httpException(code)` builds a real
  `retrofit2.HttpException` so tests can exercise a ViewModel's `e.code()`-branching error
  handling without a real network call.
- 25 new tests across 7 files, all passing: `LoginViewModelTest` (success, 401, other HTTP
  error, network failure), `SignupViewModelTest` (success chains to login, 409, 422, network
  failure), `ForgotPasswordViewModelTest` (step transitions, request failure stays on step
  1, valid-code success calls `onDone`, invalid code doesn't), `SettingsViewModelTest`
  (logout, delete-account success/wrong-password/network-failure, state reset),
  `ProfileViewModelTest` (load/save success and failure), `LegalDocumentViewModelTest`
  (generic loader success/failure/retry), `HomeViewModelTest` (real time-of-day greeting,
  not a placeholder).
- Verified: `./gradlew testDevDebugUnitTest` -- BUILD SUCCESSFUL, 25/25 passing, 0 failures,
  0 warnings.
- **Not done, scoped honestly rather than claimed**: the remaining ~14 ViewModels
  (interview flow, job matching/JD paste, resume upload, tests/assessments, roadmap,
  role selection) have no unit tests yet -- this item covers auth + settings + profile +
  home as the highest-value/most-recently-changed slice, not full ViewModel coverage.
  Compose UI tests (`androidx.compose.ui.test`) and instrumented (`androidTest`) tests are
  also not covered here -- the `androidTest`/Espresso scaffolding already in
  `build.gradle.kts` from the initial template still has no actual test files. A follow-up
  pass extending this same fake-repository pattern to the rest of the ViewModels, plus
  Compose UI tests for the two or three most critical screens (login, resume upload,
  interview), would be the natural next step if deeper coverage is wanted later.

**Production readiness item 8/20 — Forgot password / account recovery (2026-08-23).**
Backend added `POST /auth/forgot-password` / `/auth/reset-password` (see
`ai-placement-backend/README.md`). Adds the Android surface: a two-step "Forgot password"
flow reachable from Login.
- New `Screen.ForgotPassword` route + a "Forgot password?" link on `LoginScreen`.
- `ForgotPasswordScreen` is one composable with two internal steps (not two nav
  destinations — there's no need for back-stack semantics between them, and it keeps the
  in-flight email/step state in one place): step 1 collects the email and calls
  `forgot-password`; step 2 collects the emailed code + a new password and calls
  `reset-password`. `ForgotPasswordViewModel` drives both steps and exposes which one is
  active via a small `ForgotPasswordStep` enum.
- On success, `NavGraph` pops back to `Login` — the user logs in fresh with their new
  password rather than being auto-logged-in, since password reset doesn't return tokens
  (it's a REST action, not an auth grant).
- `AuthApi`/`AuthRepository` gained `forgotPassword`/`resetPassword`, following the exact
  same shape as every other repository method already in the app.
- Verified: `./gradlew compileDevDebugKotlin` BUILD SUCCESSFUL.

**Production readiness item 7/20 — Terms of Service (2026-08-23).** Backend added a public
`GET /legal/terms-of-service` (see `ai-placement-backend/README.md`). Adds the Android
surface, and refactors item 6's Privacy screen to share code with it rather than
duplicating fetch/Loading/Error/Success logic a second time.
- New `LegalDocumentViewModel` (generic: takes a `documentLoader` lambda + error message)
  and `LegalDocumentContent` composable replace the item-6 `PrivacyViewModel` — `PrivacyScreen`
  now just supplies `network.legalRepository::getPrivacyPolicy`, and the new `TermsScreen`
  supplies `::getTermsOfService`. Same rendered behavior as before, less duplicated code.
- New `Screen.Terms` route (`settings/terms`), wired into `NavGraph.kt`. `SettingsScreen`
  gets an `onOpenTerms` callback and a "Terms of Service" card next to the existing Privacy
  one.
- Verified: `./gradlew compileDevDebugKotlin` BUILD SUCCESSFUL.

**Production readiness item 6/20 — Real Privacy Policy (2026-08-23).** Backend added a
public `GET /legal/privacy-policy` (see `ai-placement-backend/README.md` for what the
policy actually says and its caveats). `PrivacyScreen` was a Stage-2 `PlaceholderScreen`
stub since it was first scaffolded — this replaces it with the real thing.
- New `LegalApi`/`LegalRepository`/`LegalRepositoryImpl`/`PrivacyViewModel`, following the
  exact same fetch-and-render pattern as `ProfileViewModel` (Loading/Error/Success via the
  shared `UiState`, `LoadingState`/`ErrorState` composables).
- `PrivacyScreen` now renders the fetched policy text in a scrollable `Text`, no markdown
  renderer added — the backend content is plain text, so no new dependency was needed for
  this.
- Verified: `./gradlew compileDevDebugKotlin` BUILD SUCCESSFUL. No on-device run (plain
  network fetch + `Text` render, same shape as every other data screen already covered by
  existing manual QA passes).

**Production readiness item 5/20 — Complete account deletion (2026-08-23).** Backend added
`DELETE /users/me` (see `ai-placement-backend/README.md`); this adds the Android surface for
it in Settings, since a backend-only capability isn't "complete" if there's no way to reach
it from the app.
- `UsersApi.deleteMyAccount` uses `@HTTP(method = "DELETE", path = "users/me", hasBody =
  true)` — Retrofit's `@DELETE` annotation doesn't support a request body, and the backend
  requires the current password to guard against a stolen/leaked access token alone
  triggering an irreversible wipe.
- `SettingsScreen` gets a "Delete account" card (styled with `colorScheme.error`) that opens
  a confirmation `AlertDialog` requiring password entry. A wrong password surfaces "Incorrect
  password" inline and leaves the dialog open; success clears local tokens
  (`authRepository.logout()`, redundant with the server already invalidating the token, but
  keeps the client state in sync) and reuses the existing `onLoggedOut` nav callback — same
  "pop to Login, clear backstack" behavior as the Log out button.
- No new dependency, no new nav route — reuses `AlertDialog`/`OutlinedTextField` and the
  existing `Screen.Settings` → `Screen.Login` transition already wired for logout.
- Verified: `./gradlew compileDevDebugKotlin` BUILD SUCCESSFUL. **Not done**: no on-device/
  emulator run of the delete flow — the backend endpoint itself was live-verified end-to-end
  against real PostgreSQL (see backend README); this is standard Retrofit/Compose wiring,
  not new client logic worth a device-specific check on its own.
- No automated Android tests added — the project has no test suite yet at all (that's item
  9, "Android automated testing," not yet reached).

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
- Not yet built: roadmap update screen, Hilt/Koin DI, HTTPS before release. (Encrypted token
  storage was later built — see the 2026-08-28 entry near the top of this status log; this
  line is historical and predates that fix.)
