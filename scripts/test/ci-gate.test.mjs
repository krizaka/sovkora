// node --test scripts/test — the required `workspace` gate carries the product's checks; Sonar reports on its own check.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

test("a red Sonar quality gate on old debt does not turn the required workspace gate red (krizaka-internal#2598)", () => {
  const ci = readFileSync(new URL("../../.github/workflows/ci.yml", import.meta.url), "utf8");
  const gate = ci.slice(ci.indexOf("\n  workspace:"));
  const needs = gate.match(/needs: \[([^\]]*)\]/)[1].split(",").map((s) => s.trim());
  assert.ok(needs.includes("java"));
  assert.ok(!needs.includes("sonar"), "workspace must not need the sonar job");
});
