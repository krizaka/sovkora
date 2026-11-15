#!/usr/bin/env node
/**
 * Gives every per-context database role the password its service is configured with.
 *
 * **Why an empty volume needs this, and why nobody had noticed.** `infra/initdb/*.sql` creates each
 * context's role with `LOGIN` and **no password**: psql 15 cannot read the environment (`\getenv`
 * arrived in 16), ERR-125 bans a shell script in the initdb directory, and a committed literal is
 * the defect that was closed in audit #5. The seeds say so in their own comments — the password is
 * applied afterwards, from `<CONTEXT>_DB_PASSWORD`, by `orazaka start`.
 *
 * The e2e harness does not run `orazaka start`; it runs `docker compose up` itself. On a volume
 * that survived from some earlier `orazaka start` the roles already had passwords, so this step was
 * invisible — **which is exactly what "not hermetic" was hiding**. The first run from a genuinely
 * empty volume ends with:
 *
 *   [krizaka-users-service] FATAL: password authentication failed for user "krizaka_users"
 *
 * and the stack does not come up. So "the harness reaches a working stack from empty" was not true,
 * and no scripted path made it true. This is that path. It is deliberately **not** a migration
 * framework: this is one `ALTER ROLE` per context, which is what the seeds already document.
 *
 * **The contexts are derived from the seed, not listed.** Every `CREATE ROLE orazaka_<ctx> LOGIN`
 * in `infra/initdb/` contributes one, so a seventh bounded context is covered by the file that
 * creates it. The CLI's own copy of this step carries a hardcoded five-element list and will not
 * be; that divergence is recorded rather than repaired here.
 *
 * Fails closed: a role whose password is missing from the environment is reported and the script
 * exits non-zero, because a stack that cannot authenticate is not a stack the gate should test.
 */

import { execFileSync } from "node:child_process";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { byText } from "./lib/source-scan.mjs";
import { composeRoot } from "./lib/compose-root.mjs";
import { toolPath } from "./lib/tool-path.mjs";

const ROOT = composeRoot(process.argv[2] ?? process.cwd());
const initdb = join(ROOT, "infra", "initdb");

/** Every context whose role the seed creates without a password. */
function contexts() {
  const found = new Set();
  for (const file of readdirSync(initdb).filter((f) => f.endsWith(".sql"))) {
    const sql = readFileSync(join(initdb, file), "utf8").replace(/--[^\n]*/g, " ");
    for (const match of sql.matchAll(/CREATE\s+ROLE\s+orazaka_(\w+)\s+LOGIN/gi)) {
      found.add(match[1].toUpperCase());
    }
  }
  return [...found].sort(byText);
}

/**
 * Loads `.env` into the process environment for keys it does not already carry.
 *
 * The harness's other steps source it inside their own `bash -c`; this one is invoked as `node`
 * with Maven's environment, which does not have it. Reading the file here rather than adding a
 * shell wrapper keeps the path a documented script rather than a shell fragment (ERR-125), and it
 * makes the script runnable by hand in the same way the harness runs it.
 */
function loadDotEnv(root) {
  let text;
  try {
    text = readFileSync(join(root, ".env"), "utf8");
  } catch {
    return; // running with the variables already exported is legitimate
  }
  for (const line of text.split("\n")) {
    const match = /^\s*([A-Z0-9_]+)\s*=\s*(.*)$/.exec(line);
    if (!match) continue;
    const value = match[2].trim().replace(/^["']|["']$/g, "");
    if (process.env[match[1]] === undefined) process.env[match[1]] = value;
  }
}

loadDotEnv(ROOT);

const superuser = process.env.DB_USERNAME ?? "orazaka_app";
const compose = ["compose", "-p", "orazaka", "--env-file", join(ROOT, ".env"), "-f", "infra/docker-compose.yml"];

/** initdb is still running when the port opens; waiting on the port is not waiting on the schema. */
function waitForRoles(expected) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const out = execFileSync(
        toolPath("docker"),
        [...compose, "exec", "-T", "db-vector", "psql", "-U", superuser, "-d", "postgres", "-tAc",
          `SELECT count(*) FROM pg_roles WHERE rolname LIKE 'orazaka\\_%'`],
        { cwd: ROOT, stdio: ["pipe", "pipe", "pipe"] },
      ).toString().trim();
      if (Number(out) >= expected) return true;
    } catch {
      /* the container is not ready to answer yet */
    }
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 2000); // 2 s, without spawning a program
  }
  return false;
}

const wanted = contexts();
if (wanted.length === 0) {
  console.error("[db-roles] no CREATE ROLE orazaka_<ctx> LOGIN found in infra/initdb — nothing to apply, which means this script is pointed at the wrong tree");
  process.exit(1);
}

if (!waitForRoles(wanted.length)) {
  console.error(`[db-roles] the seeds never created all ${wanted.length} roles; infra/initdb did not finish`);
  process.exit(1);
}

const missing = wanted.filter((ctx) => !process.env[`${ctx}_DB_PASSWORD`]);
if (missing.length > 0) {
  console.error(
    `[db-roles] no <CTX>_DB_PASSWORD in the environment for: ${missing.join(", ")}.\n` +
      "  Those roles cannot authenticate, so the services that own them will not start.",
  );
  process.exit(1);
}

for (const ctx of wanted) {
  const role = `orazaka_${ctx.toLowerCase()}`;
  // Over stdin, not -c: psql only interpolates :'pw' for file/stdin input. Interpolating means psql
  // does the quoting, so a password containing a quote cannot terminate the statement, and the
  // value never reaches a shell.
  //
  // The password is passed RAW. The CLI's copy of this step writes JSON.stringify(password) into a
  // command STRING that a shell then unquotes; execFileSync has no shell, so the same expression
  // here made the quotation marks part of the password. ALTER ROLE reported success, every role
  // "could authenticate", and identity-service still died with `password authentication failed` —
  // a step that says it worked while leaving the thing it configures broken.
  execFileSync(
    toolPath("docker"),
    [...compose, "exec", "-T", "db-vector", "psql", "-U", superuser, "-d", "postgres",
      "-v", "ON_ERROR_STOP=1", "-v", "pw=" + process.env[ctx + "_DB_PASSWORD"]],
    { cwd: ROOT, input: `ALTER ROLE ${role} PASSWORD :'pw';\n`, stdio: ["pipe", "pipe", "inherit"] },
  );
}

console.log(`[db-roles] ${wanted.length} context roles can authenticate: ${wanted.join(", ")}`);
