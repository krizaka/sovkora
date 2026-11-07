// The @krizaka/* copies this app runs must be the ones @krizaka/ui was built against. A stale root copy of
// @krizaka/tokens (an older one kept by a cached node_modules) makes @krizaka/ui fail at import
// ("Cannot read properties of undefined (reading 'length')": its marks come from tokens/native).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { pathToFileURL } from "node:url";

const root = new URL("../", import.meta.url);
const read = (path) => JSON.parse(readFileSync(new URL(path, root), "utf8"));
const appPackage = read("package.json");
// Resolved as the app resolves it: nested under the app, or hoisted to the workspace root.
const uiPackageUrl = pathToFileURL(createRequire(new URL("package.json", root)).resolve("@krizaka/ui/package.json"));
const uiPackage = JSON.parse(readFileSync(uiPackageUrl, "utf8"));

/** The three numbers of a caret range's floor ("^3.14.0" -> [3, 14, 0]). */
const floor = (range) => {
  const m = /^\^?(\d+)\.(\d+)\.(\d+)$/.exec(range);
  assert.ok(m, `not a caret range: ${range}`);
  return m.slice(1).map(Number);
};
const compare = (a, b) => a[0] - b[0] || a[1] - b[1] || a[2] - b[2];
const version = (v) => floor(v);

/** Does `version` satisfy the caret `range` (same major; for 0.x, same minor)? */
function satisfiesCaret(v, range) {
  const f = floor(range);
  const x = version(v);
  if (compare(x, f) < 0) return false;
  if (f[0] > 0) return x[0] === f[0];
  if (f[1] > 0) return x[0] === 0 && x[1] === f[1];
  return x[0] === 0 && x[1] === 0 && x[2] === f[2];
}

test("the @krizaka/* packages @krizaka/ui depends on resolve to a copy inside its range", () => {
  const fromUi = createRequire(uiPackageUrl);
  const stale = [];
  for (const [name, range] of Object.entries(uiPackage.dependencies ?? {})) {
    if (!name.startsWith("@krizaka/")) continue;
    const copy = JSON.parse(readFileSync(fromUi.resolve(`${name}/package.json`), "utf8"));
    if (!satisfiesCaret(copy.version, range)) stale.push(`${name} ${copy.version} (@krizaka/ui needs ${range})`);
  }
  assert.deepEqual(stale, [], "a stale copy is installed: remove node_modules and install again");
});

test("the app declares @krizaka/tokens at least as high as @krizaka/ui needs (no lower root copy can be kept)", () => {
  const needed = uiPackage.dependencies["@krizaka/tokens"];
  const declared = appPackage.dependencies["@krizaka/tokens"];
  assert.ok(compare(floor(declared), floor(needed)) >= 0, `declared ${declared}, @krizaka/ui needs ${needed}`);
});
