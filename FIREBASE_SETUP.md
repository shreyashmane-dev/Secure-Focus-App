# Firebase Setup

Use one Firebase project for both the Android app and admin dashboard.

## Firebase Console

1. Enable Authentication > Sign-in method > Email/Password.
2. Create a Cloud Firestore database.
3. Add an Android app with package name `com.example.focusshield`.
4. Download the Android config file and place it at:

   ```text
   app/google-services.json
   ```

5. Add a Web app in the same Firebase project.
6. Copy the Web app config into a local `.env` file at the repository root.

   ```text
   .env
   ```

   Then run:

   ```bash
   node scripts/generate-firebase-config.js
   ```

   This generates `admin/firebase-config.js` from the `.env` values.

7. Deploy Firestore rules:

   ```bash
   firebase deploy --only firestore:rules
   ```

8. Deploy the admin panel:

   ```bash
   firebase deploy --only hosting
   ```

## Admin Account

Register or create a Firebase Auth user for the admin, then set its Firestore profile:

```text
users/{adminUid}
```

```json
{
  "uid": "adminUid",
  "name": "Admin",
  "email": "admin@example.com",
  "role": "admin",
  "createdAt": "server timestamp"
}
```

Only users with `role = "admin"` can access the admin dashboard.

## Student Test Flow

1. Open Android app.
2. Register a student account.
3. Login should persist after app restart.
4. Start Protection Mode.
5. Trigger an existing violation.
6. Confirm Firestore has:
   - `sessions/{sessionId}`
   - `logs/{logId}`
   - incremented `totalViolations`
   - incremented `riskScore`
7. Open the admin dashboard and confirm live updates appear without refreshing.
