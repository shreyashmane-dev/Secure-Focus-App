
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
- Improved development notes. (2026-07-11 07:39:43.066748)
