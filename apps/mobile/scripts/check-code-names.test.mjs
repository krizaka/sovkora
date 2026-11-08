import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import { displayedTexts, guardPath } from "./check-code-names.mjs";

const config = JSON.parse(readFileSync(new URL("../app.json", import.meta.url), "utf8"));

test("the name under the icon is the commercial name; the identifiers keep the code name", () => {
  assert.equal(config.expo.name, "Sovkora");
  assert.equal(config.expo.ios.bundleIdentifier, "com.krizaka.orazaka");
  assert.equal(config.expo.android.package, "com.krizaka.orazaka");
  assert.equal(config.expo.scheme, "orazaka");
  assert.equal(config.expo.slug, "orazaka-mobile");
});

test("the displayed fields are the name and the system prompts, in words a customer reads", () => {
  const shown = displayedTexts(config);
  assert.equal(shown.name, "Sovkora");
  assert.ok(shown.prompts.length >= 2);
  for (const text of [shown.name, ...shown.prompts, ...shown.infoPlist]) {
    assert.doesNotMatch(text, /orazaka|yomizaka|akinaza|kachizaka/i);
  }
  assert.ok(shown.prompts.every((p) => p.startsWith("Sovkora")));
});

test("the guard is found in a hoisted install, above the app's own node_modules", () => {
  const top = mkdtempSync(join(tmpdir(), "guard-path-"));
  try {
    const bin = join(top, "node_modules", "@krizaka", "org", "bin");
    mkdirSync(bin, { recursive: true });
    writeFileSync(join(bin, "krizaka-code-names.mjs"), "");
    const app = join(top, "orazaka-mobile-client");
    mkdirSync(app);
    assert.equal(guardPath(app), join(bin, "krizaka-code-names.mjs"));
  } finally {
    rmSync(top, { recursive: true, force: true });
  }
});
