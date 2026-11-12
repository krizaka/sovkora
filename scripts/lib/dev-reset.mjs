/**
 * The rules of « Reset dev data » (krizaka/.github docs/plans/2026-11-02-db-migration-plan.md § R, krizaka-internal#2255):
 * which databases a product owns, in which order they are cleared, what the statements are, and every guard that keeps
 * the reset away from anything that is not a DEV product database. Pure: no network, no secret, no clock.
 */

/** The 8 product databases of the dev cluster, by product, in the plan's order (identities, plans, engine, apps). */
export const PRODUCT_DATABASES = Object.freeze({
  orazaka: Object.freeze([
    "krizaka_users_db",
    "krizaka_billing_db",
    "orazaka_db",
    "orazaka_studio_db",
    "orazaka_knowledge_db",
    "orazaka_automation_db",
  ]),
  yomizaka: Object.freeze(["yomizaka_db"]),
  akinaza: Object.freeze(["akinaza_db"]),
});

export const PRODUCTS = Object.freeze(["orazaka", "yomizaka", "akinaza"]);
export const TARGETS = Object.freeze(["local", "dev"]);

/** Never reset, whatever the arguments say (plan § 0.6). */
export const EXCLUDED_DATABASES = Object.freeze(["krizaka_newsletter", "krizaka_accounts", "defaultdb", "_dodb", "tofu_state", "postgres"]);

/** The DigitalOcean App Platform app each product redeploys after its databases are cleared (its migrations run there). */
export const DEV_APPS = Object.freeze({ orazaka: "sovkora-dev", yomizaka: "yomizaka-dev", akinaza: "akinaza-dev" });

/** The dev cluster `krizaka-dev-pg` (stacks/dev/orazaka in krizaka-infra), public or private host. */
export const DEV_HOST = /^(private-)?krizaka-dev-pg-do-user-\d+-0\.[a-z]\.db\.ondigitalocean\.com$/;
const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "::1", "postgres", "db"]);

/** Words that mark production or a commercial (prod) domain: refused in any host or URL the reset is given. */
const PROD_MARKS = /prod|verikora|folioviv|sovkora|kridoc/i;

const IDENT = /^[a-z_][a-z0-9_]{0,62}$/;

/** The databases of `product` (`all` = the three, in order). Throws on an unknown product. */
export function databasesOf(product) {
  if (product === "all") return PRODUCTS.flatMap((p) => PRODUCT_DATABASES[p]);
  if (!PRODUCTS.includes(product)) throw new Error(`unknown product « ${product} » (${[...PRODUCTS, "all"].join(", ")})`);
  return [...PRODUCT_DATABASES[product]];
}

export function productsOf(product) {
  return product === "all" ? [...PRODUCTS] : (databasesOf(product), [product]);
}

/**
 * Every reason to refuse the reset, before any connection. Empty = allowed.
 * @param {{ product: string, target: string, appEnv?: string, host?: string, expectedHost?: string, databases?: string[], urls?: string[] }} input
 */
export function refusals({ product, target, appEnv, host, expectedHost, databases, urls = [] }) {
  const out = [];
  let allowed = [];
  try {
    allowed = databasesOf(product);
  } catch (e) {
    out.push(e.message);
  }
  if (!TARGETS.includes(target)) out.push(`target must be local or dev, not « ${target} »`);
  if (appEnv !== target) out.push(`APP_ENV must be « ${target} » for --target ${target} (it is « ${appEnv ?? ""} »)`);
  for (const value of [host, expectedHost, ...urls].filter(Boolean)) {
    if (PROD_MARKS.test(value)) out.push("a production or commercial host was given: refused");
  }
  out.push(...hostRefusals(target, host, expectedHost), ...databaseRefusals(product, allowed, databases));
  return [...new Set(out)];
}

/** The host must exist and, for dev, be the dev cluster's own host; for local, a local Postgres. */
function hostRefusals(target, host, expectedHost) {
  if (!host) return ["no database host"];
  if (target === "local") return LOCAL_HOSTS.has(host) ? [] : ["--target local only reaches a local Postgres (localhost or the compose service)"];
  if (target !== "dev") return [];
  const out = [];
  if (!DEV_HOST.test(host)) out.push("the host is not the dev cluster krizaka-dev-pg");
  if (!expectedHost) out.push("no expected dev host (the dev cluster's host from the non-prod OpenTofu outputs)");
  else if (!sameClusterHost(host, expectedHost)) out.push("the host is not the dev cluster's host from the OpenTofu outputs");
  return out;
}

function sameClusterHost(host, expectedHost) {
  return host === expectedHost || host === "private-" + expectedHost || "private-" + host === expectedHost;
}

function databaseRefusals(product, allowed, databases) {
  const out = [];
  for (const db of databases ?? allowed) {
    if (EXCLUDED_DATABASES.includes(db)) out.push(`« ${db} » is never reset`);
    else if (!allowed.includes(db)) out.push(`« ${db} » is not a database of ${product}`);
  }
  return out;
}

function ident(name) {
  if (!IDENT.test(name)) throw new Error(`not an identifier: ${name}`);
  return name;
}

/**
 * Run as the owner role (the role of the database's name), in one transaction: everything the owner created goes,
 * the database, its roles, its pools and doadmin's extensions stay (no OpenTofu drift, `prevent_destroy` holds).
 * The first statement refuses to run anywhere but the named database as its owner.
 */
export function clearSql(db) {
  const owner = ident(db);
  return [
    String.raw`\set ON_ERROR_STOP 1`,
    "BEGIN;",
    `DO $$ BEGIN IF current_database() <> '${owner}' OR current_user <> '${owner}' THEN RAISE EXCEPTION 'dev-reset: not ${owner} as its owner'; END IF; END $$;`,
    `DROP OWNED BY ${owner} CASCADE;`,
    `CREATE SCHEMA IF NOT EXISTS public AUTHORIZATION ${owner};`,
    "COMMIT;",
  ].join("\n");
}

/** Read only, as the owner: the extensions that live in a schema the owner owns (DROP OWNED would take them with it). */
export function extensionsAtRiskSql(db) {
  const owner = ident(db);
  return `SELECT e.extname FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace WHERE pg_get_userbyid(n.nspowner) = '${owner}' ORDER BY 1;`;
}

/**
 * Run as the cluster's admin after the clear, idempotent: the extensions back (krizaka-infra `bootstrap_admin_sql`)
 * and the runtime role's CONNECT when the role exists.
 */
export function bootstrapSql(db, extensions = []) {
  const owner = ident(db);
  const runtime = `${owner}_app`;
  return [
    ...[...new Set(extensions)].sort((a, b) => a.localeCompare(b)).map((e) => `CREATE EXTENSION IF NOT EXISTS ${ident(e)};`),
    `DO $$ BEGIN IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${runtime}') THEN EXECUTE 'GRANT CONNECT ON DATABASE ${owner} TO ${runtime}'; END IF; END $$;`,
  ].join("\n");
}

/** Run as the owner, idempotent: the runtime role's DML (krizaka-infra `grants_sql`), which DROP OWNED removed. */
export function grantsSql(db) {
  const owner = ident(db);
  const runtime = `${owner}_app`;
  const grants = [
    `GRANT USAGE ON SCHEMA public TO ${runtime}`,
    `ALTER DEFAULT PRIVILEGES FOR ROLE ${owner} GRANT USAGE ON SCHEMAS TO ${runtime}`,
    `ALTER DEFAULT PRIVILEGES FOR ROLE ${owner} GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${runtime}`,
    `ALTER DEFAULT PRIVILEGES FOR ROLE ${owner} GRANT USAGE, SELECT ON SEQUENCES TO ${runtime}`,
    `ALTER DEFAULT PRIVILEGES FOR ROLE ${owner} GRANT EXECUTE ON FUNCTIONS TO ${runtime}`,
  ];
  const executes = grants.map((g) => "EXECUTE '" + g.replaceAll("'", "''") + "';").join(" ");
  return `DO $$ BEGIN IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${runtime}') THEN ${executes} END IF; END $$;`;
}

/** The extensions each database's bootstrap creates (same as krizaka-infra stacks; akinaza's moved out of its baseline). */
export const EXTENSIONS = Object.freeze({
  orazaka_db: ["vector"],
  orazaka_knowledge_db: ["vector"],
  akinaza_db: ["btree_gist", "pg_trgm", "unaccent"],
});

/** The ordered steps of one run (what the job summary lists). */
export function plan(product, target) {
  return productsOf(product).flatMap((p) => [
    ...PRODUCT_DATABASES[p].map((db) => ({ product: p, step: "clear", db })),
    { product: p, step: target === "dev" ? "deploy" : "migrate", app: DEV_APPS[p] },
    { product: p, step: "seed" },
    { product: p, step: "smoke" },
  ]);
}

/** One JSON line for the run's log: who, when, what; never a credential or a host. */
export function logLine({ actor, at, product, target, steps, result, droppedServiceAccounts = [] }) {
  const named = steps.map((s) => [s.product, s.step, s.db].filter(Boolean).join(":"));
  return JSON.stringify({ kind: "dev-reset", actor, at, product, target, steps: named, result, droppedServiceAccounts });
}

/**
 * The prototype dev dataset of Orazaka after a reset (the cast the demo and the persona smoke sign in as), as the
 * `node scripts/dev-deploy.mjs <command> [arg]` calls that put it back, in order. The accounts come from `persona`
 * (a plain ROLE_USER on the free plan, stored secrets, never printed), then `names` gives them the cast's names.
 * The catalogue (plans, pricebook, models, Studios) is the services' Flyway V1, not data of this list.
 * Nothing here ever touches a production host: dev-reset's guards run first.
 */
export const ORAZAKA_CAST = Object.freeze(["eric", "mira", "krizaka"]);

export function orazakaDatasetSteps() {
  return [...ORAZAKA_CAST.map((name) => ["persona", name]), ["names"]];
}
