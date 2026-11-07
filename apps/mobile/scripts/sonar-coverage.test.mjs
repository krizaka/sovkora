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

test("the CI analysis runs, and reads that report", () => {
  assert.match(read(".github/workflows/ci.yml"), /^\s+sonar: true\b/m);
  const properties = read("sonar-project.properties");
  assert.match(properties, /^sonar\.projectKey=krizaka_orazaka-mobile-client$/m);
  assert.match(properties, /^sonar\.javascript\.lcov\.reportPaths=coverage\/lcov\.info$/m);
});
