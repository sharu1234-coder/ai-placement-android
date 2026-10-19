# VA-11 — physical-device acceptance checklist

**Status: BLOCKED. No physical Android device is attached to this environment, so no row below
is marked PASS.** Every status is `NEEDS TEST` unless a prerequisite is missing in the code
itself, in which case it is `BLOCKED` and the missing contract is named.

Detection evidence, run in this phase:

```
$ adb devices -l
* daemon not running; starting now at tcp:5037
* daemon started successfully
List of devices attached
                                  (empty)
$ adb version
Android Debug Bridge version 1.0.41 / Version 37.0.0-14910828
```

ADB works; nothing is connected. A Windows PnP enumeration for Android/ADB/OEM device classes
also returned nothing, so this is not a phone attached in charging-only or MTP mode — there is
no handset present. Two emulator AVDs exist (`UE_pixel_6_API_35`, `p27_api36`) and are
**deliberately not used**: VA-11 §2 and §10 forbid substituting emulator results for device
acceptance, and an emulator's virtual microphone cannot validate the things this phase is for —
OEM audio stacks, real acoustics, Bluetooth routing, mic gain and AGC.

This document supersedes nothing. `VOICE_READINESS_CHECKLIST.md` (production item 10/20) remains
the earlier code-review pass; two of its findings are re-verified against current code below,
and **one of them is now out of date**.

## 1. What a device can actually reach — read this first

This is the most important section. The app contains two separate audio paths, and only one is
reachable from any screen.

| Layer (VA-11 §4) | Implementation | Reachable on a device? |
|---|---|---|
1. Device capture/playback | `voice/AudioRecorder.kt` (MediaRecorder, AAC/m4a 44.1 kHz), `voice/AudioPlayer.kt` (MediaPlayer, WAV) | **Yes** — via `VoiceInterviewScreen`, routed at `ui/navigation/NavGraph.kt:370` |
1′. Device capture/playback, Voice Core | `voice/core/AndroidVoiceAudio.kt` (AudioRecord/AudioTrack, PCM16, **with audio focus**) | **No** |
2. WebSocket upgrade + authenticated session | `voice/realtime/WebSocketRealtimeTransport.kt` | **No** |
3. Control-message exchange | `voice/realtime/RealtimeTransport.kt`, `RealtimeConversationState.kt` | **No** |
4. Binary audio-frame delivery | same | **No** |
5. Speech recognition | — | **No** (backend gateway performs none) |
6. Orchestrator / interview-engine invocation from the socket | — | **No** |
7. Synthesis and playback of the response | — | **No** |

Layers 1′ and 2–4 are unreachable because **nothing outside `voice/core/` and `voice/realtime/`
references those packages.** Verified:

```
$ grep -rln "voice\.core\|voice\.realtime" app/src/main/ | grep -v "voice/core/\|voice/realtime/"
                                  (empty)
```

The only mentions of the legacy classes inside `AndroidVoiceAudio.kt` are KDoc comments saying it
is "deliberately separate from" them. So the Voice Core and the realtime transport are complete,
unit-tested (164 JVM tests across 7 files) and **not mounted in the app**.

**Consequence for this phase:** attaching a phone would validate layer 1 and the legacy HTTP
upload path, and nothing else. Layers 2–7 stay BLOCKED on hardware availability *and* on
integration work that no phase has done — mounting the Voice Core in the UI, adding interview
message types to the gateway protocol, and an approved STT/TTS provider. A green layer 1 must
never be reported as a working voice agent.

## 1b. Reachability after the realtime integration — supersedes the table above

The table in §1 described the state VA-11 and VA-12 found: the Voice Core and the whole realtime
package were built, tested and **constructed nowhere** under `main/`. That is no longer true. The
rows below replace it.

| Layer | Implementation | Constructed by | Reachable on a device? |
|---|---|---|---|
| 1. Device capture/playback (turn-based) | `voice/AudioRecorder.kt`, `voice/AudioPlayer.kt` | `NetworkModule.voiceInterviewManager` | **Yes** — `VoiceInterviewScreen` |
| 1′. Device capture/playback, Voice Core (PCM16, with audio focus) | `voice/core/AndroidVoiceAudio.kt` | `NetworkModule.createRealtimeVoiceSession` | **Yes** — `VoiceAgentScreen` |
| 2. WebSocket upgrade + authenticated session | `voice/realtime/WebSocketRealtimeTransport.kt` | same | **Yes** — `VoiceAgentScreen` |
| 3. Control-message exchange | `RealtimeTransport.kt`, `RealtimeConversationState.kt` | `RealtimeVoiceSession` | **Yes** |
| 4. Binary audio-frame delivery | same | same | **Yes** |
| 5. Speech recognition | backend `app/speech/faster_whisper_stt.py` via `TurnRuntime` | backend gateway | **Yes, under development configuration only** |
| 6. Coach / interview-engine invocation from the socket | backend `app/voice/responders.py` | backend gateway | **Yes** |
| 7. Synthesis and playback of the response | backend `app/speech/pyttsx3_tts.py` → `AudioChunk` → `AudioTrackPcmPlayback` | both halves | **Yes, under development configuration only** |
| 8. Confirmed action from a real UI | `ui/actions/ActionConfirmationHost.kt` | `VoiceAgentScreen` | **Yes**, and only from an explicit tap |

### 1c. The three entry points a device tester uses (2026-10-13)

Superseding the single-entry-point note that was here: there are now three, and two of them are
the only way to reach the interview mode and the save-job confirmation at all.

| # | Journey | Route built | What it carries |
|---|---|---|---|
| 1 | Navigation drawer → **"Voice Career Coach"** | `voiceAgentRoute(COACH.wire)` → `voice-agent/coach/0/0/0` | nothing; general conversation, **no save-job control** |
| 2 | Job Hunt → a job advert → **"Talk this job over with the Career Coach"** | `voiceAgentRoute(COACH.wire, listingId = listingId)` | the advert's authoritative `listingId`; this is the **only** route on which the save-job confirmation appears |
| 3 | Voice Interview → a pending question → **"Answer by talking live instead"** | `voiceAgentRoute(INTERVIEW.wire, interviewId, questionId)` | both ids, read off the server-confirmed `Turn` |

Journey 3 returns through `VoiceInterviewScreen`'s `ON_RESUME`, which re-reads the pending
question from the server. So a device tester should expect the interview screen to land on
*whatever question the conversation left pending*, including a completed interview — not on the
question they left. If it redraws the question they already answered aloud, that is a defect.

Journey 1 deliberately shows no save-job button. That is correct, not a bug: `jobs.save` needs a
`listing_id` and the drawer route has no advert in scope.

Entry point reference: `navigation/MainScreen.kt` (journey 1),
`ui/jobhunt/JobDetailScreen.kt` → `NavGraph.kt` (journey 2),
`ui/interview/VoiceInterviewScreen.kt` → `NavGraph.kt` (journey 3), all routed through
`Screen.VoiceAgent`.

### What still has not been exercised on hardware

**Physical-device acceptance: BLOCKED.** Detection was attempted, not assumed:

```
$ adb devices -l
List of devices attached
                                  (empty)
$ adb shell getprop ro.product.model
adb.exe: no devices/emulators found
```

No emulator was substituted. Every Android test backing this increment runs on the JVM with the
transport and **all audio hardware faked**, so none of them says anything about microphone
capture, speaker playback, acoustic echo, routing, or latency. The rows marked "Yes" above are
claims about *reachability of code*, not about audio quality or about a conversation anyone has
had on a phone.

Separately, layers 5 and 7 require `STT_PROVIDER`/`TTS_PROVIDER` to be set to the
development-only adapters. On a stock server both default to `none` and a committed turn returns
`stt_unavailable`. Linux TTS remains **NOT PROVEN** — the host driver result is Windows SAPI5.

What that configuration is, is written down in `ai-placement-backend/.env.development.speech`
and bound to the production guard by `tests/unit/test_development_speech_config.py`. It is
applied per server process, never through `.env` -- the test suite loads `.env` too. On this
Windows host, with it applied, one bounded backend turn ran with real inference at both ends:
pyttsx3 returned a decodable 22 050 Hz mono WAV (115 940 bytes, 2.628 s, peak 30 721) and
faster-whisper `tiny.en` recognised 8 of 8 words from it. That is evidence about this host and
nothing else — not about a handset's microphone, and not about a Linux deployment.

## 1d. Priority run order (2026-10-14) — do these seven first

Device acceptance is **BLOCKED**: a fresh `adb devices -l` on 2026-10-14 started a new daemon,
exited 0, and listed no devices. No emulator was substituted. `LS §12 (p.17) VA-11` requires a
physical handset and forbids a production-quality claim from emulator-only testing.

This section exists so that the first hour on a real phone is spent well. The full matrix in §3
stays authoritative for coverage; this is the order to attack it in, with the exact taps and the
evidence to capture. Every row below is **NEEDS TEST** — none has ever been run on hardware.

### Before you start

```
adb devices -l                                  # must list a real serial, not "emulator-5554"
adb shell getprop ro.product.model               # record the handset in the evidence table
adb logcat -c                                    # clear, so the capture below is only this run
adb logcat -s VoiceInterview:I AudioManager:I    # leave running in a second terminal
```

Install the dev variant (`com.aiplacementos.app.dev`). The backend must be reachable from the
handset and must have development speech exported into its process — see
`ai-placement-backend/.env.development.speech`; without it a committed turn returns
`stt_unavailable`, which is correct but tests nothing about audio.

### The seven, in order

| # | What | Exact steps | Observable evidence | Pass condition |
|---|---|---|---|---|
| 1 | **D2 foreground/lifecycle — recording must stop** | Drawer → *Voice Career Coach* → grant mic → **Start** → begin speaking → press **Home** | `VoiceInterview` logcat; `adb shell dumpsys media.audio_flinger \| grep -i "input\|record"` | Capture stops at `ON_STOP`, not `ON_PAUSE`. No input client remains after backgrounding. Pulling down the notification shade alone must **not** end the session |
| 2 | **D1 audio focus acquire/release** | Start a turn, then play music in another app mid-turn; then end the session | `adb shell dumpsys audio \| grep -A5 "focus"` | Focus is held during capture **and** playback, and released on teardown. Focus loss stops capture rather than ducking it; the UI says so rather than showing "Listening…" over a dead recorder |
| 3 | **Real microphone capture and playback** | One complete turn: speak a known sentence, wait for the reply | Transcript rendered on screen; audio audible from the earpiece/speaker | The transcript is *your words*. Reply audio plays to completion. Neither is fabricated — the server cannot emit a transcript it did not recognise |
| 4 | **A complete spoken Career Coach turn** | Job Hunt → open a job advert → *Talk this job over with the Career Coach* → ask about that job | Screen + logcat | One `final_transcript`, one `assistant_message`, audio, `generation_done`. With the Null job provider active the agent must **say** live market data is unavailable, never invent listings |
| 5 | **A complete spoken Interview Coach turn** | Start a voice interview → on a pending question tap *Answer by talking live instead* → answer aloud → press Back | Interview screen after returning | The interview advances **exactly once**. Returning lands on the question the server says is pending — if it redraws the question you just answered aloud, that is a defect |
| 6 | **Interruption, cancellation, network loss** | (a) Speak over the agent mid-reply. (b) Tap stop mid-reply. (c) Enable airplane mode mid-turn, then disable | Screen notices + logcat | Barge-in stops playback and no further audio arrives for that generation. Cancellation is described honestly — a recogniser already running server-side cannot be interrupted, only its result discarded. Reconnect resumes or fails honestly; it never claims a turn succeeded |
| 7 | **Confirmation UI and verified outcomes** | From the job-advert route (#4), tap *Save this job* → read the server's preview → **Confirm**. Then repeat and **Cancel**. Then double-tap Confirm | Screen; `GET` the saved-jobs list to verify | The preview is the server's own text. Cancel writes nothing. A double tap sends one execute. The outcome distinguishes saved / already-saved / failed / expired / unknown. **Saying "yes" aloud must do nothing at all** |

### What would falsify the build

Any of these is a defect to report, not a quirk:

- The microphone stays open after Home (D2 regression).
- Music keeps playing under a turn, or stays ducked after teardown (D1 regression).
- A transcript appears that is not what you said.
- The interview advances twice, or returns you to a question you already answered aloud.
- A spoken "yes" performs a save.
- Any success message for an action whose outcome was not verified.

### Evidence to record per row

Handset model and Android version; the `adb` command and its output; the on-screen text; the
relevant `VoiceInterview` log lines; and for #3–#5 the wall-clock time from when you stopped
speaking to the first audible word — that is the first of the six metrics `LS §13.3 (p.18)`
requires and **none of them has been measured yet**.

## 2. Setup for the device run

1. Start the backend on the host and note its LAN address. The dev flavour reads
   `API_BASE_URL` from `devApiBaseUrl` (`app/build.gradle.kts:116`), so point that at the host
   rather than `localhost`; `usesCleartextTraffic` is set per flavour, so plain HTTP works on
   `dev` only.
2. `gradlew installDevDebug`. The installed package is **`com.aiplacementos.app.dev`**
   (`applicationIdSuffix = ".dev"`), which is the id every `adb` command below needs.
3. Sign up or log in, pick a role, then start an interview with **mode = voice** to land on
   `VoiceInterviewScreen`.
4. Record the device model, Android version, build variant, and both repo HEADs in the results
   table before starting.

### Observability — and a real limitation

**The reachable path emits no logs at all.** `grep` for `Log.` across `AudioRecorder.kt`,
`AudioPlayer.kt`, `VoiceInterviewManager.kt` and `VoiceInterviewViewModel.kt` returns nothing;
the only `Log` tag in the voice tree is `"VoiceCore"` in the unmounted `AndroidVoiceAudio.kt`.
So `adb logcat -s VoiceCore` will be silent for everything a student can actually do, and
defect capture has to come from three other places:

- **UI state**, which is the real contract. `VoiceTurnPhase` →
  `LOADING_AUDIO` shows "Playing the question…", `READY_TO_RECORD` shows the record control,
  `RECORDING` shows the elapsed timer, `SUBMITTING` shows "Transcribing your answer…".
  `VoiceInterviewUiState` is `Starting` / `Turn(question, phase)` / `Error(message)` /
  `Completed(interviewId)`.
- **System audio state**: `adb shell dumpsys audio` (focus stack, active recorders/players).
- **Backend access log**, which records each `POST /interviews/{id}/answer/audio` and
  `GET /interviews/{id}/questions/{qid}/audio` with status and request id.

This logging gap is recorded as defect **D3** below, because VA-11 §5 asks for "the relevant
log" on every defect and on this path there is none to capture.

## 3. Test matrix

Fill `Status` with PASS / FAIL / NEEDS TEST / BLOCKED and attach the evidence named in the last
column. Every row currently reads NEEDS TEST (no device) or BLOCKED (missing contract).

### A. Microphone and permission lifecycle

| ID | Scenario | Steps | Expected | Evidence to capture | Status |
|---|---|---|---|---|---|
A1 | First permission request | Fresh install, open voice interview | Rationale screen, then the system dialog — not a bare dialog with no context (`VoiceInterviewScreen.kt` requests via `ActivityResultContracts.RequestPermission()`) | screenshot; `adb shell dumpsys package com.aiplacementos.app.dev \| grep -A3 RECORD_AUDIO` | NEEDS TEST |
A2 | Granted, capture starts | Grant, tap record | Phase → `RECORDING`, timer advances | screenshot; `adb shell dumpsys audio` shows an active record client | NEEDS TEST |
A3 | Denied | Deny at the dialog | "grant microphone access" screen; **no** record control; no capture attempt | screenshot | NEEDS TEST |
A4 | Permanently denied | Deny twice (or "don't ask again") | Same guarded screen, no crash, no silent retry loop | screenshot | NEEDS TEST |
A5 | **Revoked while backgrounded** | On the voice screen, background the app, `adb shell pm revoke com.aiplacementos.app.dev android.permission.RECORD_AUDIO`, return | `ON_RESUME` re-check hides the record control (`DisposableEffect` on `ON_RESUME`) rather than letting a tap throw `SecurityException` | screenshot before/after; logcat for absence of `SecurityException` | NEEDS TEST |
A6 | Permission missing at session start | Revoke, then start a new voice interview | Guarded screen, no capture | screenshot | NEEDS TEST |
A7 | User cancels mid-recording | Start recording, cancel | Capture stops; recorder released; temp file deleted from `cacheDir` | `adb shell run-as com.aiplacementos.app.dev ls cache`; `dumpsys audio` shows no record client | NEEDS TEST |
A8 | Repeated start/stop across turns | Complete 5 turns, recording each | No degradation, no "mic busy" failure on a later turn | screenshots per turn | NEEDS TEST |
A9 | Cleanup after normal completion | Finish the interview | No record client remains; cache cleared | `dumpsys audio`; `ls cache` | NEEDS TEST |
A10 | Cleanup after failure | Force an upload failure (stop the backend mid-submit) | Typed `Error(message)` state, recorder released, answer not falsely reported stored | screenshot; backend log absence | NEEDS TEST |
A11 | **No recording after the session ends** | Finish or leave the screen, then check | No active record client at all | `adb shell dumpsys audio \| grep -i record`; `appops get com.aiplacementos.app.dev RECORD_AUDIO` | NEEDS TEST — **see D2** |

### B. Audio capture and playback

| ID | Scenario | Expected | Status |
|---|---|---|---|
B1 | Capture initialises and real samples arrive | Recorded `.m4a` is non-trivial in size and plays back as speech | NEEDS TEST |
B2 | Format as declared | AAC in MPEG-4, 44.1 kHz, 96 kbps (`AudioRecorder.kt`); backend writes the bytes straight to a `.m4a` tempfile with no transcoding | NEEDS TEST |
B3 | Playback starts, is intelligible, stops promptly | Question WAV from `synthesize_speech()` plays via MediaPlayer | NEEDS TEST |
B4 | Buffers do not grow without bound | 2-minute `MAX_DURATION_MS` cap ⇒ ≈1.44 MB, under the backend's 10 MB cap | NEEDS TEST |
B5 | Cancellation/closure releases audio resources | No leaked recorder or player | NEEDS TEST — **see D2** |
B6 | Rapid start/stop does not wedge the audio system | 10 fast cycles leave capture working | NEEDS TEST |
B7 | **Audio focus requested, handled, released** | — | **FAIL predicted — see D1.** The reachable path never calls `requestAudioFocus` |
B8 | Failures surface as accurate typed states | `AudioRecordingException` → "couldn't access the microphone", not a crash | NEEDS TEST |

### C. Routing and hardware changes

All rows need hardware this environment does not have. Mark NEEDS TEST unless the accessory is
actually present; do not infer one route's behaviour from another's.

| ID | Scenario | Status |
|---|---|---|
C1 | Built-in mic and speaker | NEEDS TEST |
C2 | Wired headset connect / disconnect mid-session | NEEDS TEST |
C3 | Bluetooth headset connect / disconnect mid-session | NEEDS TEST |
C4 | Route change during capture, and during playback | NEEDS TEST |
C5 | Audio focus loss and regain | **FAIL predicted — D1** |
C6 | Another app requests focus | **FAIL predicted — D1** |
C7 | Reported state matches the actual route; stale callbacks from an old route cannot overwrite a newer session | NEEDS TEST |

### D. Interruption handling

| ID | Scenario | Expected | Status |
|---|---|---|---|
D-1 | Incoming phone call during recording | Capture stops or suspends safely; focus released; no stale resume; no answer submitted on return | **FAIL predicted — D1 + D2** |
D-2 | Switch to another app during recording | Capture stops | **FAIL predicted — D2** |
D-3 | Return from background | No stale audio resumed; no unexpected submission | NEEDS TEST |
D-4 | Screen lock / unlock during recording | Capture stops | **FAIL predicted — D2** |
D-5 | **Rotation / activity recreation during a turn** | In-flight turn handled safely, recorder released | **FAIL predicted — D2** |
D-6 | System focus loss | Handled | **FAIL predicted — D1** |
D-7 | User cancellation | Clean stop | NEEDS TEST |
D-8 | Transport disconnection while audio active | Truthful error | NEEDS TEST |

A simulated callback in a JVM test does not satisfy any row here; each needs the real event.

### E. Noise, echo and real environments

Every row NEEDS TEST and none may be given a number without a repeatable measurement. Record
what was *heard*, not an invented accuracy figure: quiet room; background conversation; moderate
environmental noise; speaker playback while the mic is live; headset playback with headset mic;
varying distance and direction.

When reporting, keep these five apart — the device capturing audio, the client uploading it, the
backend recognising it, the agent composing a reply, the device speaking that reply. On the
reachable path only the first three can happen at all, and recognition quality is bounded by the
backend's dev-only `faster-whisper` on CPU.

### F. Network loss and recovery

| ID | Scenario | Expected | Status |
|---|---|---|---|
F1 | Wi-Fi lost mid-upload (`adb shell svc wifi disable`) | Truthful error; the answer is **not** reported as submitted | NEEDS TEST |
F2 | Network restored | Retry is explicit, not automatic for an unknown outcome | NEEDS TEST |
F3 | Wi-Fi ↔ mobile data switch | Session survives or fails truthfully | NEEDS TEST |
F4 | Backend down at connect | Typed error, no crash | NEEDS TEST |
F5 | Interrupted while a turn is in progress | No duplicate answer; the server's unique index on `interview_question_id` decides | NEEDS TEST |
F6 | Backgrounded during loss | No stale completion | NEEDS TEST |
F7 | Return with expired process-local state | Honest refusal, not a false recovery | NEEDS TEST |
F8 | Resume from server-confirmed state | — | **BLOCKED**: the reachable path has no resume protocol; `/voice/session` resume exists but is unmounted |

The backend guarantees behind F5 are already proven server-side (gated concurrency tests, the
unique index, and the VA-9 voice-turn states). What needs a device is whether the *client*
reports them truthfully.

### G. Battery and resources

| ID | Scenario | Expected | Status |
|---|---|---|---|
G1 | Sustained bounded session (state the duration) | No unbounded CPU/memory growth | NEEDS TEST |
G2 | Repeated audio resource creation | One recorder/player per turn, released each time | NEEDS TEST |
G3 | Leaked recorders, players, threads, callbacks, sockets | None | NEEDS TEST — **D2 predicts leaks on interruption** |
G4 | Retry/reconnect loops | Bounded | NEEDS TEST |
G5 | Stuck foreground/background work | None | NEEDS TEST |
G6 | Cleanup after app exit and process recreation | Clean | NEEDS TEST |

`adb shell dumpsys meminfo com.aiplacementos.app.dev`, `dumpsys batterystats`, and
`dumpsys audio` are the instruments. A short run proves nothing about long-duration
reliability; record the actual duration beside every result.

## 4. Open defects confirmed by code in this phase

These are **predictions for the device run**, backed by current file/line evidence. They are not
marked FAIL in the matrix, because VA-11 §6 reserves FAIL for behaviour actually exercised on
hardware.

### D1 — the reachable audio path requests no audio focus

`grep -rn "requestAudioFocus\|AudioFocusRequest" app/src/main/` matches **only**
`voice/core/AndroidVoiceAudio.kt` (lines 356–398), which implements focus correctly:
`AUDIOFOCUS_GAIN_TRANSIENT`, a listener handling `GAIN` / `LOSS_TRANSIENT` /
`LOSS_TRANSIENT_CAN_DUCK` / `LOSS`, and `abandonAudioFocusRequest` on release. That file is in
the **unmounted** package. `AudioRecorder.kt` and `AudioPlayer.kt` — the classes the routed
screen uses — request no focus at all, so a call, a notification sound or another media app
during record or playback behaves however the OEM defaults, which the app does not control.

This updates `VOICE_READINESS_CHECKLIST.md`, which recorded "no audio focus handling" as a flat
gap. The correct current statement is narrower and more awkward: **focus handling exists and is
tested, in the path no screen can reach.**

Not fixed in VA-11, deliberately. The acceptance criterion is device-observed behaviour, so a
blind fix would ship unvalidated code and an unprovable PASS; and the natural resolution is
mounting the Voice Core rather than copying focus logic into the legacy classes, which is an
integration decision outside device acceptance.

### D2 — no lifecycle stop: an interrupted recording is orphaned

Two facts, both current:

- `VoiceInterviewScreen.kt:61` — `val manager = remember { VoiceInterviewManager(...) }`. The
  manager is scoped to the composition, so activity recreation drops the instance holding the
  live `MediaRecorder`.
- The screen's `DisposableEffect(lifecycleOwner)` observer handles **`ON_RESUME` only** (the
  permission re-check). There is no `ON_PAUSE` or `ON_STOP` branch, so nothing stops or releases
  a recording when the app is backgrounded, the screen locks, or a call arrives.
- `AndroidManifest.xml` sets neither `configChanges` nor `screenOrientation`, so rotation
  recreates the activity.

Predicted device behaviour: rows A11, B5, D-1, D-2, D-4, D-5 and G3 fail, and the microphone may
keep recording after the app leaves the foreground. **A11 is the row to run first** — it is the
privacy-relevant one, and `adb shell dumpsys audio | grep -i record` answers it in one command.

Not fixed in VA-11 for the same reason as D1, plus one more: the remedy is a product choice —
lock the interview screens to portrait, or hoist recorder ownership into the `ViewModel` (which
already holds a `SavedStateHandle`) so it survives recreation. Picking one is not device
acceptance, and §8 forbids feature work to make this report look complete.

### D2a — the regression-test pattern for D2 already exists, but not for voice

`app/src/androidTest/` holds 16 instrumentation tests, and two of them already cover exactly the
class of behaviour D2 concerns: `ui/careerpath/CareerPathGenerationRotationTest.kt` and
`ui/jobprep/JobMatchPrepCareerPathLifecycleTest.kt`. There is **no voice-interview equivalent**.

So closing D2 does not need a new approach, only the same pattern applied to
`VoiceInterviewScreen` — a test that starts a turn, recreates the activity, and asserts the
recorder was released and no answer was submitted. That test is written against
`androidx.compose.ui.test` + Espresso (already declared in `app/build.gradle.kts:261-264`) and
**requires a connected device or emulator to run**, so it is listed here as the regression
evidence the device run should produce rather than something this phase could add and verify.

### D3 — no diagnostics on the reachable path

No `Log` call exists in `AudioRecorder.kt`, `AudioPlayer.kt`, `VoiceInterviewManager.kt` or
`VoiceInterviewViewModel.kt`. VA-11 §5 requires capturing "the relevant log" for each defect;
on this path there is none, so every observation above has to come from UI state or `dumpsys`.
Worth closing before the device run, because it decides how well that run can be debugged.

## 5. What this phase does not establish

- No row is PASS. Nothing here was executed on hardware.
- The Voice Core's 164 JVM tests and the backend's voice suites pass, and that is **not** device
  evidence — they are the reason the device run can be short, not a substitute for it.
- Layers 2–7 remain BLOCKED on: an approved STT provider, an approved TTS provider, gateway
  interview message types, and mounting the Voice Core in the UI. ElevenLabs stays excluded and
  no provider was selected here.
- Process-local registries (VA-2 sessions, agent memory, VA-7 confirmations) still have no
  cross-worker recovery; F7 is the row that would observe the honest refusal on a device.
