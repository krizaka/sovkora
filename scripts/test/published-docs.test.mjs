// The hand-written docs the site publishes (dev.sovkora.com/docs): no link to an internal document, no autolink the
// site's markdown cannot render, and one Node.js minimum — the one CI runs.
import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const read = (f) => fs.readFileSync(path.join(root, f), "utf8");
/** Published by orazaka-web-client (scripts/docs-manifest.mjs). */
const PUBLISHED = ["docs/101.md", "docs/CONCEPTS.md", "docs/SELF_HOSTING.md", "docs/STUDIO_GUIDE.md", "docs/CONTENT_POLICY.md", "SECURITY.md", "docs/AUTH.md", "docs/LICENSING.md"];
/** Internal: strategy, pricing, audits. */
const INTERNAL = ["DELIVERY.md", "BILLING_ARCHITECTURE.md", "PRODUCTION_READINESS_AUDIT.md", "ARCHITECTURE_ASSESSMENT.md"];

test("a published doc never links to an internal document", () => {
  for (const f of PUBLISHED) {
    const md = read(f);
    for (const name of INTERNAL) assert.ok(!md.includes(name), `${f} links ${name}`);
  }
});

test("a published doc has no <https://…> autolink (the site would print it literally)", () => {
  for (const f of PUBLISHED) assert.ok(!/<https?:\/\//.test(read(f)), `${f} has an autolink`);
});

test("the docs state the Node.js minimum CI runs", () => {
  const ci = [...read(".github/workflows/ci.yml").matchAll(/node-version:\s*"(\d+)"/g)].map((m) => m[1]);
  assert.ok(ci.length > 0);
  const major = ci[0];
  for (const f of ["docs/101.md", "docs/SELF_HOSTING.md", "README.md", "docs/DEVEX_LIFECYCLE.md"]) {
    const mins = [...read(f).matchAll(/Node(?:\.js)?\s+(\d+)\+/g)].map((m) => m[1]);
    for (const m of mins) assert.equal(m, major, `${f} says Node ${m}+, CI runs ${major}`);
  }
});

// Unpublished on dev.sovkora.com (orazaka-web-client#231): a marketing playbook, a tour and a generated stub.
// A published doc, and the README, must not link them or the removed /gallery page.
test("no published doc links a page the site no longer publishes", () => {
  const gone = [/BUSINESS_IMPLEMENTATION\.md/, /CINEMATIC_SHORTS\.md/, /\]\(USE_CASES\.md\)/, /orazaka\.com\/gallery/];
  for (const f of [...PUBLISHED, "docs/_generated/PACKS.md", "README.md"]) {
    const md = read(f);
    for (const re of gone) assert.ok(!re.test(md), `${f} links an unpublished page (${re})`);
  }
});

// The short path (krizaka/orazaka#351): Quickstart · Concepts · Guides are read; the generated Reference is folded.
// A page of the reading path stays short, and in short sentences — the budget is the test.
const READING_PATH = { "docs/101.md": 450, "docs/CONCEPTS.md": 650, "docs/SELF_HOSTING.md": 550, "docs/STUDIO_GUIDE.md": 450, "docs/CONTENT_POLICY.md": 450 };

/** The prose of a page: no front matter, no code fence, no table, no heading, no link target. */
function prose(md) {
  let fence = false;
  return md
    .replace(/^---\n[\s\S]*?\n---\n/, "")
    .split("\n")
    .filter((line) => {
      if (/^ {0,3}(```|~~~)/.test(line)) fence = !fence;
      return !fence && !/^ {0,3}(```|~~~)/.test(line) && !/^\s*\|/.test(line) && !/^#+ /.test(line);
    })
    .join("\n")
    .replace(/\]\([^)]*\)/g, "]")
    .replace(/`[^`]*`/g, "x");
}

test("the reading path is short: a word budget per page", () => {
  for (const [f, max] of Object.entries(READING_PATH)) {
    const words = read(f).split(/\s+/).filter(Boolean).length;
    assert.ok(words <= max, `${f} has ${words} words, the budget is ${max}`);
  }
});

test("the reading path is written in short sentences (average of 18 words or fewer)", () => {
  for (const f of ["docs/101.md", "docs/CONCEPTS.md", "docs/STUDIO_GUIDE.md"]) {
    const sentences = prose(read(f)).split(/(?<=[.!?])\s+|\n{2,}|\n\s*[-*\d]+[.)]?\s+/).map((s) => s.split(/\s+/).filter(Boolean).length).filter((n) => n > 2);
    const avg = sentences.reduce((a, b) => a + b, 0) / sentences.length;
    assert.ok(avg <= 18, `${f}: sentences average ${avg.toFixed(1)} words`);
  }
});

test("the glossary and the core page are folded into Concepts: nothing published links them", () => {
  for (const f of PUBLISHED) assert.ok(!/GLOSSARY\.md|\]\(CORE\.md\)/.test(read(f)), `${f} links a page that Concepts replaces`);
});

test("Quickstart, Concepts and the Studio guide exist and name the next step", () => {
  for (const f of ["docs/101.md", "docs/CONCEPTS.md", "docs/STUDIO_GUIDE.md"]) assert.ok(/\n## Next/.test(read(f)), `${f} ends on a « Next » section`);
});

// The Mac build is an unsigned test build, not distributed (founder, 2026-10-29): no published doc claims it is signed,
// notarized or downloadable until it really is (a release signed with the Apple certificate flips this guard).
test("no published doc claims the Mac build is signed, notarized or ready to download", () => {
  for (const f of [...PUBLISHED, "README.md"]) {
    const md = read(f);
    assert.ok(!/signed and notarized|notarized by Apple/i.test(md), `${f} says the Mac build is signed or notarized`);
    assert.ok(!/Download `?Orazaka\.dmg/i.test(md), `${f} sends readers to a .dmg download`);
  }
});
