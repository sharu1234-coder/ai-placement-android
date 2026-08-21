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

Stage 2 nav shell only — bottom-nav (Home/Roadmap/Progress/Profile), Auth root, and the full "Prepare Me" flow wired with placeholder screens. No business logic, networking, or persistence yet.
