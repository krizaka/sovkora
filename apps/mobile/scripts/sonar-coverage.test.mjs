// The coverage SonarCloud shows is the jest coverage of `validate`, read by the CI analysis (krizaka-internal#2436): the
// automatic analysis cannot import a report, which left « Measure has not been found » on the project.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const read = (file) => readFileSync(new URL(`../${file}`, import.meta.url), "utf8");

test("validate runs jest with an lcov report", () => {
  const { scripts } = JSON.parse(read("package.json"));
  assert.match(scripts.validate, /npm run test:coverage\b/);
  assert.match(scripts["test:coverage"], /^jest --coverage\b/);
  assert.match(scripts["test:coverage"], /--coverageReporters=lcov\b/);
});

test("the one CI analysis (ADR-029) uploads that report and reads it", () => {
  assert.match(read("../../.github/workflows/ci.yml"), /name: report-mobile\n(?:.*\n)*?\s+path: apps\/mobile\/coverage\/lcov\.info/);
  const properties = read("../../sonar-project.properties");
  assert.match(properties, /^sonar\.projectKey=krizaka_sovkora$/m);
  // scripts/prepare-sonar-coverage.mjs normalizes report-mobile into this folder
  assert.match(properties, /^sonar\.javascript\.lcov\.reportPaths=coverage-reports\/normalized\/\*\.lcov\.info$/m);
});
