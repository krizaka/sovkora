// The required check « SonarCloud Code Analysis » is posted by the SonarCloud app after a CI scan. On a merge queue
// SHA the app's automatic analysis never runs, so without a CI scan job on merge_group the queue waits forever
// (krizaka-internal#2000). This guards that the scan exists and runs on every event the required checks run on.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";

const ci = readFileSync(new URL("../../.github/workflows/ci.yml", import.meta.url), "utf8");
const sonarJob = ci.split(/\n  (?=[a-z-]+:\n)/).find((block) => /^sonar:/.test(block)) ?? "";

test("ci.yml triggers on merge_group, pull_request and push", () => {
  for (const event of ["merge_group", "pull_request", "push"]) {
    assert.match(ci.split(/\njobs:/)[0], new RegExp(`\\n  ${event}:`), `${event} trigger`);
  }
});

test("a CI Sonar job runs the org scan action with the org secret", () => {
  assert.ok(sonarJob, "a `sonar` job exists in ci.yml");
  assert.match(sonarJob, /name: SonarCloud\b/);
  assert.match(sonarJob, /krizaka\/\.github\/actions\/sonar-scan@/);
  assert.match(sonarJob, /secrets\.SONAR_TOKEN/);
  assert.match(sonarJob, /fetch-depth: 0/);
});

test("the Sonar job is not gated by an event condition that would skip merge_group", () => {
  assert.doesNotMatch(sonarJob, /github\.event_name\s*[!=]=\s*'pull_request'/);
  assert.doesNotMatch(sonarJob, /if:.*pull_request/);
});

// Coverage is measured, not assumed (krizaka-internal#2436): the scripts' tests write lcov in the scripts job, every part uploads
// its report, and the one Sonar job (ADR-029: one project) downloads them all before it scans.
test("the scripts job writes the lcov report of the script tests, and the Sonar job reads every part's coverage", () => {
  const scripts = ci.split(/\n  (?=[a-z-]+:\n)/).find((block) => /^scripts:/.test(block)) ?? "";
  assert.match(scripts, /--experimental-test-coverage/);
  assert.match(scripts, /--test-coverage-exclude='scripts\/test\/\*\*'/);
  assert.match(scripts, /--test-reporter-destination=coverage\/lcov\.info/);
  assert.ok(sonarJob.indexOf("download-artifact") < sonarJob.indexOf("actions/sonar-scan@"), "the coverage is downloaded before the scan");
  assert.match(sonarJob, /pattern: coverage-\*/);
  const p = readFileSync(new URL("../../sonar-project.properties", import.meta.url), "utf8");
  for (const report of ["coverage/lcov.info", "apps/web/coverage/lcov.info", "apps/cli/coverage/lcov.info"]) {
    assert.ok((p.match(/^sonar\.javascript\.lcov\.reportPaths=(.*)$/m)?.[1] ?? "").split(",").includes(report), report);
  }
  assert.match(p, /^sonar\.coverage\.jacoco\.xmlReportPaths=/m);
});

test("sonar-project.properties names the one project of the repository", () => {
  assert.ok(existsSync(new URL("../../sonar-project.properties", import.meta.url)));
  const p = readFileSync(new URL("../../sonar-project.properties", import.meta.url), "utf8");
  assert.match(p, /^sonar\.projectKey=krizaka_sovkora$/m);
  assert.match(p, /^sonar\.organization=krizaka$/m);
});

// One repository, one project (ADR-029): every module's sources are analysed here, none is excluded as « another project's ».
test("every module of modules.json is in the scan's sources", () => {
  const p = readFileSync(new URL("../../sonar-project.properties", import.meta.url), "utf8");
  const sources = (p.match(/^sonar\.sources=(.*)$/m)?.[1] ?? "").split(",").map((x) => x.trim());
  const { modules } = JSON.parse(readFileSync(new URL("../../modules.json", import.meta.url), "utf8"));
  for (const { name, path } of modules) {
    assert.ok(sources.some((s) => path === s || path.startsWith(`${s}/`) || s.startsWith(`${path}/`)), `${name} (${path}) is analysed`);
  }
});
