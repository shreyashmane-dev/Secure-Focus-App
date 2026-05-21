const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const envPath = path.join(root, ".env");
const targetPath = path.join(root, "admin", "firebase-config.js");

function parseEnvFile(contents) {
  return contents.split(/\r?\n/).reduce((acc, line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) return acc;
    const [key, ...rest] = trimmed.split("=");
    acc[key] = rest.join("=");
    return acc;
  }, {});
}

const parsed = {};

if (fs.existsSync(envPath)) {
  const env = fs.readFileSync(envPath, "utf8");
  Object.assign(parsed, parseEnvFile(env));
}

const config = {
  apiKey: process.env.FIREBASE_API_KEY || parsed.FIREBASE_API_KEY,
  authDomain: process.env.FIREBASE_AUTH_DOMAIN || parsed.FIREBASE_AUTH_DOMAIN,
  projectId: process.env.FIREBASE_PROJECT_ID || parsed.FIREBASE_PROJECT_ID,
  storageBucket: process.env.FIREBASE_STORAGE_BUCKET || parsed.FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.FIREBASE_MESSAGING_SENDER_ID || parsed.FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.FIREBASE_APP_ID || parsed.FIREBASE_APP_ID,
};

const requiredKeys = [
  "apiKey",
  "authDomain",
  "projectId",
  "storageBucket",
  "messagingSenderId",
  "appId"
];

const missing = requiredKeys.filter((key) => !config[key]);
if (missing.length) {
  console.error(`ERROR: Missing Firebase values for ${missing.join(", ")} in environment variables or .env.`);
  process.exit(1);
}

if (process.env.FIREBASE_MEASUREMENT_ID || parsed.FIREBASE_MEASUREMENT_ID) {
  config.measurementId = process.env.FIREBASE_MEASUREMENT_ID || parsed.FIREBASE_MEASUREMENT_ID;
}

const output = `export const firebaseConfig = ${JSON.stringify(config, null, 2)};\n`;
fs.writeFileSync(targetPath, output, "utf8");
console.log(`Generated ${targetPath} from environment variables${fs.existsSync(envPath) ? " and .env" : ""}.`);
