// End-to-end API smoke test against a running backend (dev profile, demo data).
// Usage: node scripts/smoke-test.mjs [baseUrl]
import crypto from "node:crypto";

const BASE = process.argv[2] ?? "http://localhost:8417";
const PASSWORD = process.env.PAYOPS_ADMIN_PASSWORD ?? "PayOps#Admin2026";
let failures = 0;
let passes = 0;

function check(name, condition, detail = "") {
  if (condition) {
    passes++;
    console.log(`  ✓ ${name}`);
  } else {
    failures++;
    console.log(`  ✗ ${name} ${detail}`);
  }
}

async function call(method, path, { token, body, cookie, headers = {} } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(cookie ? { Cookie: cookie } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    json = text;
  }
  return { status: res.status, json, headers: res.headers };
}

function refreshCookie(res) {
  const raw = res.headers.getSetCookie?.() ?? [];
  const rt = raw.find((c) => c.startsWith("po_rt="));
  return rt ? rt.split(";")[0] : null;
}

function totp(base32, offsetSteps = 0) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const c of base32.replace(/=+$/, "")) bits += alphabet.indexOf(c).toString(2).padStart(5, "0");
  const bytes = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) bytes.push(parseInt(bits.slice(i, i + 8), 2));
  const counter = Math.floor(Date.now() / 1000 / 30) + offsetSteps;
  const buf = Buffer.alloc(8);
  buf.writeBigUInt64BE(BigInt(counter));
  const h = crypto.createHmac("sha1", Buffer.from(bytes)).update(buf).digest();
  const o = h[h.length - 1] & 0xf;
  const code = ((h[o] & 0x7f) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3];
  return String(code % 1_000_000).padStart(6, "0");
}

async function login(email, password = PASSWORD) {
  return call("POST", "/api/v1/auth/login", { body: { email, password } });
}

console.log(`PayOps360 smoke test → ${BASE}\n`);

console.log("Security baseline");
{
  const health = await call("GET", "/actuator/health");
  check("health is UP", health.json?.status === "UP");
  const anon = await call("GET", "/api/v1/payments");
  check("anonymous API access is rejected (401)", anon.status === 401);
  check("security headers present", anon.headers.get("x-frame-options") === "DENY" && !!anon.headers.get("content-security-policy"));
  const bad = await login("admin@payops360.io", "wrong-password");
  const unknown = await login("nobody@payops360.io", "wrong-password");
  check("wrong password → 401 generic", bad.status === 401 && bad.json?.error?.code === "INVALID_CREDENTIALS");
  check("unknown user indistinguishable from wrong password", unknown.json?.error?.message === bad.json?.error?.message);
  const forged = await call("GET", "/api/v1/payments", { token: "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIn0.invalid" });
  check("forged token rejected", forged.status === 401);
}

console.log("\nAuthentication & sessions");
const adminLogin = await login("admin@payops360.io");
check("admin login succeeds", adminLogin.status === 200 && adminLogin.json?.data?.status === "AUTHENTICATED", JSON.stringify(adminLogin.json));
let token = adminLogin.json?.data?.accessToken;
let cookie = refreshCookie(adminLogin);
check("refresh token only in HttpOnly cookie", !!cookie && !JSON.stringify(adminLogin.json).includes(cookie.split("=")[1]));
const me = await call("GET", "/api/v1/account/me", { token });
check("GET /account/me", me.status === 200 && me.json?.data?.user?.role === "ADMIN");

const noCsrf = await call("POST", "/api/v1/auth/refresh", { cookie });
check("refresh without anti-CSRF header rejected", noCsrf.status === 401);
const refreshed = await call("POST", "/api/v1/auth/refresh", { cookie, headers: { "X-Requested-With": "payops" } });
check("refresh rotates tokens", refreshed.status === 200 && !!refreshCookie(refreshed) && refreshCookie(refreshed) !== cookie);
const oldCookie = cookie;
cookie = refreshCookie(refreshed);
token = refreshed.json?.data?.accessToken ?? token;

console.log("\nPayments");
const list = await call("GET", "/api/v1/payments?size=5", { token });
check("list payments", list.status === 200 && list.json?.data?.items?.length === 5);
const failed = await call("GET", "/api/v1/payments?status=FAILED&size=1", { token });
const failedRef = failed.json?.data?.items?.[0]?.reference;
check("filter by status", failed.status === 200 && failed.json?.data?.items?.[0]?.status === "FAILED");
const detail = await call("GET", `/api/v1/payments/${failedRef}`, { token });
check("payment detail with timeline", detail.status === 200 && detail.json?.data?.timeline?.length >= 2);
const idem = crypto.randomUUID();
const ingestBody = { amount: 250.5, currency: "GBP", providerCode: "adyen", customerRef: "CUS-SMOKE-1", method: "CARD", cardLast4: "4242", country: "PK" };
const created = await call("POST", "/api/v1/payments", { token, body: ingestBody, headers: { "Idempotency-Key": idem } });
check("ingest payment (201)", created.status === 201 && created.json?.data?.status === "INITIATED", JSON.stringify(created.json));
const replay = await call("POST", "/api/v1/payments", { token, body: ingestBody, headers: { "Idempotency-Key": idem } });
check("idempotent replay returns same payment", replay.status === 200 && replay.json?.data?.reference === created.json?.data?.reference);
const ref = created.json?.data?.reference;
const auth = await call("POST", `/api/v1/payments/${ref}/status`, { token, body: { status: "AUTHORIZED" } });
check("valid transition INITIATED→AUTHORIZED", auth.status === 200 && auth.json?.data?.status === "AUTHORIZED");
const illegal = await call("POST", `/api/v1/payments/${ref}/status`, { token, body: { status: "REFUNDED" } });
check("illegal transition rejected (422)", illegal.status === 422);
const badAmount = await call("POST", "/api/v1/payments", { token, body: { ...ingestBody, amount: 1.234, currency: "JPY" } });
check("currency precision validated", badAmount.status === 422);

console.log("\nProviders, alerts, incidents, analytics");
const providers = await call("GET", "/api/v1/providers", { token });
check("providers listed with health", providers.status === 200 && providers.json?.data?.length >= 8 && !!providers.json.data[0].health);
const overview = await call("GET", "/api/v1/analytics/overview?window=PT24H", { token });
check("analytics overview", overview.status === 200 && overview.json?.data?.kpis?.length === 8);
const series = await call("GET", "/api/v1/analytics/timeseries?window=PT24H&bucket=PT1H", { token });
check("timeseries 24 buckets", series.status === 200 && series.json?.data?.length === 24);
for (const p of ["providers", "failure-reasons", "methods", "risk", "corridors"]) {
  const r = await call("GET", `/api/v1/analytics/${p}?window=P3D`, { token });
  check(`analytics/${p}`, r.status === 200 && Array.isArray(r.json?.data));
}
const alerts = await call("GET", "/api/v1/alerts", { token });
check("alerts list", alerts.status === 200);
const alertSummary = await call("GET", "/api/v1/alerts/summary", { token });
check("alerts summary", alertSummary.status === 200);
const declared = await call("POST", "/api/v1/incidents", { token, body: { title: "Smoke test incident", severity: "MEDIUM", providerCode: "stripe" } });
check("declare incident", declared.status === 200 && declared.json?.data?.status === "OPEN", JSON.stringify(declared.json));
const incRef = declared.json?.data?.reference;
const ack = await call("POST", `/api/v1/incidents/${incRef}/acknowledge`, { token });
check("acknowledge incident", ack.json?.data?.status === "ACKNOWLEDGED");
const rca = await call("POST", `/api/v1/ai/incidents/${incRef}/investigate`, { token });
check("AI root-cause analysis", rca.status === 200 && !!rca.json?.data?.aiAnalysis?.rootCause, JSON.stringify(rca.json).slice(0, 300));
const resolved = await call("POST", `/api/v1/incidents/${incRef}/resolve`, { token, body: { note: "Smoke test complete" } });
check("resolve incident", resolved.json?.data?.status === "RESOLVED");
const incSummary = await call("GET", "/api/v1/incidents/summary", { token });
check("incident summary with MTTR", incSummary.status === 200 && incSummary.json?.data?.resolvedLast30Days >= 1);

console.log("\nAI capabilities");
const insights = await call("GET", "/api/v1/ai/insights", { token });
check("AI insights (health, forecast, routing)", insights.status === 200 && typeof insights.json?.data?.healthScore === "number" && insights.json.data.routing.length > 0);
const copilot = await call("POST", "/api/v1/ai/copilot", { token, body: { question: "Why are payments failing in the last 24 hours?" } });
check("copilot answers", copilot.status === 200 && copilot.json?.data?.answer?.length > 20);
const search = await call("POST", "/api/v1/ai/search/interpret", { token, body: { query: "failed card payments over 500 GBP to Pakistan in the last 6 hours" } });
const s = search.json?.data;
check("smart search interprets NL query", s?.statuses?.includes("FAILED") && s?.method === "CARD" && Number(s?.minAmount) === 500 && s?.country === "PK" && s?.lastHours === 6, JSON.stringify(s));
const explain = await call("GET", `/api/v1/ai/payments/${failedRef}/explain`, { token });
check("payment explanation", explain.status === 200 && !!explain.json?.data?.failureCategory);
const briefing = await call("GET", "/api/v1/ai/briefing", { token });
check("executive briefing", briefing.status === 200 && briefing.json?.data?.markdown?.length > 100);

console.log("\nAuthorization (least privilege)");
const analyst = await login("analyst@payops360.io");
const analystToken = analyst.json?.data?.accessToken;
const analystUsers = await call("GET", "/api/v1/users", { token: analystToken });
check("analyst cannot manage users (403)", analystUsers.status === 403);
const analystWrite = await call("POST", "/api/v1/payments", { token: analystToken, body: ingestBody });
check("analyst cannot create payments (403)", analystWrite.status === 403);
const auditor = await login("auditor@payops360.io");
const auditLog = await call("GET", "/api/v1/audit?size=5", { token: auditor.json?.data?.accessToken });
check("auditor reads audit trail", auditLog.status === 200 && auditLog.json?.data?.items?.length > 0);
const verify = await call("GET", "/api/v1/audit/verify", { token });
check("audit hash chain intact", verify.json?.data?.intact === true, JSON.stringify(verify.json?.data));

console.log("\nUser administration & MFA");
const email = `smoke.${Date.now()}@payops360.io`;
const createdUser = await call("POST", "/api/v1/users", { token, body: { email, fullName: "Smoke Tester", role: "SUPPORT" } });
check("admin creates user with temp password", createdUser.status === 200 && createdUser.json?.data?.temporaryPassword?.length === 16);
const temp = createdUser.json?.data?.temporaryPassword;
const firstLogin = await login(email, temp);
const firstToken = firstLogin.json?.data?.accessToken;
const blocked = await call("GET", "/api/v1/payments", { token: firstToken });
check("must change password before using the API", blocked.status === 403 && blocked.json?.error?.code === "PASSWORD_CHANGE_REQUIRED");
const weak = await call("POST", "/api/v1/account/password", { token: firstToken, body: { currentPassword: temp, newPassword: "password123" } });
check("weak password rejected", weak.status === 422);
const newPassword = "Str0ng!Ops#Pass2026";
const changed = await call("POST", "/api/v1/account/password", { token: firstToken, body: { currentPassword: temp, newPassword } });
check("password changed", changed.status === 200, JSON.stringify(changed.json));
const relogin = await login(email, newPassword);
const userToken = relogin.json?.data?.accessToken;
const enroll = await call("POST", "/api/v1/account/mfa/enroll/start", { token: userToken });
check("MFA enrolment returns otpauth URI", enroll.json?.data?.otpauthUri?.startsWith("otpauth://totp/"));
const confirm = await call("POST", "/api/v1/account/mfa/enroll/confirm", { token: userToken, body: { code: totp(enroll.json.data.secret) } });
check("MFA confirmed with TOTP, 10 recovery codes", confirm.json?.data?.recoveryCodes?.length === 10, JSON.stringify(confirm.json));
const mfaLogin = await login(email, newPassword);
check("login now requires MFA", mfaLogin.json?.data?.status === "MFA_REQUIRED");
const wrongCode = await call("POST", "/api/v1/auth/mfa/verify", { body: { challengeToken: mfaLogin.json.data.challengeToken, code: "000000" } });
check("wrong TOTP rejected", wrongCode.status === 401);
const recovery = confirm.json?.data?.recoveryCodes?.[0];
const recoveryLogin = await call("POST", "/api/v1/auth/mfa/verify", { body: { challengeToken: mfaLogin.json.data.challengeToken, code: recovery } });
check("recovery code completes MFA", recoveryLogin.json?.data?.status === "AUTHENTICATED");
const reuseLogin = await login(email, newPassword);
const reused = await call("POST", "/api/v1/auth/mfa/verify", { body: { challengeToken: reuseLogin.json.data.challengeToken, code: recovery } });
check("recovery code is single-use", reused.status === 401);

console.log("\nLockout & token theft detection");
let lastStatus;
for (let i = 0; i < 6; i++) lastStatus = (await login(email, "Wrong!Password1")).status;
const lockedOut = await login(email, newPassword);
check("account locks after 5 failures (correct password refused)", lockedOut.status === 401 && lastStatus === 401);
const unlock = await call("POST", `/api/v1/users/${createdUser.json.data.user.id}/unlock`, { token });
check("admin unlocks account", unlock.status === 200 && unlock.json?.data?.locked === false);
await new Promise((r) => setTimeout(r, 11_000));
const theft = await call("POST", "/api/v1/auth/refresh", { cookie: oldCookie, headers: { "X-Requested-With": "payops" } });
check("replayed refresh token rejected", theft.status === 401);
const familyRevoked = await call("POST", "/api/v1/auth/refresh", { cookie, headers: { "X-Requested-With": "payops" } });
check("token reuse revokes the whole session family", familyRevoked.status === 401);

console.log("\nRate limiting");
let throttled = false;
for (let i = 0; i < 150 && !throttled; i++) {
  throttled = (await login("nobody@payops360.io", "x")).status === 429;
}
check("auth endpoints are rate limited per IP (429)", throttled);

console.log(`\n${passes} passed, ${failures} failed`);
process.exit(failures === 0 ? 0 : 1);
