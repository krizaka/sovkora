// The closed-login notice speaks English and French; English is the default.
import { test } from "node:test";
import assert from "node:assert/strict";
import { closedCopy, pickLocale } from "../core/copy/closed.ts";

test("English by default, French when the browser prefers it", () => {
  assert.equal(pickLocale(null), "en");
  assert.equal(pickLocale("de-DE,de;q=0.9"), "en");
  assert.equal(pickLocale("fr-CA,fr;q=0.9,en;q=0.8"), "fr");
  assert.equal(pickLocale("en-US,en;q=0.9,fr;q=0.8"), "en");
});

test("both languages say the same things", () => {
  assert.deepEqual(Object.keys(closedCopy.en), Object.keys(closedCopy.fr));
  for (const lang of ["en", "fr"] as const) for (const v of Object.values(closedCopy[lang])) assert.ok(v.length > 0);
});

test("the disabled sign-in says when it opens, in both languages", () => {
  assert.notEqual(closedCopy.en.signInBanner, closedCopy.fr.signInBanner);
  assert.match(closedCopy.en.signInBanner, /dev services/);
});

test("a closed login shows the real form disabled, not only a notice", async () => {
  const { readFileSync } = await import("node:fs");
  const page = readFileSync(new URL("../../app/console/login/page.tsx", import.meta.url), "utf8");
  const form = readFileSync(new URL("../../app/console/login/LoginForm.tsx", import.meta.url), "utf8");
  assert.doesNotMatch(page, /LoginClosed/);
  assert.match(page, /<LoginForm\s+preview=/);
  assert.match(form, /disabled=\{pending \|\| Boolean\(preview\)\}/);
  // The preview never reaches next-auth: submitting is a no-op.
  assert.match(form, /if \(preview\) return;/);
});
