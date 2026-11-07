// Dev preview `ui.themes-devices` (orazaka-web-client#124): the UI crawl exists, covers both themes and both viewports,
// reads the sitemap, and the post-deploy workflow runs it against the deployed site.
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";

const read = (rel) => readFileSync(new URL(`../${rel}`, import.meta.url), "utf8");
const crawl = read("e2e/ui-crawl.mjs");

test("the crawl covers dark and light, phone and desktop", () => {
  assert.match(crawl, /THEMES = \["dark", "light"\]/);
  assert.match(crawl, /width: 1440, height: 900/);
  assert.match(crawl, /width: 390, height: 844/);
});

test("it checks what the criterion asks through the shared @krizaka/page-audit: console errors, horizontal scroll and containment, one footer, broken pictures, empty tiles, colours", () => {
  assert.match(crawl, /from "@krizaka\/page-audit"/);
  assert.match(crawl, /auditRoutes\(/);
  // The checks the audit measures (scroll, boxes past the edge and wider than their own) are always on; these are the ones this crawl turns on.
  for (const needle of [
    'landmarks: "public"',
    "boxes: true",
    "pictures: true",
    "emptyControls: true",
    "hardColors: true",
    "ignoreConsole: IGNORED_CONSOLE",
  ]) {
    assert.ok(crawl.includes(needle), `missing: ${needle}`);
  }
  assert.match(crawl, /chrome: "auth"/);
  assert.match(crawl, /chrome: "app"/);
});

test("public screens come from the sitemap, signed-in screens need a persona (E2E_USER / E2E_PASSWORD)", () => {
  assert.match(crawl, /routesFromSitemap\(BASE/);
  assert.match(crawl, /E2E_USER/);
  assert.match(crawl, /E2E_PASSWORD/);
  for (const screen of ["/", "/studios", "/packs", "/chat", "/jobs"])
    assert.ok(crawl.includes(`"${screen}"`), `signed-in screen ${screen}`);
});

test("captures go to a scratch folder (OUT), never into the repository", () => {
  assert.match(crawl, /process\.env\.OUT/);
  assert.doesNotMatch(crawl, /docs\/screenshots/);
});

test("it is an npm script and a step of its own post-deploy workflow (a failing smoke never hides it)", () => {
  assert.equal(JSON.parse(read("package.json")).scripts["test:ui-crawl"], "node e2e/ui-crawl.mjs");
  const wf = read(".github/workflows/dev-preview-live.yml");
  assert.ok(wf.includes("npm run test:ui-crawl"));
  assert.ok(wf.includes("Wait for the deployed build"));
});

test("the post-deploy workflow also runs the SEO family contract against the deployed site, indexed (seo.family)", () => {
  const wf = read(".github/workflows/dev-preview-live.yml");
  assert.match(
    wf,
    /node scripts\/check-seo-family\.mjs --base https:\/\/www\.orazaka\.com --host https:\/\/www\.orazaka\.com(\s|$)/,
  );
  assert.ok(!/--index off/.test(wf), "www.orazaka.com is indexed (AGENTS.md)");
});

test("the console crawl covers every console screen in both themes and both viewports, and checks the DEV badge (admin.shell)", () => {
  const c = read("e2e/console-crawl.mjs");
  assert.match(c, /THEMES = \["dark", "light"\]/);
  assert.match(c, /desktop:/);
  assert.match(c, /phone:/);
  for (const screen of ["/operations", "/tasks", "/users", "/billing", "/models", "/studios", "/audit"])
    assert.ok(c.includes(`"${screen}"`), screen);
  assert.ok(c.includes("no DEV badge"));
  assert.equal(JSON.parse(read("package.json")).scripts["test:console-crawl"], "node e2e/console-crawl.mjs");
});

test("the same workflow proves the console gate from outside: 403 on the admin host, 404 on the public one (admin.gated)", () => {
  const wf = read(".github/workflows/dev-preview-live.yml");
  assert.match(wf, /admin\.orazaka\.com\/login[^\n]*\n?[^\n]*= 403/);
  assert.match(wf, /www\.orazaka\.com\/console[^\n]*= 404/);
});

test("the crawl also runs axe-core (WCAG 2.1 AA) through the audit's a11y step and ratchets it against the baselines of this repo", () => {
  const flat = crawl.replace(/\s+/g, " "); // the layout (Prettier) is not what is checked
  assert.match(flat, /a11y: \{ app: "orazaka-web-client", baseline: A11Y_BASELINE/);
  assert.match(flat, /app: "orazaka-web-client-app", baseline: A11Y_BASELINE_APP/);
  assert.match(crawl, /a11y-baseline\.json/);
  assert.match(crawl, /a11y-baseline\.app\.json/);
  assert.doesNotMatch(crawl, /from "axe-core"/, "apps never import axe-core");
});

test("a11y-baseline.json holds counts, and no serious or critical rule (those are fixed, not recorded)", () => {
  const { rules } = JSON.parse(read("a11y-baseline.json"));
  for (const [rule, { impact, count }] of Object.entries(rules)) {
    assert.ok(Number.isInteger(count) && count > 0, `${rule}: a positive count`);
    assert.ok(["minor", "moderate"].includes(impact), `${rule} is ${impact}: fix it, do not record it`);
  }
});

test("a signed-in screen that shows « qa_user » fails the crawl: one shared regex, used by the crawl, the takes and the recordings", async () => {
  const { QA_ON_SCREEN } = await import("./qa-on-screen.mjs");
  for (const bad of ["Good morning, qa_user", "qa-user", "qa_admin", "Hello QA", "signed in as qa user"])
    assert.match(bad, QA_ON_SCREEN, bad);
  for (const ok of ["Good morning, Éric", "Mira Haddad", "Quality", "aqa"]) assert.doesNotMatch(ok, QA_ON_SCREEN, ok);
  assert.match(crawl, /qaOnScreen\(page\)/);
  assert.match(crawl, /problems\.push\(/);
  for (const f of ["scripts/record-web-steps.mjs", "scripts/landing-take.mjs"]) {
    const src = read(f);
    assert.match(src, /from "\.\/qa-on-screen\.mjs"/, f);
    assert.doesNotMatch(src, /qa_user\|/, `${f} keeps its own copy of the regex`);
  }
});
