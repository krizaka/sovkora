// node --test scripts/test — the facts a Studio form's model picker shows (time, price) live in the catalogue, once:
// a fresh database (job-service Flyway V1) and an existing one (this repository's migration) must say the same thing,
// and a model that is hosted must carry a price while one on the operator's hardware must not.
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { test } from "node:test";

const migration = readFileSync(new URL("../../infra/migrations/2026-10-17-model-picker-facts.sql", import.meta.url), "utf8");
// The job service's catalogue moved from infra/initdb to its Flyway V1 baseline (krizaka-internal#2255).
const initdbUrl = new URL("../../services/job/sovkora-job-service/src/main/resources/db/migration/V1__job_baseline.sql", import.meta.url);
const initdb = existsSync(initdbUrl) ? readFileSync(initdbUrl, "utf8") : null;

const facts = (sql) =>
  [...sql.matchAll(/UPDATE orazaka_models SET (typical_seconds = \d+(?:, price_cents = \d+)?) WHERE model_name = '([^']+)'/g)].map(
    (m) => `${m[2]}: ${m[1]}`,
  );

test("the migration states a typical time for the models the media Studios offer", () => {
  const names = facts(migration).map((line) => line.split(":")[0]);
  for (const model of ["flux-2-klein-4b", "z-image-turbo", "v1-5-pruned-emaonly", "stable-diffusion-3.5-large", "wan2-2-t2v-a14b"]) {
    assert.ok(names.includes(model), `${model} has no typical_seconds`);
  }
});

test("a fresh database and an existing one carry the same facts", { skip: initdb === null && "job-service not checked out" }, () => {
  assert.deepEqual(facts(initdb).sort(), facts(migration).sort());
});

test("only a hosted model has a price", () => {
  for (const line of facts(migration)) {
    const [model, rest] = line.split(": ");
    const hosted = ["stable-diffusion-3.5-large", "wan2-2-t2v-a14b"].includes(model);
    assert.equal(/price_cents/.test(rest), hosted, `${model}: ${rest}`);
  }
});

test("the migration is idempotent: columns are added once, the constraint is replaced", () => {
  assert.match(migration, /ADD COLUMN IF NOT EXISTS typical_seconds/);
  assert.match(migration, /ADD COLUMN IF NOT EXISTS price_cents/);
  assert.match(migration, /DROP CONSTRAINT IF EXISTS ck_models_measures/);
});
