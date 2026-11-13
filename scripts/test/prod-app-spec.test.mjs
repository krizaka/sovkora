// node --test scripts/test — the pilot's prod app spec stays honest (krizaka-internal#2132, ADR SOV-105):
// no plain secret, no dev host, no web app, no video worker, no GPU relay; a prod edge that refuses dev keys.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import { prodSpecProblems } from "../check-prod-spec.mjs";

const spec = readFileSync(new URL("../../infra/deploy/sovkora-prod.app.yaml", import.meta.url), "utf8");

/** The spec with one line replaced; the replaced line must exist, so a test never passes on a typo. */
function edited(from, to) {
  assert.ok(spec.includes(from), `the spec no longer holds: ${from}`);
  return spec.replace(from, to);
}

test("the prod spec as committed has no problem", () => {
  assert.deepEqual(prodSpecProblems(spec), []);
});

test("a plain secret value is refused, typed SECRET or not", () => {
  const typed = edited('type: SECRET, value: "${users-db.PASSWORD}"', 'type: SECRET, value: "hunter2-in-clear"');
  assert.match(prodSpecProblems(typed).join("\n"), /IDENTITY_DB_PASSWORD.*plain/);
  const untyped = edited('IDENTITY_JWT_SECRET, scope: RUN_TIME, type: SECRET, value: "__PROD_IDENTITY_JWT_SECRET__"', 'IDENTITY_JWT_SECRET, scope: RUN_TIME, value: "abc"');
  assert.match(prodSpecProblems(untyped).join("\n"), /IDENTITY_JWT_SECRET.*not a SECRET/);
  const leakedKey = edited('value: "https://api.sovkora.com"', 'value: "oz_live_abcdefghijklmnopqrstuvwxyz0123456789ABCD"');
  assert.match(prodSpecProblems(leakedKey).join("\n"), /looks like a credential/);
});

test("a dev host or a dev resource is refused", () => {
  assert.match(prodSpecProblems(edited("- domain: api.sovkora.com", "- domain: dev.sovkora.com")).join("\n"), /dev/);
  assert.match(prodSpecProblems(edited("value: broker-prod.sovkora.com", "value: broker-dev.orazaka.com")).join("\n"), /dev/);
  assert.match(prodSpecProblems(edited("value: classpath:db/migration }", "value: classpath:db/migration,classpath:db/dev }")).join("\n"), /dev/);
});

test("the web app, the video worker and the GPU relay are refused", () => {
  const web = edited("services:\n", "services:\n  - name: web\n    source_dir: apps/web\n    dockerfile_path: apps/web/Dockerfile\n");
  assert.match(prodSpecProblems(web).join("\n"), /web/);
  const video = edited('value: "validation" }', 'value: "validation hosted-video" }');
  assert.match(prodSpecProblems(video).join("\n"), /video worker/);
  const relay = edited("services:\n", "services:\n  - name: gpu-relay\n    source_dir: infra/deploy/gpu-relay\n");
  assert.match(prodSpecProblems(relay).join("\n"), /gpu-relay/);
});

test("the prod edge declares prod keys only, one address, and is the only routed component", () => {
  assert.match(prodSpecProblems(edited('AUTH_API_KEY_ENVIRONMENT, scope: RUN_TIME, value: "prod"', 'AUTH_API_KEY_ENVIRONMENT, scope: RUN_TIME, value: "staging"')).join("\n"), /AUTH_API_KEY_ENVIRONMENT/);
  assert.match(prodSpecProblems(edited("        name: edge\n", "        name: core\n")).join("\n"), /routed/);
  assert.match(prodSpecProblems(edited("region: tor", "region: nyc")).join("\n"), /region/);
});
