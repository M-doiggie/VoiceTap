# VoiceTap

VoiceTap is an Android app that lets you tap anywhere on screen — in any app — just by speaking. Draw a box around a button or UI element, record a trigger phrase for it, and VoiceTap will listen in the background and simulate a tap on that spot whenever you say the phrase while that app is in the foreground.

It's built for hands-free interaction: accessibility use cases, cooking/workout scenarios where your hands are busy, or automating repetitive taps in apps that don't offer voice control themselves.

## How it works

1. A floating button (drawn via a system overlay) lets you enter "drawing mode" over any app.
2. You drag a rectangle around the UI element you want to trigger — e.g. a "Next" button, a "Like" icon, a play/pause control.
3. VoiceTap opens the microphone for a few seconds and captures a single phrase you speak (e.g. "next", "like this").
4. The region (screen rect + phrase + source app) is saved to a local database.
5. An always-on offline speech recognizer listens continuously in the background. When it hears a saved phrase while its associated app is in the foreground, an Accessibility Service dispatches a synthetic tap gesture at the center of that region.

Speech recognition runs fully offline using [Vosk](https://alphacephei.com/vosk/), so no audio is sent to a server.

## Features

- Draw arbitrary tap regions over any app via a screen overlay
- Assign a custom spoken trigger phrase to each region
- Per-app matching — the same phrase can trigger different taps in different apps
- Always-on, offline voice recognition (no network required after the model is downloaded)
- Persisted regions (Room database) that survive app restarts
- Simple management UI to review and delete saved regions

## Requirements

- Android 12 (API 31) or higher
- The following permissions/grants, all requested from the in-app setup screen:
  - **Display over other apps** — to draw the floating button and region overlay
  - **Microphone** — for always-on voice recognition
  - **Usage access** — to detect which app is currently in the foreground
  - **Accessibility service** — to dispatch the simulated tap
  - **Vosk speech model** — a small (~40 MB) offline English model, downloaded on first run

## Getting started

### Build

```
./gradlew assembleDebug
```

Open the project in Android Studio (Koala or newer recommended) and run the `app` module on a device or emulator running API 31+.

### First run

1. Launch VoiceTap and grant each permission listed on the setup screen.
2. Wait for the Vosk model to download (or manually push `vosk-model-small-en-us-0.15` into the device's `Downloads` folder to skip the download).
3. Tap **Start VoiceTap** once all permissions are green.
4. Open any app, tap the floating **+** button, and drag a box around the element you want to trigger.
5. Speak the phrase you want to associate with that region when prompted.
6. Confirm the captured phrase to save the region.

From then on, saying that phrase while the target app is in the foreground will trigger a tap on that region.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3) for the UI
- **Hilt** for dependency injection
- **Room** for persisting voice regions
- **Vosk Android** for offline speech recognition
- **AccessibilityService** for gesture injection
- Foreground services for the always-on overlay and voice listener

## Project structure

```
app/src/main/java/com/voicetap/
├── data/               # Room entities/DAO and the region repository
├── di/                 # Hilt modules
├── overlay/            # Drawable overlay view used to select tap regions
├── service/            # Overlay, voice listener, and accessibility services
├── ui/                 # Compose screens, permission helpers, Vosk model manager
├── util/               # Shared event bus and foreground-app detection
└── vm/                 # MainViewModel / UI state
```

## Known limitations

- Offline recognition works best with short, distinct phrases in quiet environments.
- If a region's on-screen position shifts (scroll, resize, orientation change), the saved tap coordinates won't follow it — regions are tied to fixed screen coordinates.
- Battery optimization must be disabled for VoiceTap to keep listening reliably in the background.
