import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "..", "..", ".github", "workflows");
const read = (f) => fs.readFileSync(path.join(dir, f), "utf8");

// The live checks read the deployed commit where the product is served (#231): dev.sovkora.com since the commercial names
// (2026-11-06; dev.sovkora.com no longer answers). A check that reads a page instead never sees the deployed commit.
test("web-live.yml runs against dev.sovkora.com and reads the deployed commit from /api/healthz, in both of its jobs", () => {
  const y = read("web-live.yml");
  assert.equal((y.match(/E2E_BASE_URL: https:\/\/dev\.sovkora\.com/g) ?? []).length, 2);
  assert.equal((y.match(/\$E2E_BASE_URL\/api\/healthz/g) ?? []).length, 2);
  assert.doesNotMatch(y, /orazaka\.com/);
  assert.doesNotMatch(y, /curl -fsS "\$E2E_BASE_URL\/" \|/);
});
