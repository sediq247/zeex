# Zeex

Zeex is a native Android automation application built with Kotlin and Jetpack Compose. Gemini runs through Firebase AI Logic. Firebase Authentication, App Check, Cloud Functions, and Cloud Firestore provide identity, request protection, backend operations, and cloud storage. Room keeps a local cache; Android alarms deliver reminders on the device.

## Project structure

- `app/` — Android application and local Room database.
- `functions/` — authenticated Firebase callable backend.
- `firestore.rules` — blocks direct client access to Firestore; data access goes through callable functions.
- `firebase.json` — Firebase CLI and emulator configuration.
- `FIREBASE_SETUP.md` — Firebase project creation, app registration, local setup, deployment, and cost notes.

## Quick start

1. Create a Firebase project and add an Android app with package ID `com.zeex`.
2. Enable Anonymous sign-in, create Cloud Firestore, configure App Check and Firebase AI Logic with the Gemini Developer API provider.
3. Download `google-services.json` into `app/google-services.json`.
4. Install Node.js 22 and the Firebase CLI, then from this directory run `firebase login` and `firebase use --add`.
5. From the project root run `firebase deploy --only firestore:rules,functions`.
6. Open this directory in Android Studio, sync Gradle, and run the `app` configuration.

The Android Gradle configuration can sync without `google-services.json`, but Firebase-backed runtime features need the file and a configured Firebase project. Do not commit project credentials.

## Firebase backend

The Functions API stores records beneath `users/{uid}/chat`, `users/{uid}/missions`, and `users/{uid}/reminders`. Each callable requires Firebase Authentication and App Check, validates inputs, and scopes operations to the authenticated UID. Firestore client rules deny all direct client reads and writes. The Admin SDK in Cloud Functions performs the database operations.

Gemini is accessed by the Android app through Firebase AI Logic using the Gemini Developer API provider. No Gemini key, MongoDB URI, service account key, or public REST API URL is embedded in the app.

## Local behavior and limits

- Chat, missions, and reminders are cached on-device using Room.
- Reminders are persisted in Firestore and scheduled with Android alarms. Exact timing depends on Android's exact-alarm permission and notification permission; the app falls back to an inexact alarm when needed.
- Anonymous accounts are device-bound. Add a permanent sign-in/linking flow before promising account recovery or reliable cross-device migration.
- Missions are persistent goals and status tracking; they do not run unattended long-running work in the background.
- Device status uses only Android APIs available to this app, such as battery level and Zeex app storage. It does not claim to measure temperature, private app data, or total device storage.

See [FIREBASE_SETUP.md](FIREBASE_SETUP.md) for detailed setup and deployment steps. Cloud Functions requires Firebase's Blaze plan; usage may incur charges.
