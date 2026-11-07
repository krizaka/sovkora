#!/usr/bin/env node
/**
 * The UI crawl (Dev preview `ui.krizaka-ui` + `ui.themes-devices`, orazaka-web-client#124): the key screens in dark AND
 * light, on a desktop and a phone viewport, against a DEPLOYED Sovkora (E2E_BASE_URL, https://www.orazaka.com by default):
 *
 *   - no console error and no uncaught page error;
 *   - no horizontal scroll (nothing cut at the side);
 *   - the page never overflows (containment, krizaka-ui `parameters.contain`): no visible box sticks out past the
 *     viewport or past the scroll container that holds it (a decorative aria-hidden layer or a clipped one may);
 *   - exactly one footer (« A Krizaka product ») and a navigation landmark;
 *   - no broken picture and no empty tile (a control that renders nothing);
 *   - no hard-coded colour in the inline styles of the page (the tokens are `var(--kz-*)`);
 *   - axe-core (WCAG 2.1 AA, through @krizaka/a11y-audit) on every screen: a ratchet (`a11y-baseline.json` for the public
 *     screens and the sign-in, `a11y-baseline.app.json` for the signed-in ones) counts the violations per rule — a new one
 *     fails, the count only goes down (`--a11y-update` lowers it, `--a11y-init` records today's counts).
 *
 * Public screens come from the site's own sitemap.xml (a page added there is crawled); the signed-in screens need a
 * demo persona (E2E_USER / E2E_PASSWORD) and are skipped, said out loud, without one. A capture of each screen is saved
 * when OUT is set (the set goes to Bunny `pr-assets/orazaka-web-client/dev-preview/`, never to git).
 *
 *   E2E_USER=… E2E_PASSWORD=… OUT=/tmp/orazaka-crawl npm run test:ui-crawl
 *
 * Read-only: it opens pages, never submits anything but the sign-in form.
 *
 * The measures are the shared @krizaka/page-audit (krizaka-ui#747); this file holds the pages, the persona's sign-in and the output.
 */
import { mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";

import { auditRoutes, routesFromSitemap } from "@krizaka/page-audit";

import { qaOnScreen } from "../scripts/qa-on-screen.mjs";

const BASE = (process.env.E2E_BASE_URL || "https://www.orazaka.com").replace(/\/$/, "");
const OUT = process.env.OUT || "";
const USER = process.env.E2E_USER;
const PASSWORD = process.env.E2E_PASSWORD;
const PUBLIC_LIMIT = Number(process.env.CRAWL_PUBLIC_LIMIT || 14);
const THEMES = ["dark", "light"];
/** Desktop and phone, each with its height; the audit measures them in the order given. */
const VIEWPORTS = [
  { width: 1440, height: 900 },
  { width: 390, height: 844 },
];
const SIGNED_IN = ["/", "/studios", "/packs", "/chat", "/jobs", "/usage", "/automation", "/settings"];

/** Console messages that are not Sovkora's (a blocked third-party beacon, a missing favicon). */
const IGNORED_CONSOLE = [/Download the React DevTools/i, /favicon/i, /Failed to load resource.*(401|403|404)/i];

const A11Y_BASELINE = fileURLToPath(new URL("../a11y-baseline.json", import.meta.url));
const A11Y_BASELINE_APP = fileURLToPath(new URL("../a11y-baseline.app.json", import.meta.url));

const say = (line) => process.stdout.write(`${line}\n`);

let chromium;
try {
  // `playwright` is a transitive dependency of `@playwright/test`: a strict node_modules (pnpm) only exposes the latter.
  ({ chromium } = await import("playwright").catch(() => import("@playwright/test")));
} catch {
  console.error("✗ Playwright is missing: npm install, then npx playwright install chromium.");
  process.exit(2);
}

// A crawl as the route source: the site's own sitemap, the sections with a distinct layout first (home, docs, the Mac page), then the rest up to the limit.
const picked = await routesFromSitemap(BASE, { first: ["/", "/docs", "/desktop"], limit: PUBLIC_LIMIT });
if (picked === null) {
  console.error(`✗ Nothing answers at ${BASE}/sitemap.xml: set E2E_BASE_URL to a running Sovkora.`);
  process.exit(2);
}
const PAGES = [
  ...picked.map((url) => ({ name: url === "/" ? "home" : url.slice(1).replace(/\//g, "-"), url })),
  // The sign-in screen is the auth layout (no bar, no footer, by design); the signed-in app is the rail + header shell
  // (a navigation landmark, no page footer): the family footer belongs to the public pages.
  { name: "login", url: "/login", chrome: "auth" },
  ...(USER && PASSWORD
    ? SIGNED_IN.map((url) => ({ name: url === "/" ? "app-home" : url.slice(1), url, session: true, chrome: "app" }))
    : []),
];
if (!(USER && PASSWORD)) say("· signed-in screens skipped: set E2E_USER and E2E_PASSWORD (a demo persona)");

if (OUT) mkdirSync(OUT, { recursive: true });

let storageState;
if (USER && PASSWORD) {
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${BASE}/login`, { waitUntil: "networkidle" });
  await page.fill('input[type="email"]', USER);
  await page.fill('input[type="password"]', PASSWORD);
  await page.keyboard.press("Enter");
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 30_000 }).catch(() => {});
  if (new URL(page.url()).pathname.startsWith("/login")) {
    console.error("✗ the persona could not sign in on the deployed site");
    process.exit(2);
  }
  storageState = await context.storageState();
  await browser.close();
}

// The signed-in screens are ratcheted once their baseline is recorded from a persona run (`--a11y-init`); until then the audit counts them only.
const problems = await auditRoutes({
  base: BASE,
  routes: PAGES,
  widths: VIEWPORTS,
  themes: THEMES,
  settleMs: 500,
  attempts: 2,
  shots: OUT || undefined,
  ignoreConsole: IGNORED_CONSOLE,
  launch: () => chromium.launch(),
  session: storageState ? { storageState } : undefined,
  a11y: {
    app: "orazaka-web-client",
    baseline: A11Y_BASELINE,
    widths: VIEWPORTS.map((v) => v.width),
    log: say,
    ...(USER && PASSWORD ? { session: { app: "orazaka-web-client-app", baseline: A11Y_BASELINE_APP } } : {}),
  },
  // The family contract and the inline-colour rule of the old crawl, plus the audit's own layout measures (overflow, containment, boxes wider than their own).
  checks: { landmarks: "public", boxes: true, pictures: true, emptyControls: true, hardColors: true },
});

// A demo shows a persona, never « qa_user » (krizaka/.github#356): every signed-in screen is read for a QA name.
if (storageState) {
  const browser = await chromium.launch();
  const context = await browser.newContext({ storageState });
  const page = await context.newPage();
  for (const url of SIGNED_IN) {
    await page.goto(`${BASE}${url}`, { waitUntil: "networkidle" });
    await page.waitForTimeout(500);
    const qa = await qaOnScreen(page);
    if (qa)
      problems.push(
        `${url}: « ${qa} » is on a signed-in screen — the account must carry a persona's name (@krizaka/org cast)`,
      );
  }
  await browser.close();
}
for (const problem of problems) say(`✗ ${problem}`);
const verdict = problems.length === 0 ? "every screen clean" : `${problems.length} problem(s)`;
say(`\n${verdict}${OUT ? `, captures in ${OUT}` : ""}`);
process.exit(problems.length > 0 ? 1 : 0);
