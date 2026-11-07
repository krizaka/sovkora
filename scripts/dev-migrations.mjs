#!/usr/bin/env node
/**
 * Which merged `infra/migrations/*.sql` are not yet applied on the dev cluster (docs/DEV_DATABASE.md).
 *
 *   node scripts/dev-migrations.mjs check [--root <workspace>] [--env-file <f>]   # exit 1 and list the pending files
 *   node scripts/dev-migrations.mjs record <file> [<file>…] [--on db1,db2]        # after applying one by hand: write it in the ledger (refused unless verify finds its effect)
 *   node scripts/dev-migrations.mjs unrecord <file> [<file>…]                     # take a wrong row out
 *   node scripts/dev-migrations.mjs baseline --before YYYY-MM-DD                  # files dated before are recorded, only if verify finds every one of them
 *   node scripts/dev-migrations.mjs verify [--only <text>]                        # probe each file's effect on dev (read only); exit 1 if a recorded file is missing / partial, or a file has no probe
 *
 * The ledger is one table, `dev_migration_ledger`, in orazaka_db (created on first use, never anything else written).
 * A file is keyed `<path from the repository root>`, e.g. `services/studio/infra/migrations/<file>.sql`; krizaka-billing's are read from the
 * krizaka-java checkout (`.krizaka-java/krizaka-billing/infra/migrations/...`). A row written under a former path counts (MOVED).
 * A file that spans several databases (`\c <db>` lines, e.g. ADR-091's knowledge-access) is recorded once, for all of them:
 * `--on` must name every database it touches (refused otherwise), and the ledger keeps them in its `databases` column.
 * Nothing here ever applies a migration: applying stays a person's act (dry run in a rolled-back transaction, then commit).
 * `dev-deploy.mjs apply` runs the check and refuses to roll out while a file is pending (--allow-pending-migrations to pass).
 * Secrets: DATABASE_HOST, DATABASE_PASSWORD_ORAZAKA_DB through secret.sh @orazaka — never printed.
 */
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { devSecrets, envFileFromArgs } from "./lib/dev-secrets.mjs";
import { judge, probesOf } from "./lib/migration-probes.mjs";
import { byText } from "./lib/source-scan.mjs";
import { toolPathOrNull } from "./lib/tool-path.mjs";

const HERE = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const LEDGER_DB = "orazaka_db";
/** The krizaka-java components Sovkora's dev databases also carry, read from the krizaka-java checkout. */
const NESTED_COMPONENTS = [".krizaka-java/krizaka-billing"];
/**
 * Ledger rows written under a former path count for the same file at its path of today, longest prefix first: krizaka-billing
 * was a repository, then a folder of krizaka-build, then of krizaka-java; the Sovkora components were clones of the workspace
 * until they became folders of the one repository (ADR-029).
 */
export const MOVED = [
  ["krizaka/krizaka-build/krizaka-billing/", ".krizaka-java/krizaka-billing/"],
  ["krizaka/krizaka-billing/", ".krizaka-java/krizaka-billing/"],
  ["orazaka-apps/services/orazaka-conversation-service/", "services/conversation/sovkora-conversation-service/"],
  ["orazaka-apps/services/orazaka-studio/", "services/studio/"],
];
const today = (f) => {
  const m = MOVED.find(([from]) => f.startsWith(from));
  return m ? m[1] + f.slice(m[0].length) : f;
};
const DATE = /^\d{4}-\d{2}-\d{2}-/;

/** Every migration file of the workspace and of its component repositories, keyed by path from the root, sorted. */
export function migrationFiles(root, manifest = JSON.parse(readFileSync(join(root, "modules.json"), "utf8"))) {
  const dirs = ["", ...manifest.modules.map((r) => r.path), ...NESTED_COMPONENTS];
  const out = [];
  for (const d of dirs) {
    const rel = join(d, "infra/migrations");
    const abs = join(root, rel);
    if (!existsSync(abs)) continue;
    for (const f of readdirSync(abs).filter((n) => n.endsWith(".sql"))) out.push(join(rel, f));
  }
  return out.sort(byText);
}

/** The databases a migration file switches to with `\c <db>` (in order, once each); a file without any belongs to its own database. */
export function databasesOf(sql) {
  const out = [];
  for (const m of sql.matchAll(/^\s*\\c(?:onnect)?\s+(\S+)/gm)) if (!out.includes(m[1])) out.push(m[1]);
  return out;
}

/** The databases to write for a file: a multi-database file needs `--on` covering each of them; returns the list to keep. */
export function databasesToRecord(file, sql, on) {
  const needed = databasesOf(sql);
  if (needed.length > 1 && !on.length) throw new Error(`${file} spans ${needed.join(", ")}: record it with --on ${needed.join(",")} once applied on each`);
  const missing = needed.filter((d) => !on.includes(d));
  if (needed.length > 1 && missing.length) throw new Error(`${file} is not recorded: not applied on ${missing.join(", ")} (--on ${on.join(",")})`);
  return on.length ? on : needed;
}

/** The files the ledger does not hold. */
export function pending(files, ledger) {
  const done = new Set(ledger.map(today));
  return files.filter((f) => !done.has(f));
}

/** Files dated strictly before `date` (their name starts with YYYY-MM-DD-). */
export function datedBefore(files, date) {
  return files.filter((f) => {
    const m = f.split("/").pop().match(DATE);
    return m && f.split("/").pop().slice(0, 10) < date;
  });
}

function psqlBinary() {
  if (process.env.PSQL) return process.env.PSQL;
  const onDisk = toolPathOrNull("psql");
  if (onDisk) return onDisk;
  const cellar = "/opt/homebrew/Cellar/libpq";
  if (existsSync(cellar)) {
    const v = readdirSync(cellar).sort().pop();
    if (v) return join(cellar, v, "bin/psql");
  }
  throw new Error("psql not found (set PSQL)");
}

/** Runs `sql` as the owner role of `db` (role and database share a name; the key is DATABASE_PASSWORD_<DB>). `strict: false` keeps going after an error. */
function psql(env, sql, db = LEDGER_DB, strict = true) {
  const conn = `host=${env.DATABASE_HOST} port=${env.DATABASE_PORT || 25060} dbname=${db} user=${db} sslmode=require`;
  const r = spawnSync(psqlBinary(), [conn, "-v", `ON_ERROR_STOP=${strict ? 1 : 0}`, "-X", "-q", "-At"], {
    input: sql,
    encoding: "utf8",
    env: { ...process.env, PGPASSWORD: env[`DATABASE_PASSWORD_${db.toUpperCase()}`] },
  });
  if (r.status !== 0) throw new Error(`psql on ${db} failed: ${r.stderr.replaceAll(env.DATABASE_HOST ?? "\0", "<host>").trim()}`);
  return r.stdout;
}

/** Declared probes for what the statements cannot tell (UPDATE, column types, DO blocks): scripts/dev-migration-probes.json, keyed by file. */
function probeConfig() {
  const f = join(HERE, "scripts/dev-migration-probes.json");
  return existsSync(f) ? JSON.parse(readFileSync(f, "utf8")) : {};
}

/**
 * Runs the probes of `files` on dev, read only: [{ file, status, probes: [{ db, label, ok }] }].
 * A probe that errors (a table that is not there) counts as not satisfied.
 */
export function verifyFiles(root, files, env, config = probeConfig()) {
  const perFile = files.map((file) => ({ file, probes: probesOf(file, readFileSync(join(root, file), "utf8"), config) }));
  const byDb = new Map();
  for (const f of perFile) for (const p of f.probes) byDb.set(p.db, [...(byDb.get(p.db) ?? []), p]);
  for (const [db, items] of byDb) {
    const out = psql(env, items.map((p, n) => `SELECT ${n}, (${p.sql});`).join("\n"), db, false);
    const seen = new Map(out.split("\n").filter(Boolean).map((l) => l.split("|")));
    items.forEach((p, n) => (p.ok = seen.get(String(n)) === "t"));
  }
  return perFile.map(({ file, probes }) => ({ file, status: judge(probes), probes }));
}

const ENSURE = "CREATE TABLE IF NOT EXISTS dev_migration_ledger (file TEXT PRIMARY KEY, recorded_at TIMESTAMPTZ NOT NULL DEFAULT now());\nALTER TABLE dev_migration_ledger ADD COLUMN IF NOT EXISTS databases TEXT NOT NULL DEFAULT '';";
const quote = (s) => `'${s.replaceAll("'", "''")}'`;

function ledger(env) {
  return psql(env, `${ENSURE}\nSELECT file FROM dev_migration_ledger;`).split("\n").filter(Boolean);
}

/** @param {string[]|{file: string, databases?: string[]}[]} entries */
function record(env, entries) {
  if (!entries.length) return;
  const rows = entries.map((e) => (typeof e === "string" ? { file: e, databases: [] } : e));
  const values = rows.map((r) => `(${quote(r.file)},${quote((r.databases ?? []).join(","))})`).join(",");
  psql(env, `${ENSURE}\nINSERT INTO dev_migration_ledger (file, databases) VALUES ${values} ON CONFLICT (file) DO UPDATE SET databases = EXCLUDED.databases WHERE EXCLUDED.databases <> '';`);
}

/**
 * Nothing is written in the ledger on faith (the one-off baseline of 2026-10-26 took 8 files that were not there as applied):
 * a file is recorded only when its probes find its effect on dev, and a file without any probe cannot be recorded.
 */
export function requireApplied(root, files, env) {
  const bad = verifyFiles(root, files, env).filter((r) => r.status !== "present");
  if (bad.length) {
    const why = bad.map((r) => `  ${r.status.padEnd(8)} ${r.file}${r.status === "unprobed" ? " (add a probe to scripts/dev-migration-probes.json)" : `: ${r.probes.filter((p) => !p.ok).map((p) => p.label).slice(0, 3).join("; ")}`}`);
    throw new Error(`not recorded, the effect is not on dev:\n${why.join("\n")}\nApply it (dry run in a rolled-back transaction first), then record.`);
  }
}

/** The pending files on dev, for a given workspace root. */
export function pendingOnDev(root, env) {
  return pending(migrationFiles(root), ledger(env));
}

function main(argv) {
  let { envFile, args } = envFileFromArgs(argv);
  const take = (flag) => {
    const i = args.indexOf(flag);
    if (i < 0) return null;
    const v = args[i + 1];
    args = [...args.slice(0, i), ...args.slice(i + 2)];
    return v;
  };
  const root = resolve(take("--root") ?? HERE);
  const before = take("--before");
  const on = (take("--on") ?? "").split(",").filter(Boolean);
  const [command = "check", ...rest] = args;
  const env = devSecrets({ envFile });
  if (command === "check") {
    const todo = pendingOnDev(root, env);
    if (!todo.length) return console.log("dev migrations: none pending");
    console.error(`dev migrations: ${todo.length} merged but not recorded as applied on dev:\n${todo.map((f) => `  ${f}`).join("\n")}`);
    console.error("Apply each as its database's owner role (dry run in a rolled-back transaction first, docs/DEV_DATABASE.md), then: node scripts/dev-migrations.mjs record <file>");
    process.exitCode = 1;
  } else if (command === "record") {
    const known = new Set(migrationFiles(root));
    for (const f of rest) if (!known.has(f)) throw new Error(`not a migration of this workspace: ${f}`);
    requireApplied(root, rest, env);
    record(env, rest.map((file) => ({ file, databases: databasesToRecord(file, readFileSync(join(root, file), "utf8"), on) })));
    console.log(`recorded ${rest.length}`);
  } else if (command === "baseline") {
    if (!before || !/^\d{4}-\d{2}-\d{2}$/.test(before)) throw new Error("baseline needs --before YYYY-MM-DD");
    const files = datedBefore(migrationFiles(root), before);
    requireApplied(root, files, env);
    record(env, files);
    console.log(`baseline: ${files.length} files dated before ${before} recorded as applied`);
  } else if (command === "unrecord") {
    const known = new Set(ledger(env));
    for (const f of rest) if (!known.has(f)) throw new Error(`not in the ledger: ${f}`);
    psql(env, `DELETE FROM dev_migration_ledger WHERE file IN (${rest.map(quote).join(",")});`);
    console.log(`unrecorded ${rest.length}`);
  } else if (command === "verify") {
    const only = take("--only");
    const known = new Set(ledger(env));
    const files = migrationFiles(root).filter((f) => !only || f.includes(only));
    const rows = verifyFiles(root, files, env);
    for (const r of rows) {
      const bad = r.probes.filter((p) => !p.ok);
      console.log(`${r.status.padEnd(8)} ${known.has(r.file) ? "recorded" : "pending "} ${r.file}${bad.length && r.status !== "present" ? `\n           not found: ${bad.map((p) => `${p.db}:${p.label}`).join("; ")}` : ""}`);
    }
    const count = (s) => rows.filter((r) => r.status === s).length;
    console.log(`verify: ${count("present")} present, ${count("partial")} partial, ${count("missing")} missing, ${count("unprobed")} unprobed of ${rows.length}`);
    const wrong = rows.filter((r) => known.has(r.file) && r.status !== "present");
    if (wrong.length) console.error(`${wrong.length} recorded as applied without their effect on dev: apply them (docs/DEV_DATABASE.md), then correct the ledger.`);
    if (wrong.length || count("unprobed")) process.exitCode = 1;
  } else {
    console.error("usage: dev-migrations.mjs check | record <file>… [--on db1,db2] | unrecord <file>… | baseline --before YYYY-MM-DD | verify [--only <text>]   [--root <workspace>] [--env-file <f>]");
    process.exitCode = 2;
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    main(process.argv.slice(2));
  } catch (e) {
    console.error(e.message);
    process.exitCode = 1;
  }
}
