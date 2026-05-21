import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut,
  setPersistence,
  browserLocalPersistence
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  collection,
  doc,
  setDoc,
  getDoc,
  getDocs,
  getFirestore,
  limit,
  onSnapshot,
  orderBy,
  query,
  where
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// DOM Elements - Auth & Views
const loginView = document.querySelector("#loginView");
const dashboardView = document.querySelector("#dashboardView");
const loginForm = document.querySelector("#loginForm");
const loginButton = document.querySelector("#loginButton");
const logoutButton = document.querySelector("#logoutButton");
const loginError = document.querySelector("#loginError");
const adminName = document.querySelector("#adminName");
const adminIdentity = document.querySelector("#adminIdentity");
const connectionStatus = document.querySelector("#connectionStatus");

// DOM Elements - Tab Switching
const btnNavMonitoring = document.querySelector("#btnNavMonitoring");
const btnNavUsers = document.querySelector("#btnNavUsers");
const monitoringSection = document.querySelector("#monitoringSection");
const usersSection = document.querySelector("#usersSection");

// DOM Elements - Monitoring Tab
const searchInput = document.querySelector("#searchInput");
const statusFilter = document.querySelector("#statusFilter");
const riskFilter = document.querySelector("#riskFilter");
const sortMode = document.querySelector("#sortMode");
const exportCsvButton = document.querySelector("#exportCsvButton");
const sessionsList = document.querySelector("#sessionsList");
const sessionCaption = document.querySelector("#sessionCaption");
const sessionCount = document.querySelector("#sessionCount");
const activeCount = document.querySelector("#activeCount");
const completedCount = document.querySelector("#completedCount");
const violationCount = document.querySelector("#violationCount");
const highestRisk = document.querySelector("#highestRisk");
const sessionDetails = document.querySelector("#sessionDetails");
const recentAlertsList = document.querySelector("#recentAlertsList");
const watchList = document.querySelector("#watchList");
const alertsContainer = document.querySelector("#toastAlerts");

// DOM Elements - Users Tab
const userSearchInput = document.querySelector("#userSearchInput");
const userRoleFilter = document.querySelector("#userRoleFilter");
const usersList = document.querySelector("#usersList");
const userCount = document.querySelector("#userCount");
const userCountCaption = document.querySelector("#userCountCaption");
const userDetails = document.querySelector("#userDetails");

// DOM Elements - Floating Corner Tracker
const cornerTracker = document.querySelector("#cornerTracker");
const minimizeTracker = document.querySelector("#minimizeTracker");
const cornerSessionCount = document.querySelector("#cornerSessionCount");
const trackerContent = document.querySelector("#trackerContent");

// State Variables
let sessions = [];
let users = [];
let recentLogs = [];
let selectedSessionId = null;
let selectedSessionLogs = [];
let selectedUserId = null;
let selectedUserSessions = [];

// Realtime unsubscribers
let unsubscribeSessions = null;
let unsubscribeUsers = null;
let unsubscribeRecentLogs = null;
let unsubscribeSessionDetails = null;

let initialLogsLoaded = false;
let timersInterval = null;
let trackerMinimized = false;

/* ==========================================
   1. AUTHENTICATION & LOGIN FLOW
   ========================================== */

let isRegisterMode = false;
const toggleAuthMode = document.querySelector("#toggleAuthMode");
if (toggleAuthMode) {
  toggleAuthMode.addEventListener("click", (e) => {
    e.preventDefault();
    isRegisterMode = !isRegisterMode;
    loginButton.querySelector("span").textContent = isRegisterMode ? "Create Account" : "Login Console";
    toggleAuthMode.textContent = isRegisterMode ? "Log in to existing account instead" : "Create Account";
  });
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  loginError.textContent = "";
  loginButton.disabled = true;
  const loginText = loginButton.querySelector("span");
  const originalText = loginText.textContent;
  loginText.textContent = isRegisterMode ? "Creating..." : "Verifying...";

  try {
    const email = document.querySelector("#email").value.trim();
    const password = document.querySelector("#password").value;
    
    await setPersistence(auth, browserLocalPersistence);

    if (isRegisterMode) {
      const userCredential = await createUserWithEmailAndPassword(auth, email, password);
      // Wait for auth state listener to pick this up, but first write the user doc
      await setDoc(doc(db, "users", userCredential.user.uid), {
        uid: userCredential.user.uid,
        name: email.split('@')[0],
        email: email,
        role: "admin",
        createdAt: new Date().toISOString()
      });
    } else {
      await signInWithEmailAndPassword(auth, email, password);
    }
  } catch (error) {
    loginError.textContent = friendlyAuthError(error);
    loginButton.disabled = false;
    loginText.textContent = originalText;
  }
});

logoutButton.addEventListener("click", () => signOut(auth));

onAuthStateChanged(auth, async (user) => {
  stopRealtime();
  if (!user) {
    showLogin();
    return;
  }

  try {
    // Check if user exists in firestore and has admin role
    const profile = await getDoc(doc(db, "users", user.uid));
    if (!profile.exists() || profile.data().role !== "admin") {
      await signOut(auth);
      loginError.textContent = "Access Denied: This account is not registered as an administrator.";
      showLogin();
      return;
    }

    adminName.textContent = profile.data().name || "Administrator";
    adminIdentity.textContent = user.email || "";
    showDashboard();
    startRealtime();
  } catch (error) {
    await signOut(auth);
    loginError.textContent = `Authorization failed: ${error.message}`;
    showLogin();
  }
});

function showLogin() {
  loginView.classList.remove("hidden");
  dashboardView.classList.add("hidden");
  cornerTracker.classList.add("hidden");
  setConnection("Signed out", true);
}

function showDashboard() {
  loginView.classList.add("hidden");
  dashboardView.classList.remove("hidden");
  setConnection("Connecting", false);
}

/* ==========================================
   2. SIDEBAR TAB NAVIGATION
   ========================================== */

btnNavMonitoring.addEventListener("click", () => {
  btnNavMonitoring.classList.add("active");
  btnNavUsers.classList.remove("active");
  monitoringSection.classList.add("active-workspace");
  usersSection.classList.remove("active-workspace");
});

btnNavUsers.addEventListener("click", () => {
  btnNavUsers.classList.add("active");
  btnNavMonitoring.classList.remove("active");
  usersSection.classList.add("active-workspace");
  monitoringSection.classList.remove("active-workspace");
});

// Floating corner widget minimize toggle
minimizeTracker.addEventListener("click", () => {
  trackerMinimized = !trackerMinimized;
  if (trackerMinimized) {
    cornerTracker.classList.add("minimized");
  } else {
    cornerTracker.classList.remove("minimized");
  }
});

/* ==========================================
   3. REALTIME SYNC ENGINE (FIRESTORE)
   ========================================== */

function startRealtime() {
  // Listen to Users Database
  unsubscribeUsers = onSnapshot(
    collection(db, "users"),
    (snapshot) => {
      users = snapshot.docs.map((doc) => ({ uid: doc.id, ...doc.data() }));
      renderUsers();
      renderSelectedUser();
      updateCornerTracker();
    },
    (error) => setConnection(`Users Sync: ${error.message}`, true)
  );

  // Listen to Exam Sessions
  unsubscribeSessions = onSnapshot(
    query(collection(db, "sessions"), orderBy("lastActivity", "desc")),
    (snapshot) => {
      sessions = snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      setConnection("Live", false);
      renderSummaryStats();
      renderSessions();
      renderSelectedSession();
      updateCornerTracker();
      
      // If we are currently inspecting a user, reload their sessions
      if (selectedUserId) {
        loadUserSessions(selectedUserId);
      }
    },
    (error) => setConnection(`Sessions Sync: ${error.message}`, true)
  );

  // Listen to Recent Activity Logs
  unsubscribeRecentLogs = onSnapshot(
    query(collection(db, "logs"), orderBy("timestamp", "desc"), limit(50)),
    (snapshot) => {
      recentLogs = snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      if (initialLogsLoaded) {
        snapshot.docChanges().forEach((change) => {
          if (change.type === "added") {
            showToastAlert(change.doc.data());
          }
        });
      }
      initialLogsLoaded = true;
      renderGlobalAlertsFeed();
      renderSelectedSession();
    },
    (error) => setConnection(`Logs Sync: ${error.message}`, true)
  );

  // Run dynamic clock tick timers for elapsed test times
  timersInterval = setInterval(updateAllTimers, 1000);
}

function stopRealtime() {
  if (unsubscribeSessions) unsubscribeSessions();
  if (unsubscribeUsers) unsubscribeUsers();
  if (unsubscribeRecentLogs) unsubscribeRecentLogs();
  if (unsubscribeSessionDetails) unsubscribeSessionDetails();
  
  unsubscribeSessions = null;
  unsubscribeUsers = null;
  unsubscribeRecentLogs = null;
  unsubscribeSessionDetails = null;
  
  if (timersInterval) clearInterval(timersInterval);
  timersInterval = null;

  initialLogsLoaded = false;
  sessions = [];
  users = [];
  recentLogs = [];
  selectedSessionLogs = [];
  selectedSessionId = null;
  selectedUserId = null;
  selectedUserSessions = [];
}

function setConnection(text, isError) {
  connectionStatus.textContent = text;
  connectionStatus.classList.toggle("error", Boolean(isError));
}

/* ==========================================
   4. RENDER MONITORING TAB (LIVE METRICS & SESSIONS)
   ========================================== */

function renderSummaryStats() {
  const active = sessions.filter((s) => s.status === "active").length;
  const completed = sessions.filter((s) => s.status === "completed").length;
  const totalViolations = sessions.reduce((sum, s) => sum + Number(s.totalViolations || 0), 0);
  const maxRisk = sessions.reduce((max, s) => Math.max(max, Number(s.riskScore || 0)), 0);

  activeCount.textContent = active.toString();
  completedCount.textContent = completed.toString();
  violationCount.textContent = totalViolations.toString();
  highestRisk.textContent = maxRisk.toString();
}

function renderSessions() {
  const queryText = searchInput.value.trim().toLowerCase();
  const status = statusFilter.value;
  const risk = riskFilter.value;
  const sort = sortMode.value;

  const filtered = sessions
    .filter((s) => status === "all" || s.status === status)
    .filter((s) => matchesRiskFilter(s, risk))
    .filter((s) => {
      const student = (s.studentName || "").toLowerCase();
      const test = (s.testName || "").toLowerCase();
      return student.includes(queryText) || test.includes(queryText);
    })
    .sort((a, b) => compareSessions(a, b, sort));

  sessionCount.textContent = filtered.length.toString();
  sessionCaption.textContent = sessions.length
    ? `${filtered.length} of ${sessions.length} sessions listed`
    : "No sessions logged in database";

  if (!filtered.length) {
    sessionsList.innerHTML = `
      <div class="empty-state">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="muted-icon"><circle cx="12" cy="12" r="10"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
        <h3>No matching sessions</h3>
        <p>Try resetting filters or search query.</p>
      </div>
    `;
    return;
  }

  sessionsList.replaceChildren(...filtered.map(createSessionRow));
}

function createSessionRow(session) {
  const row = document.createElement("button");
  row.className = `session-row ${session.sessionId === selectedSessionId ? "selected" : ""}`;
  row.type = "button";
  row.addEventListener("click", () => selectSession(session.sessionId));

  const riskVal = Number(session.riskScore || 0);
  const riskBand = getRiskBand(riskVal);
  const maxRiskWidth = Math.min(100, riskVal);
  const isCompleted = session.status === "completed";

  // Real-time ticking indicator setup
  let timerContent = "";
  if (session.status === "active") {
    const started = session.startedAtMillis || timestampToMillis(session.startedAt);
    timerContent = `<span class="meta-item live-timer" data-start-time="${started}">00:00</span>`;
  } else {
    timerContent = `<span class="meta-item">${formatDuration(session)}</span>`;
  }

  row.innerHTML = `
    <div class="session-info">
      <strong>${escapeHtml(session.studentName || "Anonymous Student")}</strong>
      <div class="session-meta-row">
        <span class="meta-item text-primary">${escapeHtml(session.testName || "Online Exam")}</span>
        <span>•</span>
        ${timerContent}
      </div>
      <div class="session-meta-row" style="margin-top: 2px;">
        <span class="meta-item text-warning">${Number(session.totalViolations || 0)} violations</span>
        <span>•</span>
        <span class="meta-item">Risk: ${riskVal}</span>
      </div>
      <div class="risk-bar ${riskBand}">
        <span style="width: ${maxRiskWidth}%"></span>
      </div>
    </div>
    <span class="status-badge ${isCompleted ? "completed" : "active-indicator"}">
      ${isCompleted ? "completed" : "active"}
    </span>
  `;
  return row;
}

function selectSession(sessionId) {
  selectedSessionId = sessionId;
  
  if (unsubscribeSessionDetails) unsubscribeSessionDetails();
  unsubscribeSessionDetails = onSnapshot(
    query(collection(db, "logs"), where("sessionId", "==", sessionId)),
    (snapshot) => {
      selectedSessionLogs = snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
      renderSessions();
      renderSelectedSession();
    },
    (error) => setConnection(`Session Logs: ${error.message}`, true)
  );
  
  renderSessions();
  renderSelectedSession();
}

function renderSelectedSession() {
  if (!selectedSessionId) {
    sessionDetails.innerHTML = `
      <div class="empty-state">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="muted-icon"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/></svg>
        <h3>Select a student session</h3>
        <p>Real-time violation logs and status history will display here upon selection.</p>
      </div>
    `;
    return;
  }

  const session = sessions.find((s) => s.sessionId === selectedSessionId);
  if (!session) {
    sessionDetails.innerHTML = `
      <div class="empty-state">
        <h3>Session not found</h3>
        <p>This session may have been deleted or archived.</p>
      </div>
    `;
    return;
  }

  const isCompleted = session.status === "completed";
  const started = session.startedAtMillis || timestampToMillis(session.startedAt);
  const completed = timestampToMillis(session.completedAt);
  
  let timerBlock = "";
  if (session.status === "active") {
    timerBlock = `<strong class="live-timer" data-start-time="${started}">00:00</strong>`;
  } else {
    timerBlock = `<strong>${formatDuration(session)}</strong>`;
  }

  const riskVal = Number(session.riskScore || 0);
  const riskBand = getRiskBand(riskVal);
  const sortedLogs = [...selectedSessionLogs].sort((a, b) => timestampToMillis(b.timestamp) - timestampToMillis(a.timestamp));

  sessionDetails.innerHTML = `
    <div class="detail-header-block">
      <div>
        <h3>${escapeHtml(session.studentName || "Anonymous Student")}</h3>
        <p>${escapeHtml(session.testName || "Online Exam")}</p>
      </div>
      <span class="status-badge ${isCompleted ? "completed" : "active-indicator"}">
        ${isCompleted ? "completed" : "active"}
      </span>
    </div>

    <div class="metrics-summary-grid">
      <div class="metric-item">
        <span>Test Duration</span>
        ${timerBlock}
      </div>
      <div class="metric-item alert-level ${riskBand}">
        <span>Risk Score</span>
        <strong>${riskVal}</strong>
      </div>
      <div class="metric-item">
        <span>Total Violations</span>
        <strong>${Number(session.totalViolations || 0)}</strong>
      </div>
    </div>

    <div class="data-table-list">
      <div class="data-row">
        <span class="label">Student ID</span>
        <span class="value code">${escapeHtml(session.studentId || "-")}</span>
      </div>
      <div class="data-row">
        <span class="label">Session UID</span>
        <span class="value code">${escapeHtml(session.sessionId || "-")}</span>
      </div>
      <div class="data-row">
        <span class="label">Started At</span>
        <span class="value">${formatTimestamp(started)}</span>
      </div>
      <div class="data-row">
        <span class="label">Completed At</span>
        <span class="value">${isCompleted ? formatTimestamp(completed) : "Still active"}</span>
      </div>
      <div class="data-row">
        <span class="label">Last Activity</span>
        <span class="value">${formatTimestamp(session.lastActivity)}</span>
      </div>
    </div>

    <h3 class="section-title">Violation Audit Log</h3>
    <div class="timeline">
      ${sortedLogs.map(createTimelineEventHTML).join("") || `<p class="muted" style="text-align: center; padding: 20px;">No violations recorded during this session.</p>`}
    </div>
  `;
}

function createTimelineEventHTML(log) {
  const severity = (log.severity || "low").toLowerCase();
  const time = formatTimestamp(log.timestamp);
  
  return `
    <div class="timeline-event ${severity}">
      <div class="event-box">
        <div class="event-header">
          <strong>${escapeHtml(humanizeViolation(log.violationType))}</strong>
          <span>${time}</span>
        </div>
        <p>${escapeHtml(log.details || "No comments entered by device system.")}</p>
        <div class="event-meta">
          <span style="text-transform: uppercase;">Severity: ${severity}</span>
          <span>•</span>
          <span>Code: ${escapeHtml(log.violationType || "GENERIC")}</span>
        </div>
      </div>
    </div>
  `;
}

/* ==========================================
   5. ALL-USERS TAB (USER DATABASE & PAST HISTORY)
   ========================================== */

function renderUsers() {
  const search = userSearchInput.value.trim().toLowerCase();
  const role = userRoleFilter.value;

  const filtered = users
    .filter((u) => role === "all" || u.role === role)
    .filter((u) => {
      const name = (u.name || "").toLowerCase();
      const email = (u.email || "").toLowerCase();
      return name.includes(search) || email.includes(search);
    })
    .sort((a, b) => timestampToMillis(b.createdAt) - timestampToMillis(a.createdAt));

  userCount.textContent = filtered.length.toString();
  userCountCaption.textContent = users.length
    ? `${filtered.length} of ${users.length} accounts found`
    : "No users registered in database";

  if (!filtered.length) {
    usersList.innerHTML = `
      <div class="empty-state">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="muted-icon"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>
        <h3>No matching users</h3>
        <p>No user accounts fit your filter settings.</p>
      </div>
    `;
    return;
  }

  usersList.replaceChildren(...filtered.map(createUserRow));
}

function createUserRow(user) {
  const row = document.createElement("button");
  row.className = `user-row ${user.uid === selectedUserId ? "selected" : ""}`;
  row.type = "button";
  row.addEventListener("click", () => selectUser(user.uid));

  // Determine if this user currently has an active test session
  const activeSession = sessions.find((s) => s.studentId === user.uid && s.status === "active");
  const isOnline = !!activeSession;

  row.innerHTML = `
    <div class="user-info">
      <strong>${escapeHtml(user.name || "Unnamed Account")}</strong>
      <div class="session-meta-row">
        <span>${escapeHtml(user.email || "")}</span>
      </div>
      <div class="session-meta-row" style="margin-top: 4px;">
        <span class="meta-item" style="text-transform: capitalize;">Role: ${escapeHtml(user.role || "student")}</span>
        <span>•</span>
        <span class="meta-item">Joined: ${formatTimestampDate(user.createdAt)}</span>
      </div>
    </div>
    <span class="status-badge ${isOnline ? "active-indicator" : "idle"}">
      ${isOnline ? "active test" : "idle"}
    </span>
  `;
  return row;
}

async function selectUser(uid) {
  selectedUserId = uid;
  renderUsers();
  renderSelectedUser();
  await loadUserSessions(uid);
}

async function loadUserSessions(uid) {
  // Query all past sessions for this specific user
  try {
    const q = query(collection(db, "sessions"), where("studentId", "==", uid));
    const snapshot = await getDocs(q);
    selectedUserSessions = snapshot.docs
      .map((doc) => ({ id: doc.id, ...doc.data() }))
      .sort((a, b) => timestampToMillis(b.startedAt || b.startedAtMillis) - timestampToMillis(a.startedAt || a.startedAtMillis));
    
    // Refresh display
    renderUserSessionsHistory();
  } catch (error) {
    console.error("Error loading user exam history:", error);
  }
}

function renderSelectedUser() {
  if (!selectedUserId) {
    userDetails.innerHTML = `
      <div class="empty-state">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="muted-icon"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
        <h3>Select a User Profile</h3>
        <p>Select a user to review their registration credentials, active device status, and complete test history logs.</p>
      </div>
    `;
    return;
  }

  const user = users.find((u) => u.uid === selectedUserId);
  if (!user) {
    userDetails.innerHTML = `
      <div class="empty-state">
        <h3>User profile not found</h3>
        <p>This user account may have been deleted.</p>
      </div>
    `;
    return;
  }

  // Cross reference for current active test
  const activeSession = sessions.find((s) => s.studentId === user.uid && s.status === "active");
  const isOnline = !!activeSession;

  userDetails.innerHTML = `
    <div class="detail-header-block">
      <div>
        <h3>${escapeHtml(user.name || "Unnamed Account")}</h3>
        <p>${escapeHtml(user.email || "")}</p>
      </div>
      <span class="status-badge ${isOnline ? "active-indicator" : "idle"}">
        ${isOnline ? "active test" : "idle"}
      </span>
    </div>

    <div class="data-table-list" style="margin-bottom: 24px;">
      <div class="data-row">
        <span class="label">Database User UID</span>
        <span class="value code">${escapeHtml(user.uid || "-")}</span>
      </div>
      <div class="data-row">
        <span class="label">System Role</span>
        <span class="value" style="text-transform: uppercase; font-weight: bold; color: var(--accent);">${escapeHtml(user.role || "student")}</span>
      </div>
      <div class="data-row">
        <span class="label">Account Created</span>
        <span class="value">${formatTimestamp(user.createdAt)}</span>
      </div>
    </div>

    <h3 class="section-title">Perfect Exam History</h3>
    <div id="userHistoryContainer" class="history-section">
      <p class="muted" style="text-align: center; padding: 20px;">Loading student exam history...</p>
    </div>
  `;

  // Render the session cards
  renderUserSessionsHistory();
}

function renderUserSessionsHistory() {
  const container = document.querySelector("#userHistoryContainer");
  if (!container) return;

  if (!selectedUserSessions.length) {
    container.innerHTML = `<p class="muted" style="text-align: center; padding: 20px;">No exams recorded in the database for this student.</p>`;
    return;
  }

  container.innerHTML = "";
  selectedUserSessions.forEach((s) => {
    const card = document.createElement("div");
    card.className = "history-card";
    card.id = `history-card-${s.sessionId}`;

    const started = s.startedAtMillis || timestampToMillis(s.startedAt);
    const violations = Number(s.totalViolations || 0);
    const risk = Number(s.riskScore || 0);
    const severityClass = violations > 5 ? "high-severity" : "";
    const isCompleted = s.status === "completed";

    let timerText = "";
    if (s.status === "active") {
      timerText = `<span class="live-timer text-success" data-start-time="${started}">00:00</span>`;
    } else {
      timerText = formatDuration(s);
    }

    card.innerHTML = `
      <div class="history-summary" onclick="this.parentElement.classList.toggle('expanded'); toggleHistoryDetails('${s.sessionId}')">
        <div class="history-details-left">
          <strong>${escapeHtml(s.testName || "Online Exam")}</strong>
          <span>${formatTimestampDate(started)} (${s.status})</span>
        </div>
        <div class="history-stats-right">
          <span class="history-badge">Time: ${timerText}</span>
          <span class="history-badge violations-badge ${severityClass}">${violations} violations</span>
          <span class="history-badge risk-badge">Risk ${risk}</span>
          <svg class="chevron-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>
        </div>
      </div>
      <div class="history-card-details" id="history-details-content-${s.sessionId}">
        <p class="muted" style="text-align: center; padding: 10px;">Retrieving violation timeline logs...</p>
      </div>
    `;

    container.appendChild(card);
  });
}

// Window scope registry so onclick handler is resolved in HTML snippet
window.toggleHistoryDetails = async function(sessionId) {
  const cardDetails = document.querySelector(`#history-details-content-${sessionId}`);
  if (!cardDetails) return;

  // If already loaded and displaying, do nothing
  if (cardDetails.dataset.loaded === "true") return;

  try {
    const q = query(collection(db, "logs"), where("sessionId", "==", sessionId));
    const snapshot = await getDocs(q);
    const logs = snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
    const sortedLogs = logs.sort((a, b) => timestampToMillis(b.timestamp) - timestampToMillis(a.timestamp));

    if (!sortedLogs.length) {
      cardDetails.innerHTML = `<p class="muted" style="text-align: center; padding: 10px 0;">No violations recorded during this exam session.</p>`;
    } else {
      cardDetails.innerHTML = `
        <div class="timeline" style="margin-top: 8px;">
          ${sortedLogs.map((log) => {
            const severity = (log.severity || "low").toLowerCase();
            return `
              <div class="timeline-event ${severity}">
                <div class="event-box" style="padding: 10px 14px;">
                  <div class="event-header" style="margin-bottom: 4px;">
                    <strong style="font-size: 13px;">${escapeHtml(humanizeViolation(log.violationType))}</strong>
                    <span style="font-size: 10px;">${formatTimestamp(log.timestamp)}</span>
                  </div>
                  <p style="font-size: 12px; margin-bottom: 2px;">${escapeHtml(log.details || "No comments.")}</p>
                  <div class="event-meta" style="font-size: 10px;">
                    <span style="text-transform: uppercase;">Severity: ${severity}</span>
                  </div>
                </div>
              </div>
            `;
          }).join("")}
        </div>
      `;
    }
    cardDetails.dataset.loaded = "true";
  } catch (error) {
    cardDetails.innerHTML = `<p class="error" style="text-align: center; padding: 10px 0;">Failed to load logs: ${error.message}</p>`;
  }
};

/* ==========================================
   6. FLOATING CORNER LIVE MONITOR
   ========================================== */

function updateCornerTracker() {
  const activeSessions = sessions.filter((s) => s.status === "active");
  
  if (activeSessions.length === 0) {
    cornerTracker.classList.add("hidden");
    return;
  }
  
  cornerTracker.classList.remove("hidden");
  cornerSessionCount.textContent = `${activeSessions.length} active`;

  if (trackerMinimized) {
    return;
  }

  trackerContent.innerHTML = "";
  activeSessions.forEach((s) => {
    const row = document.createElement("div");
    row.className = "tracker-row";
    row.addEventListener("click", () => {
      // Switch tab to Monitoring and select session
      btnNavMonitoring.click();
      selectSession(s.sessionId);
    });

    const started = s.startedAtMillis || timestampToMillis(s.startedAt);
    const violations = Number(s.totalViolations || 0);

    row.innerHTML = `
      <div class="tracker-row-top">
        <strong title="${escapeHtml(s.studentName)}">${escapeHtml(s.studentName || "Student")}</strong>
        <span class="tracker-time live-timer" data-start-time="${started}">00:00</span>
      </div>
      <div class="tracker-row-bottom">
        <span class="truncate" style="max-width: 140px;" title="${escapeHtml(s.testName)}">${escapeHtml(s.testName || "Exam")}</span>
        <span class="tracker-violations ${violations > 0 ? "has-violations" : ""}">
          ${violations} violation${violations === 1 ? "" : "s"}
        </span>
      </div>
    `;
    trackerContent.appendChild(row);
  });
}

/* ==========================================
   7. LIVE COUNTERS (TIME TICK ENGINE)
   ========================================== */

function updateAllTimers() {
  const timerElements = document.querySelectorAll(".live-timer");
  timerElements.forEach((el) => {
    const startTimeStr = el.getAttribute("data-start-time");
    if (!startTimeStr) return;

    const startTime = parseInt(startTimeStr, 10);
    if (isNaN(startTime) || startTime <= 0) return;

    const elapsedMs = Date.now() - startTime;
    el.textContent = formatDurationFormatted(elapsedMs);
  });
}

function formatDurationFormatted(ms) {
  if (ms < 0) ms = 0;
  const totalSecs = Math.floor(ms / 1000);
  const hrs = Math.floor(totalSecs / 3600);
  const mins = Math.floor((totalSecs % 3600) / 60);
  const secs = totalSecs % 60;

  const hrStr = hrs > 0 ? String(hrs).padStart(2, "0") + ":" : "";
  const minStr = String(mins).padStart(2, "0") + ":";
  const secStr = String(secs).padStart(2, "0");

  return hrStr + minStr + secStr;
}

/* ==========================================
   8. CSV EXPORT UTILITY
   ========================================== */

function exportVisibleSessions() {
  const queryText = searchInput.value.trim().toLowerCase();
  const status = statusFilter.value;
  const risk = riskFilter.value;
  const sort = sortMode.value;

  const filtered = sessions
    .filter((s) => status === "all" || s.status === status)
    .filter((s) => matchesRiskFilter(s, risk))
    .filter((s) => {
      const student = (s.studentName || "").toLowerCase();
      const test = (s.testName || "").toLowerCase();
      return student.includes(queryText) || test.includes(queryText);
    })
    .sort((a, b) => compareSessions(a, b, sort));

  if (!filtered.length) {
    alert("No session data available to export.");
    return;
  }

  let csvContent = "data:text/csv;charset=utf-8,";
  csvContent += "Student Name,Test Name,Status,Total Violations,Risk Score,Started At,Duration (secs)\n";

  filtered.forEach((s) => {
    const name = `"${(s.studentName || "Anonymous").replace(/"/g, '""')}"`;
    const test = `"${(s.testName || "Exam").replace(/"/g, '""')}"`;
    const started = s.startedAtMillis || timestampToMillis(s.startedAt);
    const duration = s.status === "completed" 
      ? Math.floor((timestampToMillis(s.completedAt) - started) / 1000)
      : Math.floor((Date.now() - started) / 1000);

    csvContent += `${name},${test},${s.status},${s.totalViolations || 0},${s.riskScore || 0},"${new Date(started).toISOString()}",${duration}\n`;
  });

  const encodedUri = encodeURI(csvContent);
  const link = document.createElement("a");
  link.setAttribute("href", encodedUri);
  link.setAttribute("download", `focus_shield_audit_${new Date().toISOString().split('T')[0]}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

/* ==========================================
   9. SIDEBAR ALERTS FEED & WATCHLISTS
   ========================================== */

function renderGlobalAlertsFeed() {
  const sorted = [...recentLogs]
    .sort((a, b) => timestampToMillis(b.timestamp) - timestampToMillis(a.timestamp))
    .slice(0, 10);

  recentAlertsList.innerHTML = "";
  if (!sorted.length) {
    recentAlertsList.innerHTML = `<p class="muted" style="text-align:center; padding: 10px; font-size:12px;">No alerts captured.</p>`;
    return;
  }

  sorted.forEach((log) => {
    const row = document.createElement("div");
    row.className = "compact-row";
    row.innerHTML = `
      <div>
        <strong style="color: var(--danger); font-size:12px;">${escapeHtml(log.studentName || "Student")}</strong>
        <p style="font-size:11px; margin-top:2px;">${escapeHtml(humanizeViolation(log.violationType))}</p>
      </div>
      <span class="muted" style="font-size:10px;">${formatTimestampTime(log.timestamp)}</span>
    `;
    recentAlertsList.appendChild(row);
  });

  // Re-generate watchlist: sorting students by high risk scores
  const watchlistUsers = sessions
    .filter((s) => s.status === "active" && Number(s.riskScore || 0) > 0)
    .sort((a, b) => Number(b.riskScore) - Number(a.riskScore))
    .slice(0, 5);

  watchList.innerHTML = "";
  if (!watchlistUsers.length) {
    watchList.innerHTML = `<p class="muted" style="text-align:center; padding: 10px; font-size:12px;">No active risk threats.</p>`;
    return;
  }

  watchlistUsers.forEach((s) => {
    const row = document.createElement("div");
    row.className = "compact-row";
    row.style.cursor = "pointer";
    row.addEventListener("click", () => {
      selectSession(s.sessionId);
    });

    row.innerHTML = `
      <div>
        <strong>${escapeHtml(s.studentName || "Student")}</strong>
        <p class="muted" style="font-size:11px;">${escapeHtml(s.testName)}</p>
      </div>
      <span style="font-weight:bold; color: var(--danger);">Risk: ${s.riskScore}</span>
    `;
    watchList.appendChild(row);
  });
}

function showToastAlert(log) {
  const alert = document.createElement("article");
  alert.className = "alert";
  alert.innerHTML = `
    <strong>${escapeHtml(log.studentName || "Student")} - Violation!</strong>
    <p>${escapeHtml(humanizeViolation(log.violationType))}</p>
    <p class="muted">${escapeHtml(log.details || "")}</p>
    <span class="muted" style="font-size:10px; display:block; margin-top:6px;">${formatTimestampTime(log.timestamp)}</span>
  `;
  alertsContainer.prepend(alert);
  
  // Slide out and remove toast after 7s
  setTimeout(() => {
    alert.style.animation = "slide-in 0.3s cubic-bezier(0.16, 1, 0.3, 1) reverse forwards";
    setTimeout(() => alert.remove(), 300);
  }, 7000);
}

/* ==========================================
   10. INTERACTION HELPERS & UTILITIES
   ========================================== */

searchInput.addEventListener("input", renderSessions);
statusFilter.addEventListener("change", renderSessions);
riskFilter.addEventListener("change", renderSessions);
sortMode.addEventListener("change", renderSessions);
exportCsvButton.addEventListener("click", exportVisibleSessions);

userSearchInput.addEventListener("input", renderUsers);
userRoleFilter.addEventListener("change", renderUsers);

function matchesRiskFilter(session, filter) {
  const score = Number(session.riskScore || 0);
  if (filter === "all") return true;
  if (filter === "high") return score >= 60;
  if (filter === "medium") return score >= 25 && score < 60;
  if (filter === "low") return score < 25;
  return true;
}

function compareSessions(a, b, mode) {
  if (mode === "violations") return Number(b.totalViolations || 0) - Number(a.totalViolations || 0);
  if (mode === "risk") return Number(b.riskScore || 0) - Number(a.riskScore || 0);
  if (mode === "startedAt") return (b.startedAtMillis || timestampToMillis(b.startedAt)) - (a.startedAtMillis || timestampToMillis(a.startedAt));
  return timestampToMillis(b.lastActivity) - timestampToMillis(a.lastActivity);
}

function friendlyAuthError(error) {
  const code = error?.code || "";
  if (code.includes("invalid-credential") || code.includes("wrong-password")) {
    return "Invalid email address or password credentials.";
  }
  if (code.includes("user-not-found")) {
    return "No administrator account exists for that email.";
  }
  if (code.includes("too-many-requests")) {
    return "Account temporarily locked due to excess login requests. Try again later.";
  }
  return error.message || "An authentication error occurred.";
}

function humanizeViolation(type) {
  const labels = {
    APP_SWITCH: "Left protected environment",
    LEFT_APP: "Left Focus App",
    OVERLAY_DETECTED: "Suspicious overlay detected",
    SCREEN_OFF: "Screen turned off",
    SPLIT_SCREEN: "Split-screen active",
    UNKNOWN: "System anomaly detected"
  };
  return labels[type] || type || "Policy violation";
}

function timestampToMillis(val) {
  if (!val) return 0;
  if (typeof val.toMillis === "function") return val.toMillis();
  if (typeof val === "number") return val;
  if (typeof val.seconds === "number") return val.seconds * 1000;
  if (val instanceof Date) return val.getTime();
  const parsed = Date.parse(val);
  return isNaN(parsed) ? 0 : parsed;
}

function formatTimestamp(val) {
  const millis = timestampToMillis(val);
  return millis ? new Date(millis).toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }) : "-";
}

function formatTimestampDate(val) {
  const millis = timestampToMillis(val);
  return millis ? new Date(millis).toLocaleDateString(undefined, {
    dateStyle: "medium"
  }) : "-";
}

function formatTimestampTime(val) {
  const millis = timestampToMillis(val);
  return millis ? new Date(millis).toLocaleTimeString(undefined, {
    timeStyle: "medium"
  }) : "-";
}

function formatDuration(session) {
  const started = session.startedAtMillis || timestampToMillis(session.startedAt);
  const completed = timestampToMillis(session.completedAt);
  if (!started) return "-";
  
  const end = completed || Date.now();
  const diffSecs = Math.floor((end - started) / 1000);
  
  if (diffSecs < 60) return `${diffSecs}s`;
  const mins = Math.floor(diffSecs / 60);
  if (mins < 60) return `${mins}m ${diffSecs % 60}s`;
  const hrs = Math.floor(mins / 60);
  return `${hrs}h ${mins % 60}m`;
}

function escapeHtml(val) {
  return String(val || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
