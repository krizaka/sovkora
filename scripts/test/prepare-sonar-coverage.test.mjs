// node --test scripts/test — a coverage report that never arrives left whole folders at 0.0 % in SonarCloud (krizaka-internal#2525).
import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";

import { prepareSonarCoverage } from "../prepare-sonar-coverage.mjs";

const PROPERTIES = [
  "sonar.javascript.lcov.reportPaths=coverage-reports/normalized/*.lcov.info",
  "sonar.python.coverage.reportPaths=coverage-reports/normalized/*.coverage.xml",
  "sonar.coverage.jacoco.xmlReportPaths=**/target/site/jacoco/jacoco.xml",
].join("\n");

const skipped = { java: "skipped", web: "skipped", mobile: "skipped", cli: "skipped", scripts: "skipped", "packs-schema": "skipped", "python-coverage": "skipped" };

function workspace(files) {
  const root = mkdtempSync(join(tmpdir(), "sonar-cov-"));
  writeFileSync(join(root, "sonar-project.properties"), PROPERTIES);
  for (const [path, body] of Object.entries(files)) {
    mkdirSync(dirname(join(root, path)), { recursive: true });
    writeFileSync(join(root, path), body);
  }
  return root;
}

test("a part that succeeded and left no report fails the scan, and says which", () => {
  const root = workspace({});
  const { errors } = prepareSonarCoverage({ root, results: { ...skipped, web: "success" } });
  assert.match(errors[0], /web/);
  assert.match(errors[0], /coverage-reports\/report-web\/lcov\.info/);
});

test("an empty report is a missing report", () => {
  const root = workspace({ "coverage-reports/report-cli/lcov.info": "" });
  const { errors } = prepareSonarCoverage({ root, results: { ...skipped, cli: "success" } });
  assert.match(errors.join("\n"), /cli/);
});

test("a part that did not run needs no report", () => {
  const { errors } = prepareSonarCoverage({ root: workspace({}), results: skipped });
  assert.deepEqual(errors, []);
});

test("every expected Python directory must have reported", () => {
  const root = workspace({ "coverage-reports/report-py-workers__media/coverage.xml": "<coverage/>" });
  const { errors } = prepareSonarCoverage({ root, results: { ...skipped, "python-coverage": "success" } });
  assert.ok(errors.some((e) => e.includes("packs/worker-common")));
  assert.ok(!errors.some((e) => e.includes("workers/media")));
});

test("lcov paths become repository-relative, from a relative or an absolute SF", () => {
  const root = workspace({
    "coverage-reports/report-web/lcov.info": "SF:src/a.ts\nDA:1,1\nend_of_record\n",
    "coverage-reports/report-cli/lcov.info": "TN:\nSF:/RUNNER/apps/cli/src/b.ts\nDA:1,0\nend_of_record\n",
  });
  const absolute = join(root, "apps/cli/src/b.ts");
  writeFileSync(join(root, "coverage-reports/report-cli/lcov.info"), `SF:${absolute}\nDA:1,0\nend_of_record\n`);
  const { errors } = prepareSonarCoverage({ root, results: { ...skipped, web: "success", cli: "success" } });
  assert.deepEqual(errors, []);
  assert.match(readFileSync(join(root, "coverage-reports/normalized/web.lcov.info"), "utf8"), /^SF:apps\/web\/src\/a\.ts$/m);
  assert.match(readFileSync(join(root, "coverage-reports/normalized/cli.lcov.info"), "utf8"), /^SF:apps\/cli\/src\/b\.ts$/m);
});

test("a Cobertura report is rewritten to repository-relative filenames", () => {
  const xml = '<coverage><sources><source>/runner/packs/worker-common</source></sources><classes><class filename="worker_loop.py"/></classes></coverage>';
  const root = workspace({ "coverage-reports/report-py-packs__worker-common/coverage.xml": xml });
  prepareSonarCoverage({ root, results: { ...skipped, "python-coverage": "success" } });
  const out = readFileSync(join(root, "coverage-reports/normalized/packs__worker-common.coverage.xml"), "utf8");
  assert.match(out, /filename="packs\/worker-common\/worker_loop\.py"/);
});

test("a path the properties declare and nothing produced fails the scan", () => {
  const root = workspace({ "coverage-reports/report-web/lcov.info": "SF:src/a.ts\nend_of_record\n" });
  writeFileSync(join(root, "sonar-project.properties"), "sonar.javascript.lcov.reportPaths=apps/web/coverage/lcov.info\n");
  const { errors } = prepareSonarCoverage({ root, results: { ...skipped, web: "success" } });
  assert.match(errors.join("\n"), /apps\/web\/coverage\/lcov\.info/);
});

test("the repository's own sonar-project.properties declares only what the pipeline produces", () => {
  const properties = readFileSync(new URL("../../sonar-project.properties", import.meta.url), "utf8");
  assert.match(properties, /^sonar\.javascript\.lcov\.reportPaths=coverage-reports\/normalized\/\*\.lcov\.info$/m);
  assert.match(properties, /^sonar\.python\.coverage\.reportPaths=coverage-reports\/normalized\/\*\.coverage\.xml$/m);
});

test("ci.yml uploads each part's report under the name the check reads, and the sonar job runs the check", () => {
  const ci = readFileSync(new URL("../../.github/workflows/ci.yml", import.meta.url), "utf8");
  for (const name of ["report-web", "report-mobile", "report-cli", "report-scripts", "report-packs"]) {
    assert.ok(ci.includes(`name: ${name}\n`), `${name} is uploaded`);
  }
  assert.match(ci, /name: report-py-\$\{\{ matrix\.slug \}\}/);
  for (const dir of ["workers/media", "packs/worker-common", "packs/profile-photo-check/worker"]) {
    assert.ok(ci.includes(`dir: ${dir}\n`), `${dir} is in the Python coverage matrix`);
  }
  assert.match(ci, /node scripts\/prepare-sonar-coverage\.mjs/);
  const sonar = ci.slice(ci.indexOf("\n  sonar:"), ci.indexOf("\n  workspace:"));
  const needs = sonar.match(/needs: \[([^\]]*)\]/)[1];
  for (const job of ["java", "web", "mobile", "cli", "scripts", "packs-schema", "python-coverage"]) assert.ok(needs.includes(job), `sonar needs ${job}`);
});
