// The pack workers import worker-common/ (krizaka/orazaka-packs#96) through a path relative to their own directory:
// /app/orazaka-packs/<pack>/worker -> /app/orazaka-packs/worker-common. The dev image must therefore carry the whole
// orazaka-packs repository, and start each worker from its worker directory inside it.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const dockerfile = readFileSync(new URL("../../infra/deploy/Dockerfile", import.meta.url), "utf8");
const runServices = readFileSync(new URL("../../infra/deploy/run-services.sh", import.meta.url), "utf8");

test("the dev image copies the whole packs folder (worker-common travels with the workers)", () => {
  assert.match(dockerfile, /cp -r packs \/out\/orazaka-packs/);
  assert.doesNotMatch(dockerfile, /cp -r packs\/[\w-]+\/worker /, "never copy a single pack's worker directory");
});

test("a pack worker starts from <app>/orazaka-packs/<pack>/worker, two levels under the repository root", () => {
  const starts = [...runServices.matchAll(/cd "\$\{ORAZAKA_APP_DIR:-\/app\}\/orazaka-packs\/([\w-]+)\/worker"/g)];
  assert.ok(starts.length >= 1, "the validation worker is started from its pack's worker directory");
});

test("the prod pilot installs document-validation only: /app/packs-pilot holds that one pack (krizaka-internal#2132)", () => {
  const copied = [...dockerfile.matchAll(/cp -r packs\/([\w-]+) \/out\/packs-pilot\//g)].map((m) => m[1]);
  assert.deepEqual(copied, ["document-validation"]);
  const prod = readFileSync(new URL("../../infra/deploy/sovkora-prod.app.yaml", import.meta.url), "utf8");
  assert.match(prod, /_PACKS_SOURCES, scope: RUN_TIME, value: \/app\/packs-pilot \}/);
});
