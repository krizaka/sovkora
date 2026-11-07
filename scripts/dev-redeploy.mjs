#!/usr/bin/env node
/**
 * Keeps the dev `core` image in step with krizaka/sovkora's main (docs/DEPLOY_DEV.md § Redeploy).
 *
 * `core` is built from krizaka/sovkora's Dockerfile, and its build caches layers App Platform does not invalidate on its own
 * (CORE_REBUILD). This script (run by .github/workflows/dev-redeploy.yml every 15 minutes, or by hand) compares the newest
 * commit on sovkora's main with the last manual deployment, and starts `--force-rebuild` when it is newer.
 *
 *   node scripts/dev-redeploy.mjs [--dry-run]     # needs an authenticated doctl (the workflow logs it in with OPERATOR_DEPLOY_TOKEN) and gh (GH_TOKEN, public reads)
 *
 * It only starts a deployment, never changes the spec, and does nothing while a deployment is building.
 */
import { execFileSync } from "node:child_process";
import { byText } from "./lib/source-scan.mjs";

const APP = "sovkora-dev";
const ORG = "krizaka";
const BUSY = new Set(["PENDING_BUILD", "BUILDING", "PENDING_DEPLOY", "DEPLOYING"]);

/**
 * @param {{ deployments: {created_at: string, cause: string, phase: string}[], heads: Record<string, string> }} state
 *   deployments newest first; heads = repository → ISO date of the newest commit on main
 * @returns {{ redeploy: boolean, reason: string, newer: string[] }}
 */
export function decide({ deployments, heads }) {
  if (deployments.some((d) => BUSY.has(d.phase))) return { redeploy: false, reason: "a deployment is in progress", newer: [] };
  const last = deployments.find((d) => /manual/i.test(d.cause) && d.phase !== "ERROR" && d.phase !== "CANCELED");
  if (!last) return { redeploy: true, reason: "no manual deployment on record", newer: Object.keys(heads) };
  const since = Date.parse(last.created_at);
  const newer = Object.entries(heads).filter(([, at]) => Date.parse(at) > since).map(([repo]) => repo).sort(byText);
  return newer.length
    ? { redeploy: true, reason: `${newer.join(", ")} moved after the last rebuild (${last.created_at})`, newer }
    : { redeploy: false, reason: "core is up to date", newer: [] };
}

/**
 * A rollback must never go unnoticed (2026-10-29: dev ran an old image for hours after two failed deployments, "automated
 * rollback after failed deployment", so merged packs and fixes were not live). Fails when the ACTIVE deployment is an automated
 * rollback, or when a newer deployment than the active one ended in ERROR.
 * @param {{ id: string, created_at: string, cause: string, phase: string }[]} deployments newest first
 * @returns {{ ok: boolean, message: string }}
 */
export function rollbackVerdict(deployments) {
  const active = deployments.find((d) => d.phase === "ACTIVE");
  if (!active) return { ok: false, message: "no ACTIVE deployment" };
  if (/rollback/i.test(active.cause)) {
    const failed = /"([0-9a-f-]{36})"/.exec(active.cause)?.[1] ?? "unknown";
    return { ok: false, message: `dev runs an automated rollback (${active.id}); the deployment ${failed} failed: read its logs (doctl apps logs <app> core --deployment ${failed} --type deploy)` };
  }
  const failedAfter = deployments.find((d) => d.phase === "ERROR" && Date.parse(d.created_at) > Date.parse(active.created_at));
  if (failedAfter) return { ok: false, message: `deployment ${failedAfter.id} failed after the active one (${active.id})` };
  return { ok: true, message: `the active deployment ${active.id} is the latest` };
}

/**
 * "app spec updated" (and a spec apply in general) redeploys the commit App Platform already had, not main's head: on 2026-11-01 three
 * builds in a row compiled the commit BEFORE the fix that would have made them pass. Fails when the newest finished deployment's `core`
 * source commit is not the head of krizaka/sovkora main.
 * @param {{ id: string, phase: string, services?: {name: string, source_commit_hash?: string}[] }[]} deployments newest first
 * @param {string} mainSha head of krizaka/sovkora main
 * @returns {{ ok: boolean, message: string }}
 */
export function staleSourceVerdict(deployments, mainSha) {
  const coreCommit = (d) => d.services?.find((s) => s.name === "core")?.source_commit_hash;
  // A rebuild just started (the redeploy step runs right before this check) is the cure: judge it when it ends.
  if (deployments.some((d) => BUSY.has(d.phase))) return { ok: true, message: "a deployment is in progress; the commit is judged once it ends" };
  const last = deployments.find((d) => !BUSY.has(d.phase) && coreCommit(d));
  const built = last && coreCommit(last);
  if (!last || !built) return { ok: true, message: "no finished deployment with a recorded source commit" };
  if (mainSha.startsWith(built) || built.startsWith(mainSha)) return { ok: true, message: `core was built from main's head (${built.slice(0, 7)})` };
  return { ok: false, message: `deployment ${last.id} built core from ${built.slice(0, 7)}, behind main (${mainSha.slice(0, 7)}): a spec apply reuses the old commit, start doctl apps create-deployment <app> --force-rebuild` };
}

/**
 * The redeploy ships a new jar every 15 minutes and never applies a migration (docs/DEV_DATABASE.md), so a service can run ahead of its
 * database: on 2026-11-02 dev billing answered 500 on /api/v1/billing/plans, `column "overage_credit_price_cents" does not exist`
 * (krizaka-billing 2026-10-30-offers-versioned.sql merged, released in 0.8.x, never applied; krizaka-internal#2255). Fails when the run
 * logs of `core` hold such an error, and names the services and the missing columns or tables (secret-shaped text never leaves the log).
 * @param {string} logs run logs of the core component, one line per entry, prefixed `[service]` by run-services.sh
 * @returns {{ ok: boolean, message: string }}
 */
export function schemaDriftVerdict(logs) {
  const found = new Map();
  for (const line of logs.split("\n")) {
    const m = /\[([\w-]+)\].*PSQLException: ERROR: (column|relation) "([^"]+)" does not exist/.exec(line);
    if (m) found.set(`${m[1]}: ${m[2]} ${m[3]}`, true);
  }
  if (!found.size) return { ok: true, message: "no schema drift in the core run logs" };
  return { ok: false, message: `a service runs ahead of its database (apply the pending migration, node scripts/dev-migrations.mjs check): ${[...found.keys()].sort((a, b) => a.localeCompare(b)).join("; ")}` };
}

/**
 * Why a deployment failed, when it is a Flyway baseline that cannot adopt a database created before Flyway (krizaka-internal#2255,
 * #2366: from 2026-11-03 every dev deployment rolled back on `relation "orazaka_jobs" already exists`, while the old image kept
 * running against an old schema). A red "Dev redeploy" now says it instead of "read its logs".
 * @param {string} logs deploy logs of the failed deployment, one line per entry, prefixed `[service]`
 * @returns {{ found: boolean, message: string }}
 */
export function deployFailureCause(logs) {
  const failed = new Map();
  const present = new Map();
  for (const line of logs.split("\n")) {
    const f = /\[([\w-]+)\].*Migration of schema "[^"]*" to version "([^"]+)" failed/.exec(line);
    if (f) failed.set(f[1], f[2]);
    const r = /\[([\w-]+)\].*ERROR: relation "([^"]+)" already exists/.exec(line);
    if (r) present.set(r[1], r[2]);
  }
  const describe = ([svc, version]) => {
    const relation = present.get(svc);
    const why = relation ? `, relation ${relation} already exists (a database created before Flyway: reset it or adopt it)` : "";
    return `${svc}: Flyway baseline "${version}" failed${why}`;
  };
  const lines = [...failed.entries()].map(describe).toSorted((a, b) => a.localeCompare(b));
  return lines.length ? { found: true, message: lines.join("; ") } : { found: false, message: "no migration failure in the deploy logs" };
}

const run = (cmd, args) => execFileSync(cmd, args, { encoding: "utf8", maxBuffer: 256 * 1024 * 1024, stdio: ["ignore", "pipe", "inherit"] });

/** `--check`: the rollback (with its cause), stale-source and schema-drift verdicts of the dev app; any red one fails the run. */
function checkDeployments(org, id, deployments) {
  const v = rollbackVerdict(deployments);
  console.log(`${APP}: ${v.message}`);
  const failedId = /deployment ([0-9a-f-]{36}) failed/.exec(v.message)?.[1];
  if (!v.ok && failedId) {
    const cause = deployFailureCause(run("doctl", ["apps", "logs", id, "core", "--deployment", failedId, "--type", "deploy"]));
    if (cause.found) console.log(`${APP}: cause: ${cause.message}`);
  }
  const mainSha = run("gh", ["api", `repos/${org}/sovkora/commits/main`, "--jq", ".sha"]).trim();
  const stale = staleSourceVerdict(deployments, mainSha);
  console.log(`${APP}: ${stale.message}`);
  const drift = schemaDriftVerdict(run("doctl", ["apps", "logs", id, "core", "--type", "run", "--tail", "2000"]));
  console.log(`${APP}: ${drift.message}`);
  if (!v.ok || !stale.ok || !drift.ok) process.exitCode = 1;
}

function main(dryRun, check) {
  // One repository (ADR-029): the image is built from sovkora's tree, so its main is the only head that moves `core`.
  const heads = { sovkora: JSON.parse(run("gh", ["api", `repos/${ORG}/sovkora/commits/main`, "--jq", "{d: .commit.committer.date}"])).d };
  const apps = JSON.parse(run("doctl", ["apps", "list", "-o", "json"]));
  const id = apps.find((a) => a.spec?.name === APP)?.id;
  if (!id) throw new Error(`no app ${APP}`);
  const deployments = JSON.parse(run("doctl", ["apps", "list-deployments", id, "-o", "json"]));
  if (check) {
    checkDeployments(ORG, id, deployments);
    return;
  }
  const verdict = decide({ deployments, heads });
  console.log(`${APP}: ${verdict.reason}`);
  if (!verdict.redeploy || dryRun) return;
  run("doctl", ["apps", "create-deployment", id, "--force-rebuild", "--format", "ID", "--no-header"]);
  console.log(`${APP}: rebuild started`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    main(process.argv.includes("--dry-run"), process.argv.includes("--check"));
  } catch (e) {
    console.error(e.message);
    process.exitCode = 1;
  }
}
