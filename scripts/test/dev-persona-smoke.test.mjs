import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { verdict, webVerdict } from "../dev-persona-smoke.mjs";

test("the web sign-in is checked too: a session without the persona's e-mail (the web cannot reach the router) fails", () => {
  assert.equal(webVerdict(200, { user: { email: "eric@example.com" } }, "eric@example.com").ok, true);
  assert.equal(webVerdict(200, {}, "eric@example.com").ok, false);
  assert.equal(webVerdict(200, null, "eric@example.com").ok, false);
  assert.match(webVerdict(200, {}, "eric@example.com").message, /router/);
  assert.equal(webVerdict(502, { user: { email: "eric@example.com" } }, "eric@example.com").ok, false);
});

test("the smoke fails on the 500 of the duplicated persona and on a tokenless 200", () => {
  assert.equal(verdict(500, false).ok, false);
  assert.match(verdict(500, false).message, /HTTP 500/);
  assert.equal(verdict(200, false).ok, false);
  assert.equal(verdict(200, true).ok, true);
});

test("the workflow runs the smoke after the redeploy and on a schedule, with the persona secrets by name", () => {
  const wf = readFileSync(new URL("../../.github/workflows/dev-persona-smoke.yml", import.meta.url), "utf8");
  assert.match(wf, /workflow_run:[\s\S]*Dev redeploy/);
  assert.match(wf, /schedule:/);
  assert.match(wf, /APP_QA_ORAZAKA_PERSONA_EMAIL: \$\{\{ secrets\.APP_QA_ORAZAKA_PERSONA_EMAIL \}\}/);
  assert.match(wf, /node scripts\/dev-persona-smoke\.mjs eric/);
});

test("the smoke targets the dev address by default, never the retired host (every run failed with « fetch failed »)", () => {
  const source = readFileSync(new URL("../dev-persona-smoke.mjs", import.meta.url), "utf8");
  assert.doesNotMatch(source, /www\.orazaka\.com/);
  assert.match(source, /ORAZAKA_WEB_URL \?\? "https:\/\/dev\.sovkora\.com"/);
  assert.match(source, /ORAZAKA_EDGE_URL \?\? "https:\/\/dev\.sovkora\.com\/edge"/);
});
