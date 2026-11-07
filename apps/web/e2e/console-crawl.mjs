#!/usr/bin/env node
/**
 * The console crawl (Dev preview `admin.shell`, `admin.ux-review`): every screen of the operator console in dark AND light,
 * on a desktop and a phone viewport — the NAV-SYS bar with its DEV badge, no console error, no horizontal scroll, no
 * broken picture, no empty tile, no hard-coded colour, and the five places of config/sections.ts in the bar. The console is
 * gated by address, so this runs from an allowed address (a GitHub runner is refused: dev-preview-live.yml proves that
 * gate); the operator is read from the environment (E2E_ADMIN_USER, E2E_ADMIN_SECRET), never printed.
 *
 *   OUT=/tmp/console-crawl npm run test:console-crawl
 */
import { mkdirSync } from "node:fs";
import { join } from "node:path";

const BASE = (process.env.CONSOLE_URL || "https://admin.orazaka.com").replace(/\/$/, "");
const OUT = process.env.OUT || "";
const USER = process.env.E2E_ADMIN_USER;
const SECRET = process.env.E2E_ADMIN_SECRET;
const THEMES = ["dark", "light"];
const VIEWPORTS = { desktop: { width: 1440, height: 900 }, phone: { width: 390, height: 844 } };
const SCREENS = ["/", "/operations", "/tasks", "/users", "/billing", "/models", "/studios", "/law", "/audit"];
const PLACES = ["Supervision", "Operations", "Accounts", "Catalogue", "Audit log"];
const IGNORED_CONSOLE = [/Download the React DevTools/i, /favicon/i, /Failed to load resource.*(401|403|404)/i];
const say = (line) => process.stdout.write(`${line}\n`);

let chromium;
try {
  ({ chromium } = await import("playwright"));
} catch {
  process.stderr.write("✗ Playwright is missing: npm install, then npx playwright install chromium.\n");
  process.exit(2);
}
if (!(USER && SECRET)) {
  process.stderr.write("✗ set E2E_ADMIN_USER and E2E_ADMIN_SECRET (an operator account)\n");
  process.exit(2);
}

const PAGES = [
  { name: "login", url: "/login", chrome: "auth" },
  ...SCREENS.map((url) => ({
    name: url === "/" ? "supervision" : url.slice(1),
    url,
    session: true,
    chrome: "console",
  })),
];

if (OUT) mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch();

const context = await browser.newContext();
const page = await context.newPage();
await page.goto(`${BASE}/login`, { waitUntil: "networkidle" });
await page.fill('input[type="email"]', USER);
await page.fill('input[type="password"]', SECRET);
await page.keyboard.press("Enter");
await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 30_000 }).catch(() => {});
if (new URL(page.url()).host !== new URL(BASE).host || new URL(page.url()).pathname.startsWith("/login")) {
  process.stderr.write("✗ the operator could not sign in (is this address allowed?)\n");
  process.exit(2);
}
const signedIn = await context.storageState();
await context.close();

/** What the page says about its layout and the console contract (run in the page). */
function inspect([chrome, places]) {
  const problems = [];
  const doc = document.documentElement;
  if (doc.scrollWidth > doc.clientWidth + 1)
    problems.push(`horizontal scroll (${doc.scrollWidth}px in a ${doc.clientWidth}px viewport)`);
  if (chrome === "console") {
    const bar = document.querySelector("nav, header");
    if (!bar) problems.push("no bar");
    if (!/\bdev\b/i.test((document.body.innerText || "").slice(0, 300))) problems.push("no DEV badge");
    const text = document.body.textContent || "";
    for (const place of places) if (!text.includes(place)) problems.push(`place « ${place} » missing from the bar`);
  }
  for (const img of document.querySelectorAll("img")) {
    const box = img.getBoundingClientRect();
    if (box.width > 8 && box.height > 8 && img.complete && img.naturalWidth === 0 && img.currentSrc)
      problems.push(`broken picture ${img.currentSrc.slice(0, 80)}`);
  }
  for (const el of document.querySelectorAll("main a[href], main button")) {
    const box = el.getBoundingClientRect();
    const style = getComputedStyle(el);
    const visible = style.visibility !== "hidden" && style.display !== "none";
    if (
      visible &&
      box.width > 24 &&
      box.height > 24 &&
      !el.textContent?.trim() &&
      !el.getAttribute("aria-label") &&
      !el.getAttribute("aria-labelledby") &&
      !el.querySelector("img, svg, video, picture, canvas")
    )
      problems.push("empty tile (a control with nothing in it)");
  }
  const hard = [...document.querySelectorAll("[style]")].filter(
    (e) =>
      /#[0-9a-f]{3,8}\b|rgba?\(/i.test(e.getAttribute("style") || "") && !/var\(/.test(e.getAttribute("style") || ""),
  );
  if (hard.length > 0) problems.push(`${hard.length} element(s) with a hard-coded colour in an inline style`);
  return [...new Set(problems)];
}

async function crawl(p, theme, device, viewport) {
  const ctx = await browser.newContext({
    viewport,
    colorScheme: theme,
    reducedMotion: "reduce",
    ...(p.session ? { storageState: signedIn } : {}),
  });
  await ctx.addInitScript((mode) => {
    try {
      localStorage.setItem("kz-theme", mode);
    } catch {}
  }, theme);
  const consoleErrors = [];
  try {
    const tab = await ctx.newPage();
    tab.on(
      "console",
      (m) =>
        m.type() === "error" &&
        !IGNORED_CONSOLE.some((re) => re.test(m.text())) &&
        consoleErrors.push(m.text().slice(0, 160)),
    );
    tab.on("pageerror", (e) => consoleErrors.push(`uncaught: ${e.message.slice(0, 160)}`));
    await tab
      .goto(`${BASE}${p.url}`, { waitUntil: "networkidle", timeout: 60_000 })
      .catch(() => tab.waitForLoadState("load"));
    await tab.waitForTimeout(800);
    // On a phone the places sit behind the menu button: open it before reading the bar.
    if (p.chrome === "console" && device === "phone")
      await tab
        .getByRole("button", { name: /open the menu/i })
        .click()
        .catch(() => {});
    const problems = await tab.evaluate(inspect, [p.chrome, PLACES]);
    if (consoleErrors.length > 0) problems.push(...consoleErrors.slice(0, 3).map((m) => `console: ${m}`));
    if (OUT) await tab.screenshot({ path: join(OUT, `${p.name}.${theme}.${device}.png`), fullPage: false });
    return problems;
  } finally {
    await ctx.close();
  }
}

let failures = 0;
let checked = 0;
for (const [device, viewport] of Object.entries(VIEWPORTS)) {
  for (const theme of THEMES) {
    for (const p of PAGES) {
      const label = `${p.name} · ${theme} · ${device}`;
      let problems;
      for (let attempt = 1; attempt <= 2 && !problems; attempt++) {
        try {
          problems = await crawl(p, theme, device, viewport);
        } catch (error) {
          if (attempt === 2) problems = [`could not be crawled: ${error.message.split("\n")[0]}`];
        }
      }
      checked++;
      if (problems.length === 0) say(`✓ ${label}`);
      else {
        failures++;
        say(`✗ ${label}`);
        for (const problem of problems) say(`    · ${problem}`);
      }
    }
  }
}
await browser.close();
say(`\n${checked - failures}/${checked} screens clean${OUT ? `, captures in ${OUT}` : ""}`);
process.exit(failures > 0 ? 1 : 0);
