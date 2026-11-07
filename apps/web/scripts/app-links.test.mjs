// node --test scripts — the repository passes its own app-links rule: every shareable web route is claimed, every claimed
// path is a route, the two files exist, and the committed routes file is current.
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { test } from "node:test";

test("check-app-links passes on this repository (web mode)", () => {
  const r = spawnSync(process.execPath, ["scripts/check-app-links.mjs"], { encoding: "utf8" });
  assert.equal(r.status, 0, r.stdout + r.stderr);
});
