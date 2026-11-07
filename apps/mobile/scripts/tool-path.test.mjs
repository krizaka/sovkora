import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { toolPath } from "./tool-path.mjs";

test("a program is resolved to an absolute path inside the given folders, never through PATH", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "tool-path-"));
  const bin = path.join(dir, "ffmpeg");
  fs.writeFileSync(bin, "#!/bin/sh\n", { mode: 0o755 });
  fs.writeFileSync(path.join(dir, "plain"), "x", { mode: 0o644 });
  assert.equal(toolPath("ffmpeg", [dir]), bin);
  assert.throws(() => toolPath("plain", [dir]), /not found/);
  assert.throws(() => toolPath("missing", [dir]), /not found/);
  const before = process.env.PATH;
  process.env.PATH = dir;
  try {
    assert.throws(() => toolPath("ffmpeg", ["/nonexistent"]), /not found/, "PATH is ignored");
  } finally {
    process.env.PATH = before;
  }
});

test("a name that is a path is refused", () => {
  assert.throws(() => toolPath("../evil"), /not a program name/);
  assert.throws(() => toolPath("a/b"), /not a program name/);
});

test("the recording scripts spawn only resolved programs", () => {
  for (const f of ["frame-clips.mjs", "record-story.mjs"]) {
    const src = fs.readFileSync(new URL(`./${f}`, import.meta.url), "utf8");
    assert.doesNotMatch(src, /(?:execFileSync|spawn)\(\s*"[a-z0-9]+"/, `${f} spawns a program by bare name`);
  }
});
