// node --test scripts/test — a merged migration is never left unapplied on dev unseen: the pure rules of the ledger check.
import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { databasesOf, databasesToRecord, datedBefore, migrationFiles, pending } from "../dev-migrations.mjs";

function workspace() {
  const root = mkdtempSync(join(tmpdir(), "devmig-"));
  for (const [dir, name] of [
    ["infra/migrations", "2026-09-03-a.sql"],
    [".krizaka-java/krizaka-billing/infra/migrations", "2026-10-26-price-catalogue.sql"],
    [".krizaka-java/krizaka-billing/infra/migrations", "README.md"],
  ]) {
    mkdirSync(join(root, dir), { recursive: true });
    writeFileSync(join(root, dir, name), "-- x");
  }
  writeFileSync(join(root, "modules.json"), JSON.stringify({ modules: [{ path: "not/cloned" }] }));
  return root;
}

test("migration files: the workspace's and each cloned component's .sql, keyed from the root", () => {
  assert.deepEqual(migrationFiles(workspace()), [
    ".krizaka-java/krizaka-billing/infra/migrations/2026-10-26-price-catalogue.sql",
    "infra/migrations/2026-09-03-a.sql",
  ]);
});

test("pending: exactly the files the ledger lacks", () => {
  const files = ["a/1.sql", "a/2.sql", "b/3.sql"];
  assert.deepEqual(pending(files, ["a/1.sql", "gone.sql"]), ["a/2.sql", "b/3.sql"]);
  assert.deepEqual(pending(files, files), []);
});

test("baseline selects the files dated strictly before the day, and never an undated one", () => {
  const files = ["x/infra/migrations/2026-10-25-a.sql", "x/infra/migrations/2026-10-26-b.sql", "x/infra/migrations/notes.sql"];
  assert.deepEqual(datedBefore(files, "2026-10-26"), ["x/infra/migrations/2026-10-25-a.sql"]);
});

const TWO = "\\c orazaka_knowledge_db\nALTER TABLE x;\n\\c orazaka_db\nCREATE TABLE y();\n\\c orazaka_knowledge_db\n";

test("databasesOf: each database a file switches to, once, in order", () => {
  assert.deepEqual(databasesOf(TWO), ["orazaka_knowledge_db", "orazaka_db"]);
  assert.deepEqual(databasesOf("-- no switch\nSELECT 1;"), []);
});

test("a migration spanning several databases is recorded only once applied on all of them", () => {
  assert.throws(() => databasesToRecord("m.sql", TWO, []), /spans orazaka_knowledge_db, orazaka_db/);
  assert.throws(() => databasesToRecord("m.sql", TWO, ["orazaka_db"]), /not applied on orazaka_knowledge_db/);
  assert.deepEqual(databasesToRecord("m.sql", TWO, ["orazaka_db", "orazaka_knowledge_db"]), ["orazaka_db", "orazaka_knowledge_db"]);
  assert.deepEqual(databasesToRecord("m.sql", "SELECT 1;", []), []);
});

// ── verify: a migration is never taken as applied without something that proves it (krizaka/orazaka#442)
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defaultDatabase, deriveProbes, judge, probesOf, sections } from "../lib/migration-probes.mjs";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

test("probes: tables, columns, named constraints, indexes, triggers, functions and inserted rows are derived", () => {
  const labels = deriveProbes(`
    -- CREATE TABLE ignored_in_comment (a int);
    CREATE TABLE IF NOT EXISTS audit (id int);
    ALTER TABLE audit ADD COLUMN IF NOT EXISTS note text, ADD CONSTRAINT ck_note CHECK (true);
    CREATE UNIQUE INDEX IF NOT EXISTS uq_audit ON audit (id);
    CREATE OR REPLACE FUNCTION f() RETURNS trigger LANGUAGE plpgsql AS $x$ BEGIN CREATE TABLE inner_body (a int); END $x$;
    CREATE TRIGGER trg BEFORE UPDATE ON audit FOR EACH ROW EXECUTE FUNCTION f();
    INSERT INTO caps (feature_key, v) VALUES ('a.b', '(x)'), ('c.d', 'y') ON CONFLICT (feature_key) DO NOTHING;
  `).map((p) => p.label);
  assert.deepEqual(labels, ["table audit", "table inner_body", "column audit.note", "constraint ck_note", "index uq_audit", "trigger trg", "function f", "row caps feature_key=a.b", "row caps feature_key=c.d"]);
});

test("probes: an inserted row is keyed by its ON CONFLICT columns, else by its string values only", () => {
  const [keyed] = deriveProbes("INSERT INTO e (plan_key, entitlement_key, value) VALUES ('free','studio.x','true') ON CONFLICT (plan_key, entitlement_key) DO NOTHING;");
  assert.equal(keyed.sql, "EXISTS (SELECT 1 FROM e WHERE plan_key::text = 'free' AND entitlement_key::text = 'studio.x')");
  const [loose] = deriveProbes("INSERT INTO p (version, capability, credits) VALUES (2, 'AUDIO', 40) ON CONFLICT DO NOTHING;");
  assert.equal(loose.sql, "EXISTS (SELECT 1 FROM p WHERE capability::text = 'AUDIO')");
  const [dated] = deriveProbes("INSERT INTO b (price_key, effective_from) VALUES ('k', '1970-01-01') ON CONFLICT (price_key, effective_from) DO NOTHING;");
  assert.match(dated.sql, /effective_from::text LIKE '1970-01-01%'/);
});

test("sections: \\c cuts the file, a renamed database keeps its new name, the default comes from the note, the config, the repository", () => {
  assert.deepEqual(sections("SELECT 1;\n\\c orazaka_billing_db\nSELECT 2;").map((s) => s.db), [null, "krizaka_billing_db"]);
  assert.equal(defaultDatabase("infra/migrations/x.sql", "-- Runs in orazaka_knowledge_db."), "orazaka_knowledge_db");
  assert.equal(defaultDatabase(".krizaka-java/krizaka-billing/infra/migrations/x.sql", "SELECT 1"), "krizaka_billing_db");
  assert.equal(defaultDatabase("infra/migrations/x.sql", "SELECT 1", { "infra/migrations/x.sql": "krizaka_users_db" }), "krizaka_users_db");
  assert.equal(defaultDatabase("infra/migrations/x.sql", "SELECT 1"), "orazaka_db");
});

test("a file with nothing derivable has a declared probe, and a declared-only file ignores its statements", () => {
  const f = "infra/migrations/y.sql";
  assert.equal(probesOf(f, "UPDATE t SET a = 1;").length, 0);
  assert.equal(judge(probesOf(f, "UPDATE t SET a = 1;")), "unprobed");
  const config = { probes: { [f]: [{ label: "a is 1", sql: "true" }] } };
  assert.deepEqual(probesOf(f, "UPDATE t SET a = 1;", config).map((p) => p.source), ["declared"]);
  assert.deepEqual(probesOf(f, "CREATE TABLE t (a int);", { ...config, declaredOnly: [f] }).map((p) => p.label), ["a is 1"]);
});

test("judge: present, partial, missing, unprobed", () => {
  assert.equal(judge([{ ok: true }, { ok: true }]), "present");
  assert.equal(judge([{ ok: true }, { ok: false }]), "partial");
  assert.equal(judge([{ ok: false }]), "missing");
  assert.equal(judge([]), "unprobed");
});

test("every migration of this repository has a probe: verify cannot be blind to one", () => {
  const config = JSON.parse(readFileSync(join(ROOT, "scripts/dev-migration-probes.json"), "utf8"));
  const blind = migrationFiles(ROOT).filter((f) => !probesOf(f, readFileSync(join(ROOT, f), "utf8"), config).length);
  assert.deepEqual(blind, [], "add a probe to scripts/dev-migration-probes.json");
});

test("a ledger row written under a former path counts for the same file today (billing, then the components of ADR-029)", () => {
  const moved = ".krizaka-java/krizaka-billing/infra/migrations/2026-10-26-price-catalogue.sql";
  assert.deepEqual(pending([moved], ["krizaka/krizaka-billing/infra/migrations/2026-10-26-price-catalogue.sql"]), []);
  assert.deepEqual(pending([moved], ["krizaka/krizaka-build/krizaka-billing/infra/migrations/2026-10-26-price-catalogue.sql"]), []);
  const studio = "services/studio/infra/migrations/2026-11-01-x.sql";
  assert.deepEqual(pending([studio], ["orazaka-apps/services/orazaka-studio/infra/migrations/2026-11-01-x.sql"]), []);
  const conversation = "services/conversation/sovkora-conversation-service/infra/migrations/2026-11-01-y.sql";
  assert.deepEqual(pending([conversation], ["orazaka-apps/services/orazaka-conversation-service/infra/migrations/2026-11-01-y.sql"]), []);
  assert.deepEqual(pending([moved], []), [moved]);
});
