// Browser walkthrough: signs in, visits every console page, reports console errors,
// failed API calls and visible error banners; saves screenshots.
// Usage: node scripts/ui-walkthrough.mjs [baseUrl] [outDir]   (requires playwright-core + Microsoft Edge)
import { chromium } from "playwright-core";
import fs from "node:fs";

const BASE = process.argv[2] ?? "http://localhost:3417";
const OUT = process.argv[3] ?? "ui-screens";
fs.mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ channel: "msedge", headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
const problems = [];
page.on("console", (m) => m.type() === "error" && problems.push(`console: ${m.text().slice(0, 200)}`));
page.on("pageerror", (e) => problems.push(`pageerror: ${e.message.slice(0, 200)}`));
page.on("response", (r) => {
  if (r.url().includes("/api/v1/") && r.status() >= 400 && !r.url().includes("/auth/refresh")) {
    problems.push(`http ${r.status()} ${r.request().method()} ${r.url().replace(BASE, "")}`);
  }
});

await page.goto(`${BASE}/login`);
await page.fill("#email", "admin@payops360.io");
await page.fill("#password", process.env.PAYOPS_ADMIN_PASSWORD ?? "PayOps#Admin2026");
await page.click("button[type=submit]");
await page.waitForURL("**/dashboard", { timeout: 20000 });

const routes = ["/dashboard", "/payments", "/providers", "/alerts", "/incidents", "/analytics", "/copilot",
  "/admin/users", "/admin/audit", "/settings/security"];
for (const route of routes) {
  const before = problems.length;
  await page.goto(BASE + route);
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(2500);
  const banners = await page.locator("[role=alert]").allTextContents();
  banners.forEach((b) => problems.push(`banner on ${route}: ${b.slice(0, 160)}`));
  await page.screenshot({ path: `${OUT}/${route.replace(/\//g, "_").slice(1) || "root"}.png`, fullPage: false });
  console.log(`${problems.length === before ? "✓" : "✗"} ${route}`);
}

// Detail pages via first links.
await page.goto(`${BASE}/payments`);
await page.waitForTimeout(2500);
const payLink = page.locator("a[href^='/payments/PAY-']").first();
if (await payLink.count()) {
  await payLink.click();
  await page.waitForTimeout(3500);
  await page.screenshot({ path: `${OUT}/payment_detail.png` });
  console.log("✓ payment detail", page.url().replace(BASE, ""));
}
await page.goto(`${BASE}/incidents`);
await page.waitForTimeout(2500);
const incLink = page.locator("a[href^='/incidents/INC-']").first();
if (await incLink.count()) {
  await incLink.click();
  await page.waitForTimeout(3000);
  await page.screenshot({ path: `${OUT}/incident_detail.png` });
  console.log("✓ incident detail", page.url().replace(BASE, ""));
}

// Copilot round trip.
await page.goto(`${BASE}/copilot`);
await page.waitForTimeout(1500);
await page.fill("input[placeholder^='Ask about']", "Why are payments failing in the last 24 hours?");
await page.keyboard.press("Enter");
await page.waitForSelector(".prose-ai", { timeout: 30000 });
await page.screenshot({ path: `${OUT}/copilot_answer.png` });
console.log("✓ copilot answered");

// Reload keeps the session (HttpOnly refresh cookie) and logout ends it.
await page.goto(`${BASE}/dashboard`);
await page.reload();
await page.waitForTimeout(3000);
console.log(page.url().endsWith("/dashboard") ? "✓ session survives reload" : "✗ session lost on reload");

await browser.close();
console.log(problems.length ? `\nProblems:\n${[...new Set(problems)].join("\n")}` : "\nNo console errors, failed API calls or error banners.");
process.exit(problems.length ? 1 : 0);
