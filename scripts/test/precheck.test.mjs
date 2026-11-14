// node --test scripts/test — precheck.json (read by krizaka-precheck before a push) must name only what exists (krizaka-internal#3537).
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { test } from "node:test";

const root = new URL("../../", import.meta.url);
const precheck = JSON.parse(readFileSync(new URL("precheck.json", root), "utf8"));

test("every check names itself, its paths and a command, and only the schema's keys", () => {
  assert.deepEqual(Object.keys(precheck).filter((k) => !["$schema", "budgetSeconds", "checks"].includes(k)), []);
  assert.ok(Number.isInteger(precheck.budgetSeconds) && precheck.budgetSeconds <= 3600);
  const allowed = ["name", "paths", "run", "cwd", "heavy", "timeoutSeconds"];
  for (const check of precheck.checks) {
    assert.deepEqual(Object.keys(check).filter((k) => !allowed.includes(k)), [], check.name);
    assert.ok(check.name && check.paths.length > 0 && check.run.length > 0, check.name);
  }
});

test("every folder a check runs in, and every Maven module it builds, exists", () => {
  for (const check of precheck.checks) {
    if (check.cwd) assert.ok(existsSync(new URL(`${check.cwd}/`, root)), `${check.name}: ${check.cwd}`);
    const at = check.run.indexOf("-pl");
    if (at >= 0) {
      for (const module of check.run[at + 1].split(",")) {
        assert.ok(existsSync(new URL(`${module}/pom.xml`, root)), `${check.name}: ${module}`);
      }
    }
  }
});
