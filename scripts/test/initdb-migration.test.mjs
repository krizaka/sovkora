// node --test scripts/test — a fresh database (the components' infra/initdb) equals a migrated one
// (infra/migrations): every column, constraint, index and table a migration adds is in the initdb DDL.
// The initdb files live in the modules that own them (modules.json `initdb`).
import assert from "node:assert/strict";
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const manifest = { repositories: JSON.parse(readFileSync(join(root, "modules.json"), "utf8")).modules };

/** The database of each part of a migration (`\c db`) -> the objects it adds. */
export function objectsAdded(sql) {
  const parts = {};
  let db = null;
  const text = sql.replace(/--[^\n]*/g, "");
  for (const chunk of text.split(/^\\c\s+(\w+)\s*$/m)) {
    if (/^\w+$/.test(chunk.trim()) && !chunk.includes(" ")) { db = chunk.trim(); parts[db] = []; continue; }
    if (!db) continue;
    for (const m of chunk.matchAll(/ALTER TABLE (\w+)\s+ADD COLUMN IF NOT EXISTS (\w+)/gi)) parts[db].push({ kind: "column", table: m[1], name: m[2] });
    for (const m of chunk.matchAll(/ALTER TABLE (\w+)\s+ADD CONSTRAINT (\w+)/gi)) parts[db].push({ kind: "constraint", table: m[1], name: m[2] });
    for (const m of chunk.matchAll(/CREATE (?:UNIQUE )?INDEX IF NOT EXISTS (\w+)/gi)) parts[db].push({ kind: "index", name: m[1] });
    for (const m of chunk.matchAll(/CREATE TABLE IF NOT EXISTS (\w+)\s*\(([\s\S]*?)\n\);/gi)) {
      parts[db].push({ kind: "table", name: m[1] });
      for (const c of m[2].matchAll(/^\s+(?:CONSTRAINT (\w+)|(\w+)\s+[A-Z])/gim)) {
        parts[db].push(c[1] ? { kind: "constraint", table: m[1], name: c[1] } : { kind: "column", table: m[1], name: c[2] });
      }
    }
  }
  return parts;
}

/** The text of every initdb file of the workspace, and of the Flyway baselines that replace them (krizaka-internal#2255). */
function initdbText() {
  const all = [];
  for (const r of manifest.repositories) {
    for (const f of r.initdb ?? []) {
      const p = join(root, r.path, "infra", "initdb", f);
      if (existsSync(p)) all.push(readFileSync(p, "utf8").replace(/--[^\n]*/g, ""));
    }
    for (const f of flywayMigrations(join(root, r.path))) all.push(readFileSync(f, "utf8").replace(/--[^\n]*/g, ""));
  }
  return all.join("\n");
}

/**
 * Every Flyway migration of a repository: `src/main/resources/db/<location>/V<n>__*.sql` of the repository or of one of
 * its modules (a multi-module repository keeps a context's baseline in its module, e.g. orazaka-ai-engine's
 * `orazaka-persistence-app/src/main/resources/db/governance`, krizaka-internal#2255).
 */
export function flywayMigrations(repo) {
  const found = [];
  const walk = (dir, depth) => {
    if (!existsSync(dir) || depth > 3) return;
    const db = join(dir, "src", "main", "resources", "db");
    if (existsSync(db)) {
      for (const location of readdirSync(db, { withFileTypes: true }).filter((e) => e.isDirectory())) {
        for (const f of readdirSync(join(db, location.name)).filter((n) => /^V\d+__.*\.sql$/.test(n)).sort()) {
          found.push(join(db, location.name, f));
        }
      }
    }
    for (const e of readdirSync(dir, { withFileTypes: true })) {
      if (e.isDirectory() && !/^(\.|node_modules$|target$|src$|build$)/.test(e.name)) walk(join(dir, e.name), depth + 1);
    }
  };
  walk(repo, 0);
  return found;
}

test("the Flyway migrations of a repository and of its modules are read, whatever their location", () => {
  const repo = mkdtempSync(join(tmpdir(), "flyway-"));
  const put = (path, text) => {
    mkdirSync(dirname(join(repo, path)), { recursive: true });
    writeFileSync(join(repo, path), text);
  };
  put("src/main/resources/db/migration/V1__root.sql", "");
  put("app-module/src/main/resources/db/governance/V1__governance_baseline.sql", "");
  put("app-module/src/main/resources/db/governance/notes.sql", "");
  put("app-module/src/test/resources/db/other/V1__test_only.sql", "");
  put("app-module/target/classes/db/governance/V1__copy.sql", "");
  const got = flywayMigrations(repo).map((f) => f.slice(repo.length + 1)).sort();
  assert.deepEqual(got, [
    "app-module/src/main/resources/db/governance/V1__governance_baseline.sql",
    "src/main/resources/db/migration/V1__root.sql",
  ]);
});

test("the migration parser reads columns, constraints, indexes and tables", () => {
  const got = objectsAdded(`\\c a_db
ALTER TABLE t ADD COLUMN IF NOT EXISTS x INT;
ALTER TABLE t ADD CONSTRAINT uq UNIQUE (x);
CREATE INDEX IF NOT EXISTS ix ON t(x);
CREATE TABLE IF NOT EXISTS n (
    id SERIAL PRIMARY KEY,
    CONSTRAINT nq UNIQUE (id)
);
`);
  assert.deepEqual(got.a_db.map((o) => `${o.kind}:${o.name}`), ["column:t.x".replace("t.", ""), "constraint:uq", "index:ix", "table:n", "column:id", "constraint:nq"]);
});

for (const file of ["2026-10-26-knowledge-access.sql"]) {
  test(`${file}: the initdb DDL carries everything the migration adds`, (t) => {
    const sql = readFileSync(join(root, "infra", "migrations", file), "utf8");
    const initdb = initdbText();
    const added = Object.values(objectsAdded(sql)).flat();
    assert.ok(added.length >= 8, "the migration was not parsed");
    // Not cloned (a run outside the workspace): nothing to compare. In CI the components are cloned from main.
    if (initdb.trim() === "") {
      t.skip("the components' initdb is not cloned");
      return;
    }
    const missing = [];
    for (const o of added) {
      const re = o.kind === "table" || o.kind === "index" || o.kind === "constraint"
        ? new RegExp(`\\b${o.name}\\b`) : new RegExp(`\\b${o.name}\\b\\s+[A-Za-z]`);
      if (!re.test(initdb)) missing.push(`${o.kind} ${o.table ? o.table + "." : ""}${o.name}`);
    }
    assert.deepEqual(missing, [], "in the migration but not in infra/initdb");
  });
}

test("2026-10-27-capability-billable-none.sql: the CHECK list is the one of the job service's initdb (NONE included)", (t) => {
  const listOf = (sql) => [...sql.replace(/--[^\n]*/g, "").matchAll(/CHECK \(billable_capability IN \(([^)]*)\)\)/g)].map((m) => m[1].replace(/\s/g, ""));
  const migration = listOf(readFileSync(join(root, "infra", "migrations", "2026-10-27-capability-billable-none.sql"), "utf8"));
  assert.equal(migration.length, 1, "the migration widens the check once");
  assert.match(migration[0], /'NONE'/);
  const initdb = initdbText();
  if (initdb.trim() === "") {
    t.skip("the components' initdb is not cloned");
    return;
  }
  // The same constraint is declared twice in a fresh database (the column, then the external checks block): both equal.
  assert.deepEqual([...new Set(listOf(initdb))], migration);
});
