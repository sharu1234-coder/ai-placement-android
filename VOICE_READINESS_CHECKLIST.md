# Voice interview mode — real-device readiness checklist

Production readiness item 10/20. This item is explicitly scoped in the master prompt as
**"a checklist, not fabricated hardware claims."** This environment has no physical Android
device and no working emulator (the one configured AVD, `UE_pixel_6_API_35`, failed to boot
— see "What was actually attempted" below), so nothing here claims to have been verified by
running the app on real audio hardware. Everything marked ✅ was verified by reading the
actual code (cited by file/line); everything marked 🔲 genuinely requires a physical device
and has not been checked.

## What was actually attempted

- Ran `adb devices` — no device attached.
- Booted the one configured AVD (`UE_pixel_6_API_35`) via the SDK's `emulator.exe` — it
  failed immediately: `FATAL | Cannot find AVD system path` (the
  `android-35/google_apis_playstore/x86_64` system image referenced by the AVD isn't
  actually installed in this SDK setup). Did not chase this further — downloading a system
  image is a multi-GB fetch disproportionate to this item, and even a working emulator's
  virtual microphone (typically silence or a test tone, not real acoustic input) wouldn't
  validate the things that actually matter here: OEM audio-stack quirks, real background
  noise, Bluetooth headset routing, mic gain/AGC behavior. Those genuinely need a physical
  device.
- Built `assembleDevDebug` successfully (confirms the manifest/permissions/resources
  compile and package correctly — see `README.md`'s test-suite status entries for the
  unit-test-level verification already done in item 9).

## Verified by code review

- ✅ **Permission declared correctly.** `RECORD_AUDIO` in
  `app/src/main/AndroidManifest.xml`.
- ✅ **Runtime permission requested, not just declared.** `VoiceInterviewScreen.kt` requests
  `RECORD_AUDIO` via `ActivityResultContracts.RequestPermission()` on first entry, and
  re-checks it on every `ON_RESUME` (a `DisposableEffect` observing the lifecycle) — this
  matters because a student can background the app, revoke mic access in system settings,
  and return; without the re-check the UI would keep showing record controls that throw
  `SecurityException` the moment they're tapped. A clear "grant microphone access" screen is
  shown instead of a raw permission dialog with no context.
- ✅ **Recording format matches what the backend actually expects.** `AudioRecorder.kt`
  records AAC-in-MPEG4 (`.m4a`) at 44.1kHz/96kbps. The backend's
  `OllamaProvider.transcribe()` (`ai-placement-backend/app/ai/ollama_provider.py`) writes
  the uploaded bytes straight to a `.m4a` tempfile for faster-whisper — the two ends agree
  on format without any client-side transcoding.
- ✅ **Playback format matches too.** The backend's `synthesize_speech()` returns WAV bytes;
  `VoiceInterviewManager.playQuestionAudio()` writes them to a `.wav` file and
  `AudioPlayer.kt` (`MediaPlayer`) plays it — no format mismatch.
- ✅ **Realistic upload size margin.** `AudioRecorder`'s `MAX_DURATION_MS` caps a single
  answer at 2 minutes; at 96kbps that's ≈1.44MB, comfortably under the backend's 10MB
  `_MAX_AUDIO_UPLOAD_BYTES` cap (`app/api/interviews.py`) — a full-length answer won't get
  rejected as "too large."
- ✅ **Network timeout accounts for real (slow) transcription latency.**
  `NetworkModule.kt`'s OkHttp client sets a 150s read timeout specifically because
  resume/JD parsing and audio transcription run real (CPU-bound, dev-only) inference
  server-side and can take 1-2 minutes — a student on a slow connection or during a cold
  Whisper-model-load won't see a client-side timeout fire before the server responds.
- ✅ **Common recorder failure modes are caught, not left to crash the app.**
  `AudioRecorder.start()` wraps `prepare()`/`start()` and converts `IOException`/
  `IllegalStateException`/`SecurityException` (mic busy, hardware failure, permission
  revoked mid-session) into a typed `AudioRecordingException` that
  `VoiceInterviewViewModel` turns into a user-facing "couldn't access the microphone"
  message rather than a crash.
- ✅ **No stale audio accumulates on-device.** Both the downloaded question audio and the
  recorded answer are deleted from `cacheDir` immediately after use
  (`VoiceInterviewManager.kt`) — consistent with the project's "minimize stored voice data"
  principle applying on-device, not just server-side.

## Gaps found (concrete, not hypothetical)

- ❌ **No audio focus handling.** Neither `AudioRecorder` nor `AudioPlayer` requests
  `AudioFocus` (`AudioManager.requestAudioFocus`/`AudioFocusRequest`). A phone call,
  another app's notification sound, or another media app starting playback during a
  recording or question-audio playback isn't handled at all — behavior in that case is
  whatever the OS defaults to, which varies by manufacturer and isn't something this app
  currently controls. **Recommended fix**: request focus for the duration of each
  recording/playback and pause/stop gracefully (with a user-facing message) if focus is
  lost, rather than silently continuing or corrupting the take.
- ❌ **No protection against configuration changes or process death mid-turn.** There's no
  orientation lock and no `configChanges` override on `MainActivity`
  (`AndroidManifest.xml`), and `VoiceInterviewManager` is created via
  `remember { ... }` scoped to the composition (`VoiceInterviewScreen.kt`). Rotating the
  screen (or any other config change) recreates the Activity, which drops that `remember`
  instance and orphans whatever `MediaRecorder` was in progress — the in-flight answer is
  lost and the underlying recorder/file isn't cleanly released. **Recommended fix**: either
  lock the interview screens to portrait (simplest), or move recording state into something
  that survives recomposition/recreation (a `ViewModel`-owned recorder + `rememberSaveable`
  state) if landscape support is wanted later.

## Flagged but out of scope for this item

- The actual voice *quality* is a separate, already-documented limitation (item 2, STT/TTS,
  deferred by explicit user choice): server-side STT is `faster-whisper` on CPU and TTS is
  `pyttsx3` — both explicitly marked "dev-only... not production-grade" in their own
  docstrings (`ollama_provider.py`). No amount of Android-side correctness fixes the fact
  that the synthesized question audio is described in the code itself as "robotic." This
  checklist only covers the Android recording/playback *pipeline*, not voice quality.
- **`pyttsx3` is effectively Windows-only as currently used.** Its own docstring says
  "Windows SAPI5 via pyttsx3," and `requirements.txt` conditions `pywin32` on
  `sys_platform == "win32"`. If the backend is ever deployed to a non-Windows production
  host (the common case — Linux containers), `synthesize_speech()` will likely fail outright
  unless a Linux TTS backend (e.g. espeak) is installed and pyttsx3 is reconfigured to use
  it. This is a deployment-portability finding that belongs to item 2's eventual resolution,
  not something to silently paper over here.
- `android:usesCleartextTraffic="true"` in the manifest means the dev build talks to
  `10.0.2.2`/a LAN IP over plain HTTP — fine for local dev, a real gap once shipped. That's
  item 11 (HTTPS/network security), cross-referenced here because it directly affects
  whether recorded audio is encrypted in transit, not re-solved in this item.

## Requires a physical device — cannot be checked here

- 🔲 Real background-noise robustness of Whisper transcription (a quiet room vs. a busy
  street/hostel room).
- 🔲 Bluetooth headset / wired-headset mic routing and any echo-cancellation quirks.
- 🔲 OEM-specific audio-stack behavior (Samsung/Xiaomi/OnePlus etc. are known to diverge
  from stock `MediaRecorder`/`MediaPlayer` behavior in edge cases).
- 🔲 Behavior under aggressive battery-saver / background-restriction modes some OEMs apply,
  which can throttle mic access or kill the process mid-recording.
- 🔲 Actual recording/playback quality perception (is 96kbps AAC "good enough," is the
  synthesized WAV audible at a comfortable volume by default).

## Verdict

The Android-side recording/playback *plumbing* is sound based on code review: permissions
are requested correctly (not just declared), formats agree end-to-end with zero
transcoding, common failure modes are caught rather than crashing, timeouts and size limits
have realistic margin, and on-device audio is cleaned up promptly. Two concrete, fixable
gaps were found (audio focus, config-change robustness) and are recommended, not yet fixed,
in this pass. Voice *quality* itself remains explicitly non-production-grade (item 2,
already known). Nothing here should be read as "voice mode has been tested on a real
device" — it has not, because none was available in this environment; that verification
still needs to happen before voice mode ships to real users.
