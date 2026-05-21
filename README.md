# Focus Shield

Focus Shield is a lightweight Android MVP built with Kotlin and Jetpack Compose. It creates a temporary focus protection session for online exams by using normal Android APIs to discourage distractions and record suspicious activity.

It is not spyware, malware, a hacking tool, or a fully cheat-proof lockdown system. It does not require root, does not permanently disable system features, and all monitoring is tied to Protection Mode.

## Features

- Start and stop temporary Protection Mode
- Live timer, violation counter, current protected app, foreground app, and session logs
- Immersive fullscreen mode with status/navigation bars hidden while active
- Portrait orientation lock while active
- Split-screen and multi-window warning detection
- Overlay permission risk checks before and during sessions
- App switching detection through an Accessibility Service
- Screenshot blocking inside Focus Shield with `FLAG_SECURE`
- Foreground monitoring service with persistent notification
- Modern dark Material 3 Compose UI
- MVVM-style state flow with a repository-backed session model

## Project Structure

```text
app/src/main/java/com/example/focusshield
├── MainActivity.kt
├── FocusShieldViewModel.kt
├── data/
│   ├── ProtectionRepository.kt
│   ├── ProtectionUiState.kt
│   └── SessionLog.kt
├── monitoring/
│   └── ImmersiveModeController.kt
├── service/
│   ├── FocusShieldAccessibilityService.kt
│   └── ProtectionMonitoringService.kt
└── ui/
    ├── FocusShieldApp.kt
    ├── screens/
    └── theme/
```

## Setup

1. Open the project in Android Studio.
2. Let Gradle sync dependencies. If Android Studio asks for a Gradle version, use the version compatible with Android Gradle Plugin `8.7.3`.
3. Add your Firebase Android config at `app/google-services.json`.
4. Enable Firebase Authentication with Email/Password and create a Cloud Firestore database.
5. Create a local `.env` file at the repository root with your Firebase Web app config, then generate `admin/firebase-config.js`:

   ```bash
   cp .env.example .env
   # fill .env with your Firebase values
   node scripts/generate-firebase-config.js
   ```

6. Deploy `firestore.rules` to the same Firebase project.
7. Run the `app` configuration on a device or emulator running Android 8.0+.
8. On the Settings screen, enable:
   - Focus Shield accessibility service
   - Notification permission on Android 13+
9. Enter the exam app package name on the Home screen, for example `com.android.chrome`.
10. Start Protection Mode.

## Vercel Deployment

1. In your Vercel project, set the root directory to the repository root and use the build command:

   ```bash
   npm run build
   ```

2. Set the output directory to:

   ```text
   admin
   ```

3. Add the Firebase environment variables in Vercel:
   - `FIREBASE_API_KEY`
   - `FIREBASE_AUTH_DOMAIN`
   - `FIREBASE_PROJECT_ID`
   - `FIREBASE_STORAGE_BUCKET`
   - `FIREBASE_MESSAGING_SENDER_ID`
   - `FIREBASE_APP_ID`
   - `FIREBASE_MEASUREMENT_ID`

4. Deploy the site. Vercel will run the build script and generate `admin/firebase-config.js` before serving the `admin` folder.

## Firebase Backend

- Android students register and log in with Firebase Email/Password authentication.
- Student profiles are stored in `users/{uid}` with `uid`, `name`, `email`, `role`, and `createdAt`.
- Protection start creates `sessions/{sessionId}` with active status and counters.
- Existing violation events are mirrored into `logs/{logId}` and increment the session risk score.
- Protection stop marks the Firestore session as `completed`.

For the admin panel, create `admin/firebase-config.js` from the root `.env` file by running `node scripts/generate-firebase-config.js`. Then serve or deploy the `admin` directory with Firebase Hosting. Admin access requires the signed-in user's `users/{uid}.role` to be `admin`.

## Run Notes

- This repository does not include a Gradle wrapper yet, so opening it in Android Studio is the intended way to run it.
- In this shell environment there is no `gradle` or `adb`, so local install/launch could not be automated here.
- For a first demo session, set the protected package to an installed browser such as `com.android.chrome`.

## Android Security Notes

Android apps cannot fully lock a student into another app, disable system navigation permanently, read private content from other apps, or guarantee that every overlay is visible. Focus Shield stays inside those limits:

- Fullscreen is restored while the app has focus.
- App switching is detected only when the user enables the accessibility service.
- Overlay checks look for apps with enabled draw-over-other-apps capability.
- The MVP declares `QUERY_ALL_PACKAGES` so local/demo builds can inspect installed packages for overlay permission risk. A Play Store release should replace this with a narrower package-visibility strategy.
- Monitoring stops when Protection Mode stops.
- The foreground service exists only to keep the temporary session visible and active.

This makes the app suitable as a college-level MVP for focus protection and behavior logging, not as a high-stakes proctoring product.
