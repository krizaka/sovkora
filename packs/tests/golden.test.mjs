import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { checkRatchet, discover } from "./golden/harness.mjs";

const root = fileURLToPath(new URL("..", import.meta.url));
const ratchet = JSON.parse(readFileSync(new URL("../golden-ratchet.json", import.meta.url), "utf8"));

test("every non-exempt Studio ships 3–5 schema-valid fictional golden cases", () => {
  const suites = discover(root);
  assert.ok(suites.length > 0, "no Studio goldens discovered");
});

/** The exceptions recorded at a commit, or null when it has none. `./` makes the path relative to this folder, not to the repository root. */
function exceptionsAt(base) {
  const files = execFileSync("git", ["ls-tree", "--name-only", base, "--", "golden-ratchet.json"], {
    cwd: root,
    encoding: "utf8",
  });
  if (!files.trim()) return null;
  return JSON.parse(execFileSync("git", ["show", `${base}:./golden-ratchet.json`], { cwd: root, encoding: "utf8" }))
    .exceptions;
}

test("golden coverage exceptions only decrease against the PR base", () => {
  // CI supplies the immutable PR base SHA; local runs still reject any new exception.
  const base = process.env.GOLDEN_BASE_SHA;
  if (base) assert.match(base, /^[0-9a-f]{40}$/);
  const previous = (base && exceptionsAt(base)) || [
    "document-validation/compliance-check",
    "document-validation/document-authenticity",
  ];
  checkRatchet(ratchet.exceptions, previous);
});

test("the PR base's exceptions are read from this folder, below the repository root", () => {
  const head = execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  assert.deepEqual(
    exceptionsAt(head),
    ratchet.exceptions,
    "HEAD's committed golden-ratchet.json, read through the PR-base path",
  );
});
