# TALVIORA AI Android — forensic audit and repair, 2026-10-19

SGLD Quant Labs. Branch `career-strategy-intelligence`, baseline HEAD
`3ad14db8085dbc30bc49e7a27ae353f6532c0e2a`.

The baseline was clean and level with `origin`, so no pre-existing uncommitted work existed to
preserve and every change now in the working tree belongs to this audit. Nothing was committed
or pushed.

The cross-repository findings, root causes and measurements live in the backend repository at
`docs/forensic-audit-2026-10-19.md`. This file records what changed **here** and what the
Android side still cannot prove.

---

## 1. Changes

### A client can now say it finished (Finding 3)

The wire protocol had no way to express a deliberate ending. Its client vocabulary was
`commit_turn`, `interrupt`, `resume`, `cancel_operation` and `heartbeat`, so a student closing
a conversation looked exactly like a dropped socket — and the backend resolved that ambiguity
the wrong way, recording `completed` for idle timeouts, lost signal, oversized frames and
protocol violations alike. A student who lost signal mid-interview got a history entry saying
they finished it.

| File | Change |
|---|---|
| `voice/realtime/RealtimeTransport.kt` | `RealtimeClientEvent.EndSession` |
| `voice/realtime/WebSocketRealtimeTransport.kt` | encodes it as `{"type":"end_session"}` |
| `voice/realtime/RealtimeVoiceSession.kt` | `stop(deliberate: Boolean = false)` sends it, before teardown, while the socket is still open |
| `ui/voiceagent/VoiceAgentViewModel.kt` | `endSession()` for the deliberate case; `stop()` keeps the old meaning |
| `ui/voiceagent/VoiceAgentScreen.kt` | the "End session" button calls `endSession` |

**The default is the safety property.** `deliberate` defaults to `false` because most stops are
not completions: `onLeftForeground()` (`ON_STOP`) and `onCleared()` both call `stop()`, and
neither means the student finished. Only the button does.

The send is best-effort by design. If it fails, the server never sees an end and records
`interrupted` — the honest answer for a socket that died on the way out. A failure here cannot
manufacture a false completion.

Backward compatible in the safe direction: an older client that never sends `end_session`
simply never produces a `completed` conversation.

### A guard on the audio-output wiring (committed in the baseline, not changed here)

This was added just before this audit's baseline and is already in `3ad14db`. It is recorded
here because it is the guard protecting the routing half of the voice defect, not because this
audit wrote it.

`VoiceSession`'s `output` parameter defaults to the inert `AudioOutputRouter.None`, so deleting
`output = AndroidAudioOutputRouter(appContext)` from `NetworkModule.createRealtimeVoiceSession`
would still compile, still pass every test in `VoiceSessionOutputRoutingTest` (which supplies
its own fake router), and silently return playback to the earpiece on the voice-call stream —
the reported "too quiet / wrong speaker" defect.

`voice/core/VoiceOutputWiringGuardTest.kt` now asserts the construction site, that
`AudioOutputRouter.None` is never what production is wired to, and that `VoiceSession` actually
calls `output.begin()` and `output.end()` so a router that is passed cannot be ignored.

That guard's first version was itself wrong: it matched `NetworkModule`'s explanatory *comment*
mentioning `AudioOutputRouter.None` and failed on the repository's own documentation. It now
matches the assignment.

### CI (Finding 7)

`.github/workflows/ci.yml` triggered on `master` only, while all work happens on
`career-strategy-intelligence`. Confirmed with `gh run list`: the only runs are Dependabot on
`master` and on `dependabot/*` PR branches. **No workflow has ever run against this branch
head.** The workflow now triggers on `master` plus this branch, and `pull_request` carries no
branch filter so a PR into any branch is checked. `permissions: {contents: read}` and the
`build` job are unchanged; nothing was deleted and no deployment permission added.

This remains **unproven on GitHub**: the change is local and unpushed, so CI has still run
nothing. A local Gradle pass is not a CI pass.

---

## 2. Validation

| Check | Result |
|---|---|
| `:app:testDevDebugUnitTest` | **1542 tests, 0 failures, 0 errors, 0 skipped** across 177 suites, counted from `TEST-*.xml` rather than from a build message |
| `:app:lintDevDebug` | **0 errors**, 32 warnings, all pre-existing dependency/SDK advisories |
| `:app:assembleDevDebug` | succeeded |
| APK | `app-dev-debug.apk`, 26,951,191 bytes, sha256 `6b756f48…c92d3` |
| Embedded base URL | `http://192.168.0.113:8000/`, read out of the APK's dex — still this machine's Wi-Fi address |
| `androidTest` / instrumentation | **NOT RUN.** No device or emulator available |
| Physical-device acceptance | **BLOCKED.** `adb devices` lists none |

Of those 1542, four are new in this audit (`EndSessionContractTest`). The three wiring-guard
tests were already in the baseline.

---

## 3. What this repository still cannot prove

- **No screen has been seen rendered.** The premium UI work across all 97 screens is verified
  only as far as compilation, unit tests and lint reach. Spacing rhythm, wrapping at 1.5× font
  scale, real contrast, and whether the T monogram is legible at launcher density are all
  unverified.
- **No voice has been heard.** `AndroidAudioOutputRouter` talks to a real `AudioManager` and
  there is no Robolectric on this project, so its mode save/restore and loudspeaker selection
  are not unit-testable here. Whether the reply is actually audible, on the loudspeaker, and
  responsive to the volume keys is a handset question.
- **The APK is unchanged by this audit's Android edits** in the sense that matters for the voice
  fix: the female-voice repair is entirely backend-side. No reinstall is needed, but a backend
  must be running — nothing was listening on port 8000 at any point during this audit.

---

## 4. Change control

No commit, no push, no deploy, no signing, no publication. No dependency added — Robolectric
was considered for the router and deliberately **not** installed. `local.properties`,
`*.jks` and `keystore.properties` are correctly gitignored and were never read beyond the
single `API_BASE_URL` key. No secret printed. The working tree is left dirty with the changes
above visible in `git diff`.
