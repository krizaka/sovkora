// The workspace job runs `npm ci` in orazaka-apps/ui: when a component repo raises a floor (@krizaka/ui ^3.38.0) and this
// lock still resolves 3.23.0, `npm ci` fails and EVERY pull request of the workspace goes red. This guard says it in a
// test: for each component present, every @krizaka/* range it declares is satisfied by the version the lock resolves.
//
// A member that carries its own pnpm-lock.yaml (orazaka-web-client, "packageManager": "pnpm" in orazaka.workspace.json) is NOT
// pinned by this lock: its bump of @krizaka/ui never waits for a lock here (orazaka#414 / orazaka-web-client#284 deadlocked on it).
// Its floors are checked against ITS lock instead, below.
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { test } from "node:test";

const ui = new URL("../../orazaka-apps/ui/", import.meta.url);
const lock = JSON.parse(readFileSync(new URL("package-lock.json", ui), "utf8"));

const parse = (v) => v.replace(/^[\^~>=\s]+/, "").split(/[-+]/)[0].split(".").map(Number);
/** ^x.y.z / ~x.y.z / x.y.z / >=x.y.z : the lock's version is at least the floor, and in the range's major (caret) or minor (tilde). */
export function satisfies(version, range) {
  const [vM, vm, vp] = parse(version);
  const [rM, rm, rp] = parse(range);
  const atLeast = vM > rM || (vM === rM && (vm > rm || (vm === rm && vp >= rp)));
  if (range.startsWith("^")) return atLeast && (rM > 0 ? vM === rM : vM === 0 && vm === rm);
  if (range.startsWith("~")) return atLeast && vM === rM && vm === rm;
  if (range.startsWith(">=")) return atLeast;
  return vM === rM && vm === rm && vp === rp;
}

test("the range check itself", () => {
  assert.equal(satisfies("3.38.0", "^3.38.0"), true);
  assert.equal(satisfies("3.23.0", "^3.38.0"), false);
  assert.equal(satisfies("4.0.0", "^3.38.0"), false);
  assert.equal(satisfies("0.2.1", "^0.2.1"), true);
  assert.equal(satisfies("0.3.0", "^0.2.1"), false);
});

test("every @krizaka/* floor of a component is met by the workspace lock (npm ci must not fail)", () => {
  const problems = [];
  for (const name of Object.keys(lock.packages).filter((k) => /^[^/]+$/.test(k) && k !== "")) {
    const dir = new URL(`${name}/package.json`, ui);
    if (!existsSync(dir)) continue; // a component not cloned here (CI clones them all)
    const pkg = JSON.parse(readFileSync(dir, "utf8"));
    for (const [dep, range] of Object.entries({ ...pkg.dependencies, ...pkg.devDependencies })) {
      if (!dep.startsWith("@krizaka/") || !/^[\^~>=]*\d/.test(range)) continue;
      // npm nests a copy under the component when its range differs from the hoisted one.
      const resolved = (lock.packages[`${name}/node_modules/${dep}`] ?? lock.packages[`node_modules/${dep}`])?.version;
      if (resolved && !satisfies(resolved, range)) problems.push(`${name} wants ${dep} ${range}, the lock has ${resolved}`);
    }
  }
  assert.deepEqual(problems, [], "regenerate: cd orazaka-apps/ui && npx npm@11 install --legacy-peer-deps --package-lock-only");
});

const manifest = JSON.parse(readFileSync(new URL("../../orazaka.workspace.json", import.meta.url), "utf8"));
const pnpmMembers = manifest.repositories.filter((r) => r.kind === "npm" && r.packageManager === "pnpm");
const workspaces = JSON.parse(readFileSync(new URL("package.json", ui), "utf8")).workspaces;

test("a pnpm member is outside the npm workspace and out of this lock", () => {
  assert.ok(pnpmMembers.length > 0, "orazaka-web-client is declared pnpm in the manifest");
  for (const m of pnpmMembers) {
    const name = m.path.split("/").pop();
    assert.ok(!workspaces.includes(name), `${name} is not an npm workspace member`);
    assert.equal(lock.packages[name], undefined, `${name} is not in package-lock.json`);
  }
});

test("every @krizaka/* floor of a pnpm member is met by its own pnpm-lock.yaml (pnpm install --frozen-lockfile must not fail)", () => {
  const problems = [];
  for (const m of pnpmMembers) {
    const dir = new URL(`../../${m.path}/`, import.meta.url);
    if (!existsSync(new URL("package.json", dir))) continue; // not cloned here (CI clones it)
    const pkg = JSON.parse(readFileSync(new URL("package.json", dir), "utf8"));
    const pnpmLock = readFileSync(new URL("pnpm-lock.yaml", dir), "utf8");
    const importer = pnpmLock.split(/^importers:\n/m)[1]?.split(/^\S/m)[0] ?? "";
    for (const [dep, range] of Object.entries({ ...pkg.dependencies, ...pkg.devDependencies })) {
      if (!dep.startsWith("@krizaka/") || !/^[\^~>=]*\d/.test(range)) continue;
      const block = new RegExp(`^ {6}'?${dep.replace("/", "\\/")}'?:\\n {8}specifier: (\\S+)\\n {8}version: (\\d+\\.\\d+\\.\\d+)`, "m").exec(importer);
      if (!block) problems.push(`${m.name}: ${dep} ${range} is not in its pnpm-lock.yaml`);
      else if (block[1] !== range || !satisfies(block[2], range)) problems.push(`${m.name} wants ${dep} ${range}, its pnpm lock has ${block[2]} for ${block[1]}`);
    }
  }
  assert.deepEqual(problems, [], "regenerate: cd apps/web && pnpm install");
});
