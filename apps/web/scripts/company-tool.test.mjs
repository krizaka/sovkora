import assert from "node:assert/strict";
import { test } from "node:test";

import { companyTool } from "./company-tool.mjs";

test("a company tool is in the folder kz-config names, asked at the user's ~/.local/bin", () => {
  const calls = [];
  const exec = (bin, args) => (calls.push([bin, ...args]), "/kz/tools\n");
  assert.equal(companyTool("secret.sh", { exec, home: "/h" }), "/kz/tools/secret.sh");
  assert.deepEqual(calls, [["/h/.local/bin/kz-config", "tools"]]);
});

test("an empty answer is an error that says what to set, never a relative path", () => {
  assert.throws(() => companyTool("secret.sh", { exec: () => "", home: "/h" }), /KRIZAKA_TOOLS/);
});
