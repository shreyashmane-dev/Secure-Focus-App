const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const envPath = path.join(root, ".env");
const targetPath = path.join(root, "admin", "firebase-config.js");

if (!fs.existsSync(envPath)) {
  console.error("ERROR: .env file not found. Copy .env.example to .env and fill in Firebase config values.");
  process.exit(1);
}

const env = fs.readFileSync(envPath, "utf8");
const parsed = env.split(/\r?\n/).reduce((acc, line) => {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith("#")) return acc;
  const [key, ...rest] = trimmed.split("=");
  acc[key] = rest.join("=");
  return acc;
}, {});

const requiredKeys = [
  "FIREBASE_API_KEY",
  "FIREBASE_AUTH_DOMAIN",
  "FIREBASE_PROJECT_ID",
  "FIREBASE_STORAGE_BUCKET",
  "FIREBASE_MESSAGING_SENDER_ID",
  "FIREBASE_APP_ID"
];

const missing = requiredKeys.filter((key) => !parsed[key]);
if (missing.length) {
  console.error(`ERROR: Missing values for ${missing.join(", ")} in .env.`);
  process.exit(1);
}

const config = {
  apiKey: parsed.FIREBASE_API_KEY,
  authDomain: parsed.FIREBASE_AUTH_DOMAIN,
  projectId: parsed.FIREBASE_PROJECT_ID,
  storageBucket: parsed.FIREBASE_STORAGE_BUCKET,
  messagingSenderId: parsed.FIREBASE_MESSAGING_SENDER_ID,
  appId: parsed.FIREBASE_APP_ID,
};

if (parsed.FIREBASE_MEASUREMENT_ID) {
  config.measurementId = parsed.FIREBASE_MEASUREMENT_ID;
}

const output = `export const firebaseConfig = ${JSON.stringify(config, null, 2)};\n`;
fs.writeFileSync(targetPath, output, "utf8");
console.log(`Generated ${targetPath} from .env`);
