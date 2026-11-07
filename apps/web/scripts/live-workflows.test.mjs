import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", ".github", "workflows");
const read = (f) => fs.readFileSync(path.join(dir, f), "utf8");

// Since the www rule dev.orazaka.com answers 308 to www: a live check that reads the page or the SEO files on dev never
// sees the deployed commit. The evidence behind the dev-preview badge is read where the product is served (#231).
for (const f of ["live-smoke.yml", "dev-preview-live.yml"]) {
  test(`${f} runs against www.orazaka.com and reads the deployed commit from /api/healthz`, () => {
    const y = read(f);
    assert.match(y, /E2E_BASE_URL: https:\/\/www\.orazaka\.com/);
    assert.match(y, /\$E2E_BASE_URL\/api\/healthz/);
    assert.doesNotMatch(y, /--base https:\/\/dev\.orazaka\.com|--host https:\/\/dev\.orazaka\.com/);
    assert.doesNotMatch(y, /curl -fsS "\$E2E_BASE_URL\/" \|/);
  });
}
