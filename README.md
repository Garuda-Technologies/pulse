# Pulse

A no-root app freezer and live system monitor for Android.

Android does not let one app stop another. Pulse does what Greenify does without root: an accessibility service opens the target's app-info page and presses **Force stop** for you. The rest of the app is a dashboard — memory, battery, a ring gauge and a sparkline, drawn with custom views.

## How the freeze works

1. You pick an app from the list
2. `FreezeService` (an `AccessibilityService`) opens `Settings → App info → <target>`
3. It walks the accessibility tree, finds the "Force stop" button and clicks it
4. It backs out and reports success

The service is declared in `res/xml/freeze_service.xml` and listens for `typeWindowStateChanged` / `typeWindowContentChanged`.

## What is in the app

| File | Role |
|---|---|
| `FreezeService.kt` | the accessibility service that presses Force stop |
| `MainActivity.kt` | app list, RAM/battery readouts, the freeze action |
| `RingView.kt` | ring gauge for the headline number |
| `SparkView.kt` | sparkline for the recent history |

## Build

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/
```

JDK 17, Android SDK 35, minSdk 26. `local.properties` needs `sdk.dir` and is not committed.

## Setup on the phone

1. Install the APK
2. Open Pulse, tap the prompt and enable **Pulse** under Settings → Accessibility
3. Grant the usage-access / process-list permission the dashboard asks for
4. Pick an app and freeze it

## Limits

- **Accessibility services are tightly restricted on Google Play.** This app needs one for its core function, so it is a sideload/internal tool, not a store app.
- The flow depends on the Settings UI: a vendor skin that renames or moves "Force stop" breaks it.
- Apps with device-admin protection, or the app you are currently in, cannot be stopped this way.
- There is no scheduling yet — freezing is manual.
