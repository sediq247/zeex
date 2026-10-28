# Firebase setup and deployment

Zeex uses Firebase Authentication for identity, App Check for app attestation, Cloud Functions for the private backend, Cloud Firestore for persistent data, and Firebase AI Logic for Gemini. Railway and MongoDB are not part of this deployment.

## 1. Create and configure Firebase

1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app with application ID `com.zeex`.
3. Enable **Authentication → Sign-in method → Anonymous**.
4. Create the **Cloud Firestore** database. Choose a database location near your users; Functions are currently configured for `us-central1`.
5. In **App Check**, register the Android app. Debug builds use the Debug provider; register its debug token in the Firebase Console. Release builds use Play Integrity and need the relevant Play Console setup.
6. In **AI Logic**, enable the Gemini Developer API provider. The app calls the `gemini-3.8-flash` model through Firebase AI Logic; no Gemini key is added to this repository.
7. Upgrade to the **Blaze** plan to deploy Cloud Functions. Configure a Google Cloud budget alert.
8. Download `google-services.json` and place it at `app/google-services.json`. The file is ignored by Git.

## 2. Install and link Firebase CLI

Install Node.js 22 and Firebase CLI, then open PowerShell in the project directory:

```powershell
npm install -g firebase-tools
firebase login
firebase use --add
```

Select your Firebase project. This creates a local `.firebaserc` file, which is ignored by Git.

## 3. Deploy Firestore rules and Functions

From the project root:

```powershell
Set-Location functions
npm install
Set-Location ..
firebase deploy --only firestore:rules,functions
```

The database collections are scoped by the signed-in user:

- `users/{uid}/chat/{messageId}`
- `users/{uid}/missions/{missionId}`
- `users/{uid}/reminders/{reminderId}`

Firestore rules deny client-side database access. Callable Functions verify Firebase Auth and enforce App Check before using the Admin SDK.

## 4. Build and run Android

Open `C:\Users\Hp\Zeex` in Android Studio, let Gradle sync, and run the `app` configuration. The debug App Check token appears in Android Studio Logcat on first launch; register it in Firebase Console. Ensure the Firebase Android app and Functions deployment use the same Firebase project.

## Local emulators

From the project root, use `firebase emulators:start --only functions,firestore`. To route Android emulator calls to the local emulators, configure the Firebase Functions SDK to use host `10.0.2.2` on port `5001` and Firestore emulator port `8080` in a debug-only configuration. Do not use emulator endpoints in release builds.

## Costs

Cloud Functions requires the Blaze plan. Firestore reads/writes, Gemini requests, function invocations, and network use can incur charges. Configure billing alerts and review usage. App Check helps restrict backend access; it is not a spend cap.
