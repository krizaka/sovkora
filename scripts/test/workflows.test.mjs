// node --test scripts/test — workflow files GitHub would refuse. A workflow that is invalid fails in 0 s on
// every push with no job and no log (gpu-smoke.yml did for days: `runner` is not available in a job-level env),
// which is red on main that nobody sees. The contexts below are the ones a job-level `env:` cannot read.
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { test } from "node:test";

const dir = new URL("../../.github/workflows/", import.meta.url);
const FORBIDDEN_IN_JOB_ENV = /\$\{\{[^}]*\b(runner|steps|job)\./;

/** Violations: `file:line` of each expression in a job-level `env:` block (indent 4) that reads a step-only context. */
export function jobEnvViolations(name, text) {
  const out = [];
  const lines = text.split("\n");
  let inJobEnv = false;
  lines.forEach((line, i) => {
    if (/^ {4}env:\s*$/.test(line)) inJobEnv = true;
    else if (inJobEnv && !/^ {5,}\S/.test(line) && line.trim() !== "" && !/^\s*#/.test(line)) inJobEnv = false;
    if (inJobEnv && FORBIDDEN_IN_JOB_ENV.test(line)) out.push(`${name}:${i + 1}: ${line.trim()}`);
  });
  return out;
}

test("the checker sees a runner context in a job-level env", () => {
  const bad = "jobs:\n  a:\n    env:\n      X: ${{ runner.temp }}/x\n    steps:\n      - run: echo\n";
  assert.equal(jobEnvViolations("t.yml", bad).length, 1);
  const ok = "jobs:\n  a:\n    steps:\n      - env:\n          X: ${{ runner.temp }}\n        run: echo\n";
  assert.equal(jobEnvViolations("t.yml", ok).length, 0);
});

test("no workflow reads runner/steps/job in a job-level env", () => {
  const found = readdirSync(dir)
    .filter((f) => f.endsWith(".yml"))
    .flatMap((f) => jobEnvViolations(f, readFileSync(new URL(f, dir), "utf8")));
  assert.deepEqual(found, [], `GitHub refuses these (the run fails in 0 s):\n${found.join("\n")}`);
});

test("docs-sync starts CI on its branch itself (a GITHUB_TOKEN PR starts none) and keeps write scopes on the job", () => {
  const text = readFileSync(new URL("docs-sync.yml", dir), "utf8");
  assert.match(text, /gh workflow run ci\.yml[^\n]*--ref chore\/docs-sync/);
  assert.match(text, /actions: write/);
  assert.doesNotMatch(text.split("jobs:")[0], /^  [a-z-]+: write/m, "no write scope at the top level (Scorecard TokenPermissions)");
});

test("CI holds every change to the generated docs: they are made from this tree and krizaka-java at the imported release", () => {
  const text = readFileSync(new URL("ci.yml", dir), "utf8");
  assert.match(text, /node scripts\/generate-docs\.mjs --check/);
  assert.match(text, /clone -q --depth 1 --branch "v\$version" https:\/\/github\.com\/krizaka\/krizaka-java\.git \.krizaka-java/);
});

/** Duplicate keys inside one job (two `permissions:` after a merge) make GitHub refuse the whole file at startup. */
export function duplicateJobKeys(name, text) {
  const out = [];
  let job = null;
  let seen = new Set();
  text.split("\n").forEach((line, i) => {
    if (/^ {2}[\w-]+:\s*$/.test(line) && text.split("\n").slice(0, i).some((l) => /^jobs:\s*$/.test(l))) {
      job = line.trim();
      seen = new Set();
    } else if (job) {
      const m = /^ {4}([\w-]+):/.exec(line);
      if (m) {
        if (seen.has(m[1])) out.push(`${name}:${i + 1}: duplicate key "${m[1]}" in job ${job}`);
        seen.add(m[1]);
      }
    }
  });
  return out;
}

test("no workflow repeats a key inside a job (a merge once left two `permissions:` in component.yml and broke every repository's CI)", () => {
  const bad = readdirSync(dir).filter((f) => /\.ya?ml$/.test(f)).flatMap((f) => duplicateJobKeys(f, readFileSync(new URL(f, dir), "utf8")));
  assert.deepEqual(bad, []);
  assert.equal(duplicateJobKeys("t.yml", "jobs:\n  a:\n    permissions:\n      contents: read\n    runs-on: x\n    permissions:\n      contents: write\n").length, 1);
});

test("a workflow that mints the app token skips Dependabot pull requests (Dependabot reads no secrets: the job failed on « private-key must be set »)", () => {
  const bad = readdirSync(dir)
    .filter((f) => /\.ya?ml$/.test(f))
    .map((f) => [f, readFileSync(new URL(f, dir), "utf8")])
    .filter(([, t]) => /on:\s*\n\s+pull_request:/.test(t) && t.includes("create-github-app-token") && !t.includes("dependabot[bot]"))
    .map(([f]) => f);
  assert.deepEqual(bad, []);
});


// --test-force-exit ends a test file's process as soon as the tests it knows of have finished. Under load, a file whose
// tests are registered inside describe() blocks is cut short before the later blocks register, and the run reports the
// tests it saw as passing: CI run 42410859306 (2026-11-07) ran the CI-COST guard on 13 of the 21 workflows, « fail 0 ».
// Reproduced locally (1 of 6 parallel runs short by 17 tests; 8 of 8 complete without the flag). The step's
// timeout-minutes and --test-timeout bound a hang; a silently partial run is worse than a hang.
test("no workflow runs node --test with --test-force-exit (it drops tests silently)", () => {
  const offenders = readdirSync(dir)
    .filter((n) => /\.ya?ml$/.test(n))
    .flatMap((n) => (/node --test\b[^\n]*(\\\n[^\n]*)*--test-force-exit/.test(readFileSync(new URL(n, dir), "utf8")) ? [n] : []));
  assert.deepEqual(offenders, []);
});
