#!/usr/bin/env node
/**
 * MANUAL TOOL (ADR-026), run by a person. Owner: sovkora lane. Listed in README.md « Manual tools ».
 * « Reset dev data » in one command (krizaka/.github docs/plans/2026-11-02-db-migration-plan.md § R, krizaka-internal#2255).
 *
 *   node scripts/dev-reset.mjs <orazaka|yomizaka|akinaza|all> --target <local|dev> [--dry-run] [--yes] [--no-deploy] [--drop-service-accounts <product,…>] [--log <file>]
 *
 * Per product, in order: clear each of its databases as the owner role (DROP OWNED BY <owner> CASCADE, public schema back),
 * put back doadmin's extensions and the runtime role's grants, redeploy its dev app (its migrations run from V1 before it
 * takes traffic), wait for the deployment, then seed the cast (Orazaka: the personas Eric, Mira, Krizaka then their names, `orazakaDatasetSteps`; the others `tools/seed-dev.sh <product>`) and run the persona smoke.
 * Clearing krizaka_users_db drops every product's service account (svc_<product>) and its API key: refused unless each product
 * is named with --drop-service-accounts (krizaka-internal#3214); the products dropped go to the run log. After the Orazaka
 * reseed (dev target) every product of scripts/dev-service-products.json gets its account back (`issueServiceAccount`) and its new
 * key in its vault file, its dev App Platform app and this repository's SERVICE_KEY_<PRODUCT> secret; then each key must answer
 * 200 on the edge or the reset fails naming the product (krizaka-internal#2416).
 * Without --yes it stops after the guards and the read-only preflight (same as --dry-run).
 *
 * Runs on the maintainer's Mac: the dev cluster trusts no GitHub runner (CTO decision 2026-10-28, krizaka/.github#356), so
 * there is no workflow that could reach it. Guards (scripts/lib/dev-reset.mjs, tested): closed list of the 8 product
 * databases, excluded ones refused before connecting, APP_ENV must equal the target, the host must be the dev cluster's
 * (DATABASE_HOST must equal the one from krizaka-infra's non-prod outputs, --expected-host or DEV_RESET_EXPECTED_HOST),
 * any prod or commercial host refused. Secrets through secret.sh @orazaka (DATABASE_HOST, DATABASE_PASSWORD_<DB>,
 * DATABASE_PASSWORD_DOADMIN for the extensions only): never printed. One JSON line per run goes to --log (default
 * ~/.krizaka-private/logs/dev-reset.jsonl).
 */
import { execFileSync, spawnSync } from "node:child_process";
import { appendFileSync, existsSync, mkdirSync, readFileSync, readdirSync } from "node:fs";
import { homedir, userInfo } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { devSecrets } from "./lib/dev-secrets.mjs";
import { edgeBase } from "./lib/edge-url.mjs";
import { issueServiceAccount, writeEnvKeys } from "./dev-deploy.mjs";
import { USERS_DATABASE, droppedAccountsAlert, productsOfAccounts, serviceAccountRefusal, serviceAccountsSql } from "./lib/service-keys.mjs";
import { DEV_APPS, EXTENSIONS, KEY_VARIABLE_OF_PRODUCT, PRODUCT_DATABASES, bootstrapSql, clearSql, extensionsAtRiskSql, grantsSql, logLine, orazakaDatasetSteps, parseServiceProducts, plan, productsOf, refusals, restoreServiceAccounts, specWithSecret, unrestorable, vaultFileOf } from "./lib/dev-reset.mjs";
import { keyVariable } from "./lib/service-keys.mjs";

const TOOLS = join(homedir(), ".krizaka-worktrees/tools");
const SERVICE_PRODUCTS_FILE = join(dirname(fileURLToPath(import.meta.url)), "dev-service-products.json");
const SOURCE_REPOSITORY = "krizaka/sovkora";

function flag(argv, name) {
  return argv.includes(`--${name}`);
}
function option(argv, name, fallback) {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 && argv[i + 1] && !argv[i + 1].startsWith("--") ? argv[i + 1] : fallback;
}

/** Fixed, root-owned or Homebrew directories: a tool is never looked up through a PATH that a project could write to. */
const TOOL_DIRS = ["/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/bin"];

function libpqBins() {
  const cellar = "/opt/homebrew/Cellar/libpq";
  if (!existsSync(cellar)) return [];
  return readdirSync(cellar)
    .sort((a, b) => a.localeCompare(b, "en", { numeric: true }))
    .reverse()
    .map((v) => join(cellar, v, "bin"));
}

function toolPath(name, override) {
  if (override) return override;
  const dirs = [...TOOL_DIRS, ...libpqBins()];
  const found = dirs.map((d) => join(d, name)).find((f) => existsSync(f));
  if (!found) throw new Error(`${name} not found in ${dirs.join(", ")} (set ${name.toUpperCase()})`);
  return found;
}

const psqlBinary = () => toolPath("psql", process.env.PSQL);
const doctl = (...args) => execFileSync(toolPath("doctl", process.env.DOCTL), args, { encoding: "utf8" });

/** A synchronous pause, without a child process. */
function pause(seconds) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, seconds * 1000);
}

/** `sql` on `db` as `role`; the password goes through the environment only, the host never reaches a message. */
function psql({ host, port, password, sslmode }, db, role, sql) {
  const conn = `host=${host} port=${port} dbname=${db} user=${role} sslmode=${sslmode}`;
  const r = spawnSync(psqlBinary(), [conn, "-v", "ON_ERROR_STOP=1", "-X", "-q", "-At"], { input: sql, encoding: "utf8", env: { ...process.env, PGPASSWORD: password } });
  if (r.status !== 0) throw new Error(`psql on ${db} as ${role} failed: ${String(r.stderr).replaceAll(host, "<host>").trim()}`);
  return r.stdout.trim();
}

function waitForDeployment(app, log) {
  const id = doctl("apps", "list", "--format", "ID,Spec.Name", "--no-header")
    .split("\n").map((l) => l.trim().split(/\s+/)).find(([, name]) => name === app)?.[0];
  if (!id) throw new Error(`no App Platform app ${app}`);
  const dep = JSON.parse(doctl("apps", "create-deployment", id, "--force-rebuild", "--output", "json"))[0];
  log(`  ${app}: deployment ${dep.id} started`);
  for (let i = 0; i < 120; i++) {
    pause(15);
    const d = JSON.parse(doctl("apps", "get-deployment", id, dep.id, "--output", "json"))[0];
    if (d.phase === "ACTIVE") return log(`  ${app}: ACTIVE`);
    if (["ERROR", "CANCELED", "SUPERSEDED"].includes(d.phase)) throw new Error(`${app}: deployment ${dep.id} ended ${d.phase} (the previous release keeps serving)`);
  }
  throw new Error(`${app}: deployment ${dep.id} not active after 30 minutes`);
}

function run(cmd, args, cwd) {
  const r = spawnSync(cmd, args, { cwd, stdio: "inherit" });
  if (r.status !== 0) throw new Error(`${[cmd, ...args].join(" ")} exited ${r.status}`);
}

/** The connection of `role` to the target's cluster; the password comes from the environment or the vault only. */
function connectionFor({ local, host, environ, secrets }) {
  return (role) => ({
    host,
    port: local ? (environ.PGPORT ?? "5432") : (secrets.DATABASE_PORT ?? "25060"),
    sslmode: local ? "disable" : "require",
    password: local ? environ.PGPASSWORD : secrets["DATABASE_PASSWORD_" + role.toUpperCase()],
  });
}

/** One database: the read-only preflight, then (when applying) clear, extensions back, grants back. */
function resetDatabase({ db, conn, apply, acknowledged, dropped, catalog, log }) {
  const atRisk = psql(conn(db), db, db, extensionsAtRiskSql(db));
  const putBack = atRisk ? " (extensions put back after: " + atRisk.replaceAll("\n", ", ") + ")" : "";
  log(`  ${db}: preflight ok as its owner${putBack}`);
  if (db === USERS_DATABASE) {
    const products = productsOfAccounts(psql(conn(db), db, db, serviceAccountsSql()));
    if (products.length) log(`  ${droppedAccountsAlert(products)}`);
    const refusal = serviceAccountRefusal(db, products, acknowledged);
    if (apply && refusal) throw new Error(refusal);
    const orphans = unrestorable(products, catalog);
    if (apply && orphans.length) throw new Error(`no restore data for the service account of ${orphans.join(", ")}: add it to scripts/dev-service-products.json before a reset can put it back`);
    dropped.push(...products);
  }
  if (!apply) return;
  psql(conn(db), db, db, clearSql(db));
  psql(conn("doadmin"), db, "doadmin", bootstrapSql(db, EXTENSIONS[db]));
  psql(conn(db), db, db, grantsSql(db));
  log(`  ${db}: cleared, extensions and grants back`);
}

/** A command's stdout; the value, when there is one, goes on its standard input only: never on the command line, never echoed. */
function runWithInput(cmd, args, input) {
  const r = spawnSync(cmd, args, { input, encoding: "utf8" });
  if (r.status !== 0) throw new Error(`${cmd.split("/").pop()} ${args.slice(0, 2).join(" ")} exited ${r.status}`);
  return r.stdout;
}

/** The three places a service key lives; each writer takes the key as a value and prints nothing. */
function keyWriters(environ) {
  return {
    /** The product's vault file: one upsert of AI_API_KEY (the file is never read into a log). */
    vault(_product, entry, key) {
      const map = environ.KRIZAKA_SECRETS_MAP || execFileSync(join(homedir(), ".local/bin/kz-config"), ["secretsMap"], { encoding: "utf8" }).trim();
      writeEnvKeys(vaultFileOf(readFileSync(map, "utf8"), entry.vault, dirname(map), homedir()), { [KEY_VARIABLE_OF_PRODUCT]: key });
    },
    /** The product's dev app: AI_API_KEY (SECRET) on the components that declare it, on the live spec, never the repository's. */
    app(_product, entry, key) {
      const id = doctl("apps", "list", "--format", "ID,Spec.Name", "--no-header")
        .split("\n").map((l) => l.trim().split(/\s+/)).find(([, name]) => name === entry.app)?.[0];
      if (!id) throw new Error(`no App Platform app ${entry.app}`);
      const live = JSON.parse(doctl("apps", "spec", "get", id, "--format", "json"));
      const { spec } = specWithSecret(live, KEY_VARIABLE_OF_PRODUCT, key);
      runWithInput(toolPath("doctl", process.env.DOCTL), ["apps", "update", id, "--spec", "-", "--format", "ID", "--no-header"], JSON.stringify(spec));
    },
    /** This repository's secret of the key watch; the value goes through standard input. */
    watch(product, key) {
      runWithInput(toolPath("gh", process.env.GH), ["secret", "set", keyVariable(product), "--repo", SOURCE_REPOSITORY], key);
    },
  };
}

/** After a product's databases are clear: its migrations run from V1 (redeploy), then the cast, the service accounts and the smoke. */
async function reseedProduct({ p, local, noDeploy, catalog, environ, log }) {
  if (local) {
    log(`  ${p}: start the stack (docker compose up): the services' migrations run from V1; the products' service accounts are put back on the dev target only`);
    return;
  }
  if (!noDeploy) waitForDeployment(DEV_APPS[p], log);
  if (p === "orazaka") for (const step of orazakaDatasetSteps()) run(process.execPath, ["scripts/dev-deploy.mjs", ...step]);
  else run(join(TOOLS, "seed-dev.sh"), [p]);
  if (p === "orazaka") {
    await restoreServiceAccounts({ catalog, issue: issueServiceAccount, writers: keyWriters(environ), edgeUrl: edgeBase(environ), log });
    run(process.execPath, ["scripts/dev-persona-smoke.mjs", "eric"]);
  }
}

const DEFAULT_LOG = join(homedir(), ".krizaka-private/logs/dev-reset.jsonl");

function announce({ product, target, apply, steps, log }) {
  const dryRun = apply ? "" : " (dry run: guards and read-only preflight only)";
  log(`dev-reset ${product} --target ${target}${dryRun}`);
  for (const s of steps) log("  " + s.product + " · " + s.step + (s.db ? " · " + s.db : ""));
}

/** One JSON line per run: who, when, what; never a credential or a host. */
function recordRun({ file, product, target, steps, result, dropped }) {
  mkdirSync(dirname(file), { recursive: true });
  const line = logLine({ actor: userInfo().username, at: new Date().toISOString(), product, target, steps, result, droppedServiceAccounts: dropped });
  appendFileSync(file, line + "\n");
}

export async function main(argv = process.argv.slice(2), environ = process.env) {
  const product = argv[0];
  const target = option(argv, "target", "");
  const apply = flag(argv, "yes") && !flag(argv, "dry-run");
  const secrets = devSecrets();
  const local = target === "local";
  const host = local ? (environ.PGHOST ?? "localhost") : secrets.DATABASE_HOST;
  const expectedHost = option(argv, "expected-host", environ.DEV_RESET_EXPECTED_HOST);
  const no = refusals({ product, target, appEnv: environ.APP_ENV, host, expectedHost });
  if (no.length) {
    for (const reason of no) console.error(`dev-reset: refused: ${reason}`);
    return 2;
  }
  const steps = plan(product, target);
  const log = (line) => console.log(line);
  announce({ product, target, apply, steps, log });
  const conn = connectionFor({ local, host, environ, secrets });
  const acknowledged = option(argv, "drop-service-accounts", "").split(",").filter(Boolean);
  const dropped = [];
  const catalog = parseServiceProducts(readFileSync(SERVICE_PRODUCTS_FILE, "utf8"));
  const record = (result) => recordRun({ file: option(argv, "log", DEFAULT_LOG), product, target, steps, result, dropped });
  try {
    for (const p of productsOf(product)) {
      for (const db of PRODUCT_DATABASES[p]) resetDatabase({ db, conn, apply, acknowledged, dropped, catalog, log });
      if (apply) await reseedProduct({ p, local, noDeploy: flag(argv, "no-deploy"), catalog, environ, log });
    }
    record(apply ? "done" : "dry-run");
    return 0;
  } catch (e) {
    console.error(`dev-reset: stopped: ${e.message}`);
    record(`stopped: ${e.message}`);
    return 1;
  }
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) process.exit(await main());
