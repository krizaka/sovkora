// node --test scripts/test — the provider-served video model is routed by data, and its rows say so everywhere.
import assert from "node:assert/strict";
import { test } from "node:test";
import { readFileSync } from "node:fs";

const read = (p) => readFileSync(new URL(`../../${p}`, import.meta.url), "utf8");
const MODEL = "wan2-2-t2v-a14b";

test("the migration and the dev data give the model the same provider, category and honest registry facts", () => {
  for (const file of ["infra/migrations/2026-10-15-do-wan-a14b.sql", "infra/deploy/dev-data.sql"]) {
    const sql = read(file);
    assert.match(sql, new RegExp(`'${MODEL}', 'Wan 2\\.2 T2V A14B \\(serverless\\)', 'video', NULL, 'do-inference'`), file);
    assert.match(sql, /runs_on = 'provider'/, file);
    assert.match(sql, /runtime = 'DigitalOcean serverless inference'/, file);
    assert.match(sql, /licence = 'Apache-2\.0'/, file);
  }
});

test("the Mac worker file carries the provider key without ever printing it", () => {
  const sh = read("scripts/gpu-dev.sh");
  assert.match(sh, /INFERENCE_KEY="\$\(secret AI_INFERENCE_KEY_ALL \|\| secret AI_INFERENCE_KEY\)"/);
  assert.match(sh, /handle\.write\(f"AI_INFERENCE_KEY=/);
  assert.doesNotMatch(sh, /echo[^\n]*INFERENCE_KEY/);
});

test("the worker's provider-model suite runs in CI", () => {
  assert.match(read(".github/workflows/ci.yml"), /test_remote_video\.py/);
});
