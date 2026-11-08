// Guard: no tool, workflow or web source names the retired orazaka.com hosts — dev.sovkora.com is the dev address.
// Historic text (changelogs) stays; this guard scans only operational source files.
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");

const RETIRED_HOSTS = ["www.orazaka.com", "admin.orazaka.com"];

/** Directories to scan (relative to repo root). */
const SCAN_DIRS = [
  "scripts",
  "apps/web/scripts",
  "apps/web/src",
  ".github/workflows",
  "infra/deploy",
];

/** Files whose retired-host references are allowed (guard itself, or fixed in a concurrent PR). */
const ALLOWED_FILES = new Set([
  "scripts/test/dev-domain-hosts.test.mjs",
  // Fixed in https://github.com/krizaka/sovkora/pull/649 — will be gone once that PR lands.
  "scripts/dev-persona-smoke.mjs",
]);

/** Extensions to scan. */
const SCAN_EXTS = new Set([".mjs", ".ts", ".tsx", ".sh", ".yml", ".yaml"]);

function walk(dir, found = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, found);
    } else if (SCAN_EXTS.has(entry.name.slice(entry.name.lastIndexOf(".")))) {
      found.push(full);
    }
  }
  return found;
}

test("no source file names a retired orazaka.com host", () => {
  const hits = [];
  for (const dir of SCAN_DIRS) {
    const abs = join(root, dir);
    if (!statSync(abs, { throwIfNoEntry: false })?.isDirectory()) continue;
    for (const file of walk(abs)) {
      const rel = relative(root, file).replace(/\\/g, "/");
      if (ALLOWED_FILES.has(rel)) continue;
      const text = readFileSync(file, "utf8");
      for (const host of RETIRED_HOSTS) {
        if (text.includes(host)) {
          hits.push(`${rel}: contains "${host}"`);
        }
      }
    }
  }
  assert.deepEqual(hits, [], `Retired orazaka.com hosts found:\n${hits.join("\n")}`);
});
