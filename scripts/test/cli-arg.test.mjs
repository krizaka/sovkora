import assert from "node:assert/strict";
import { test } from "node:test";

import { arg } from "../lib/cli-arg.mjs";

test("a flag's value is the word after it; an absent flag gives the fallback", () => {
  const argv = ["node", "script.mjs", "--out", "/tmp/x", "--only", "chat"];
  assert.equal(arg("out", "default", argv), "/tmp/x");
  assert.equal(arg("only", undefined, argv), "chat");
  assert.equal(arg("host", "droplet", argv), "droplet");
});

test("a flag in first position is not read, and a trailing flag has no value", () => {
  assert.equal(arg("out", "fb", ["--out", "x"]), "fb");
  assert.equal(arg("out", "fb", ["node", "s.mjs", "--out"]), undefined);
});
