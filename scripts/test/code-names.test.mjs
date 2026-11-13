// The code-name ratchet (krizaka-internal#2684): the count of retired names may only fall.
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { globToRegExp, lowered, main } from "../check-code-names.mjs";

const NAME = ["ora", "zaka"].join(""); // spelled in two halves so this file is not its own finding
const NAMING = "| Id | x |\n|:--|:--|\n| K1 | applied migrations |\n";

function repo(files, { allow = { paths: [], tokens: [] }, ratchet } = {}) {
  const dir = mkdtempSync(join(tmpdir(), "code-names-"));
  const all = { "docs/NAMING.md": NAMING, "scripts/code-names.allow.json": JSON.stringify(allow), ...files };
  if (ratchet) all["scripts/code-names.ratchet.json"] = JSON.stringify({ counts: ratchet });
  for (const [path, text] of Object.entries(all)) {
    mkdirSync(dirname(join(dir, path)), { recursive: true });
    writeFileSync(join(dir, path), text);
  }
  execFileSync("git", ["init", "-q"], { cwd: dir });
  execFileSync("git", ["add", "-A"], { cwd: dir });
  return dir;
}
function run(dir, ...args) {
  const err = console.error, log = console.log, out = [];
  console.error = (m) => out.push(m);
  console.log = (m) => out.push(m);
  try { return { code: main(args, dir), out: out.join("\n") }; } finally { console.error = err; console.log = log; rmSync(dir, { recursive: true, force: true }); }
}
const ratchetOf = (dir) => JSON.parse(readFileSync(join(dir, "scripts/code-names.ratchet.json"), "utf8")).counts;

test("a new file holding the name outside the allow-list raises the count: red", () => {
  const { code, out } = run(repo({ "apps/a.ts": "const a = 1;\n", "apps/b.ts": `// ${NAME}\n` }, { ratchet: { apps: 0 } }));
  assert.equal(code, 1);
  assert.match(out, /apps: 1 hits, the ratchet allows 0/);
});

test("a path carrying the name counts, case-insensitively, and so does a product name of the old repositories", () => {
  const { code } = run(repo({ [`apps/${NAME.toUpperCase()}-web/x.ts`]: "ok\n", "engine/t.java": "// Yomizaka\n" }, { ratchet: { apps: 1, engine: 0 } }));
  assert.equal(code, 1);
});

test("the same count, or a lower one, passes", () => {
  assert.equal(run(repo({ "apps/b.ts": `// ${NAME}\n` }, { ratchet: { apps: 1 } })).code, 0);
  const { code, out } = run(repo({ "apps/b.ts": "clean\n" }, { ratchet: { apps: 3 } }));
  assert.equal(code, 0);
  assert.match(out, /--update/);
});

test("a folder the ratchet does not list tolerates nothing", () => {
  assert.equal(run(repo({ "new/b.ts": `// ${NAME}\n` }, { ratchet: {} })).code, 1);
});

test("lockfiles are not read", () => {
  assert.equal(run(repo({ "apps/pnpm-lock.yaml": `${NAME}\n`, "workers/uv.lock": `${NAME}\n` }, { ratchet: {} })).code, 0);
});

test("an allowed path is kept, path and content; an allowed token is removed from the line", () => {
  const allow = { paths: [{ glob: "infra/migrations/*.sql", reason: "applied", row: "K1" }], tokens: [{ token: `${NAME}-dev-x`, reason: "ingress", row: "K1" }] };
  const files = { [`infra/migrations/${NAME}.sql`]: `${NAME}\n`, "apps/c.ts": `url(${NAME}-dev-x)\n` };
  assert.equal(run(repo(files, { allow, ratchet: {} })).code, 0);
});

test("a dead allow entry fails, path or token", () => {
  const dead = { paths: [{ glob: "nothing/**", reason: "r", row: "K1" }], tokens: [] };
  const a = run(repo({ "apps/a.ts": "x\n" }, { allow: dead, ratchet: {} }));
  assert.equal(a.code, 1);
  assert.match(a.out, /dead allowance/);
  const token = { paths: [], tokens: [{ token: "gone", reason: "r", row: "K1" }] };
  assert.equal(run(repo({ "apps/a.ts": "x\n" }, { allow: token, ratchet: {} })).code, 1);
});

test("an allowance without a row in the mapping table, or without a reason, fails", () => {
  const live = { "infra/migrations/a.sql": `${NAME}\n` };
  const noRow = { paths: [{ glob: "infra/migrations/*.sql", reason: "r", row: "K9" }], tokens: [] };
  assert.match(run(repo(live, { allow: noRow, ratchet: {} })).out, /row K9 is not in the mapping table/);
  const noReason = { paths: [{ glob: "infra/migrations/*.sql", row: "K1" }], tokens: [] };
  assert.match(run(repo(live, { allow: noReason, ratchet: {} })).out, /no reason/);
});

test("--update lowers the counts and never raises one", () => {
  const dir = repo({ "apps/a.ts": `// ${NAME}\n// ${NAME}\n`, "engine/b.ts": "clean\n", "docs/c.md": `${NAME}\n` }, { ratchet: { apps: 5, engine: 2 } });
  const { code } = (() => { const c = main(["--update"], dir); return { code: c }; })();
  assert.equal(code, 1, "docs is not in the ratchet: still a failure, never silently added");
  assert.deepEqual(ratchetOf(dir), { apps: 2 });
  rmSync(dir, { recursive: true, force: true });
});

test("--update creates the ratchet from today's counts when there is none", () => {
  const dir = repo({ "apps/a.ts": `// ${NAME}\n` });
  assert.equal(main(["--update"], dir), 0);
  assert.deepEqual(ratchetOf(dir), { apps: 1 });
  rmSync(dir, { recursive: true, force: true });
});

test("lowered never exceeds the old count", () => {
  assert.deepEqual(lowered({ a: 9, b: 1 }, { a: 4, b: 3 }, false), { a: 4, b: 1 });
});

test("globs: * stays in a folder, ** crosses folders", () => {
  assert.ok(globToRegExp("services/*/*/db/V1__*.sql").test("services/job/svc/db/V1__x.sql"));
  assert.ok(!globToRegExp("infra/*.sql").test("infra/a/b.sql"));
  assert.ok(globToRegExp("infra/**/*.sql").test("infra/a/b.sql"));
});

test("the repository itself is at or under its ratchet, with live allowances", () => {
  const root = join(dirname(fileURLToPath(import.meta.url)), "../..");
  const { code, out } = (() => { const log = console.log, err = console.error, buf = []; console.log = console.error = (m) => buf.push(m); try { return { code: main([], root), out: buf.join("\n") }; } finally { console.log = log; console.error = err; } })();
  assert.equal(code, 0, out);
});
