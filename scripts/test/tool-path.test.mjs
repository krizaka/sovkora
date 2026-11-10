// node --test scripts/test — the programs the workspace scripts spawn are found in fixed folders, never through PATH.
import assert from "node:assert/strict";
import { chmodSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { after, test } from "node:test";
import { TOOL_FOLDERS, toolPath, toolPathOrNull } from "../lib/tool-path.mjs";

const root = mkdtempSync(path.join(tmpdir(), "tool-path-"));
const first = path.join(root, "first");
const second = path.join(root, "second");
mkdirSync(first);
mkdirSync(second);
writeFileSync(path.join(second, "prog"), "#!/bin/sh\n");
chmodSync(path.join(second, "prog"), 0o755);
writeFileSync(path.join(first, "plain"), "not executable");
mkdirSync(path.join(first, "dir"));
after(() => rmSync(root, { recursive: true, force: true }));

test("the first executable file of that name in the folders, in order", () => {
  assert.equal(toolPath("prog", [first, second]), path.join(second, "prog"));
});

test("not found, not executable or a folder: toolPath throws, toolPathOrNull answers null", () => {
  for (const name of ["missing", "plain", "dir"]) {
    assert.throws(() => toolPath(name, [first, second]), /not found/, name);
    assert.equal(toolPathOrNull(name, [first, second]), null, name);
  }
});

test("a name is a program name, never a path", () => {
  for (const name of ["../prog", "/bin/sh", "a/b", "", "prog;rm"]) {
    assert.throws(() => toolPath(name, [second]), /program name/, name);
  }
});

test("the fixed folders are absolute, PATH is never read, and node's own folder comes first (npm, npx)", () => {
  assert.ok(TOOL_FOLDERS.every((d) => path.isAbsolute(d)));
  assert.equal(TOOL_FOLDERS[0], path.dirname(process.execPath));
  const saved = process.env.PATH;
  process.env.PATH = second;
  try {
    assert.equal(toolPathOrNull("prog"), null);
  } finally {
    process.env.PATH = saved;
  }
});

test("macOS keeps htpasswd in /usr/sbin, so the persona seed finds it there", () => {
  assert.ok(TOOL_FOLDERS.includes("/usr/sbin"));
});
