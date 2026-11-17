// node --test scripts/test — the company tools folder is resolved, never hard-coded.
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { toolPathOf, toolsDir } from "../lib/tools.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..");

test("KRIZAKA_TOOLS wins and kz-config is not called", () => {
  const exec = () => assert.fail("must not be called");
  assert.equal(toolsDir({ environ: { KRIZAKA_TOOLS: "/x/tools" }, exec }), "/x/tools");
  assert.equal(toolPathOf("secret.sh", { environ: { KRIZAKA_TOOLS: "/x/tools" }, exec }), "/x/tools/secret.sh");
});

test("without KRIZAKA_TOOLS, `kz-config tools` answers (trimmed)", () => {
  const calls = [];
  const exec = (file, args) => (calls.push([file, args]), "/y/tools\n");
  assert.equal(toolsDir({ environ: {}, exec, home: "/h" }), "/y/tools");
  assert.deepEqual(calls, [["/h/.local/bin/kz-config", ["tools"]]]);
});

test("a blank KRIZAKA_TOOLS falls through to kz-config", () => {
  assert.equal(toolsDir({ environ: { KRIZAKA_TOOLS: "  " }, exec: () => "/z\n" }), "/z");
});

test("a clear error when neither answers", () => {
  assert.throws(() => toolsDir({ environ: {}, exec: () => { throw new Error("ENOENT"); } }), /set KRIZAKA_TOOLS/);
  assert.throws(() => toolsDir({ environ: {}, exec: () => "\n" }), /answered nothing/);
});

test("no file of the repository names the retired tools folder (code and docs)", () => {
  const files = execFileSync("git", ["ls-files"], { cwd: ROOT, encoding: "utf8" }).split("\n").filter(Boolean);
  const needle = [".krizaka", "worktrees"].join("-");
  const hits = files.filter((f) => {
    try {
      return readFileSync(join(ROOT, f), "utf8").includes(needle);
    } catch {
      return false;
    }
  });
  assert.deepEqual(hits, []);
});

test("no file reads the legacy qa.env path", () => {
  const files = execFileSync("git", ["ls-files", "apps", "scripts", "docs"], { cwd: ROOT, encoding: "utf8" }).split("\n").filter(Boolean);
  const hits = files.filter((f) => !f.endsWith("tools.test.mjs") && readFileSync(join(ROOT, f), "utf8").includes("krizaka/qa.env"));
  assert.deepEqual(hits, []);
});
