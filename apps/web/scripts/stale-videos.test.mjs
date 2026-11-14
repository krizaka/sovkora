import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { ALERT_KEY, alertFor, MAX_AGE_DAYS, staleVideos } from "./stale-videos-lib.mjs";

const v = (flow, capturedAt) => ({
  platform: "web",
  flow,
  seconds: 5,
  capturedAt,
  files: { webm: "", mp4: "", poster: "" },
});
const NOW = new Date("2026-11-20T00:00:00Z");

test("the watch and the page's helper use the same age", () => {
  const source = readFileSync(new URL("../src/features/landing/utils/app-videos.ts", import.meta.url), "utf8");
  assert.equal(Number(/MAX_AGE_DAYS = (\d+);/.exec(source)?.[1]), MAX_AGE_DAYS);
});

test("a stale recording raises the alert, naming every stale id", () => {
  const alert = alertFor(NOW, [
    v("old-en", "2026-11-05T00:00:00Z"),
    v("fresh-en", "2026-11-19T00:00:00Z"),
    v("older-fr", "2026-10-31T10:47:29Z"),
  ]);
  assert.equal(alert.state, "red");
  assert.deepEqual(alert.ids, ["web-old-en", "web-older-fr"]);
  assert.match(alert.body, /web-old-en/);
  assert.match(alert.body, /web-older-fr/);
  assert.doesNotMatch(alert.body, /web-fresh-en/);
});

test("fresh recordings clear the alert", () => {
  const alert = alertFor(NOW, [v("a-en", "2026-11-07T00:00:01Z"), v("b-en", "2026-11-20T00:00:00Z")]);
  assert.equal(alert.state, "green");
  assert.deepEqual(alert.ids, []);
});

test("the cutoff is exactly the age: 14 days old is still fresh, one second more is stale", () => {
  assert.deepEqual(staleVideos(NOW, [v("edge-en", "2026-11-06T00:00:00Z")]), []);
  assert.equal(staleVideos(NOW, [v("edge-en", "2026-11-05T23:59:59Z")]).length, 1);
});

test("the alert is one key for the product", () => {
  assert.equal(ALERT_KEY, "stale-videos/sovkora");
});

// A bug means a missing guard: the watch's first run failed with ERR_MODULE_NOT_FOUND (date-fns) because the workflow ran the
// report without installing the web app's dependencies (run 43609446294, krizaka-internal#3626). A bare package the scripts import
// must be installed by a step that runs before the report.
test("the watch workflow installs the web app's locked dependencies before it runs the report", () => {
  const read = (rel) => readFileSync(new URL(rel, import.meta.url), "utf8");
  const workflow = read("../../../.github/workflows/stale-videos.yml").replace(/^\s*#.*$/gm, "");
  const bare = [read("./stale-videos-lib.mjs"), read("./stale-videos-report.mjs")]
    .flatMap((src) => [...src.matchAll(/from "([^".][^"]*)"/g)].map((m) => m[1]))
    .filter((spec) => !spec.startsWith("node:"));
  if (bare.length === 0) {
    return;
  }
  const install = workflow.indexOf("pnpm install --frozen-lockfile");
  const report = workflow.indexOf("stale-videos-report.mjs");
  assert.ok(install !== -1, `the scripts import ${bare.join(", ")} but the workflow installs nothing`);
  assert.ok(install < report, "the install step must come before the report");
});
