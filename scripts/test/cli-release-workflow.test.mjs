// node --test scripts/test — the CLI is published as `sovkora` by CI only, and the retired names are deprecated, never unpublished (krizaka-internal#3935).
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

const root = new URL("../../", import.meta.url).pathname;
const read = (p) => readFileSync(join(root, p), "utf8");
const workflow = read(".github/workflows/cli-release.yml");
const pkg = JSON.parse(read("apps/cli/package.json"));

test("the package is the unscoped `sovkora`, with the bin `sovkora` and nothing else", () => {
  assert.equal(pkg.name, "sovkora");
  assert.deepEqual(Object.keys(pkg.bin), ["sovkora"]);
});

test("it publishes from a cli-v tag, with provenance, from CI only", () => {
  assert.match(workflow, /tags: \["cli-v\*"\]/);
  assert.match(workflow, /npm publish --provenance --access public/);
  assert.match(workflow, /id-token: write/);
});

test("the retired packages are deprecated with the rename, never unpublished", () => {
  assert.match(workflow, /npm deprecate "@krizaka\/orazaka-cli" "renamed: use sovkora"/);
  assert.match(workflow, /npm deprecate "orazaka" "renamed: use sovkora"/);
  assert.doesNotMatch(workflow, /npm unpublish/);
});

test("every action is pinned by commit", () => {
  for (const line of workflow.split("\n").filter((l) => l.includes("uses:"))) {
    assert.match(line, /@[0-9a-f]{40}\b/);
  }
});

test("pnpm is set up from the CLI's package.json (the repository root declares no packageManager)", () => {
  assert.match(workflow, /pnpm\/action-setup@[0-9a-f]{40}[^\n]*\n\s+with:\n\s+package_json_file: apps\/cli\/package\.json/);
  assert.match(pkg.packageManager, /^pnpm@\d+\.\d+\.\d+$/);
});

test("the install is filtered to the CLI, as ci.yml does (a bare install walks every app's lockfile)", () => {
  assert.doesNotMatch(workflow, /pnpm install --frozen-lockfile\s*$/m);
  assert.equal(workflow.match(/pnpm install --frozen-lockfile --filter \./g)?.length, 2);
});
