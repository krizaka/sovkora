/**
 * The rules of « Reset dev data » (krizaka/.github docs/plans/2026-11-02-db-migration-plan.md § R, krizaka-internal#2255):
 * which databases a product owns, in which order they are cleared, what the statements are, and every guard that keeps
 * the reset away from anything that is not a DEV product database. Pure: no network, no secret, no clock (the writers
 * and the key check of `restoreServiceAccounts` are passed in).
 */
import { checkKeys, keyVariable } from "./service-keys.mjs";

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

/**
 * The products that call Sovkora with a service account (`svc_<product>`), as data (`scripts/dev-service-products.json`):
 * the Studios the account is granted, the dev App Platform app that holds its `AI_API_KEY`, and the vault source (a name of
 * secrets.map) whose file holds it. Refuses a shape that would make a restore guess.
 */
export function parseServiceProducts(text) {
  const parsed = JSON.parse(text);
  const out = {};
  for (const [product, e] of Object.entries(parsed)) {
    const ok = e && Array.isArray(e.studios) && e.studios.length > 0 && e.studios.every((x) => typeof x === "string")
      && typeof e.app === "string" && e.app !== "" && typeof e.vault === "string" && e.vault !== "";
    if (!ok) throw new Error(`service product ${product}: needs studios (a non-empty list), app and vault`);
    out[product] = Object.freeze({ studios: Object.freeze([...e.studios]), app: e.app, vault: e.vault });
  }
  return Object.freeze(out);
}

/** The service accounts a reset drops that this list cannot put back: refused before anything is cleared. */
export function unrestorable(dropped, catalog) {
  return dropped.filter((p) => !Object.hasOwn(catalog, p));
}

/**
 * The live spec of an app (a JSON document) with `key` set to `value` on every component that already declares it, as a
 * SECRET. Never adds the variable to a component that does not read it. Throws when no component declares it.
 * @returns {{ spec: object, components: string[] }}
 */
export function specWithSecret(spec, key, value) {
  const next = structuredClone(spec);
  const components = [];
  for (const kind of ["services", "workers", "jobs", "static_sites", "functions"]) {
    for (const c of next[kind] ?? []) {
      const entry = (c.envs ?? []).find((e) => e.key === key);
      if (!entry) continue;
      entry.value = value;
      entry.type = "SECRET";
      components.push(c.name);
    }
  }
  if (components.length === 0) throw new Error(`no component of ${next.name ?? "the app"} declares ${key}`);
  return { spec: next, components };
}

/** The file of a secrets.map source (its first file, relative to the map's folder): where `secret.sh @<source>` writes a product's own keys. */
export function vaultFileOf(mapText, source, mapDir, home) {
  const line = mapText.split("\n").map((l) => l.trim().split(/\s+/)).find(([name]) => name === source && !name.startsWith("#"));
  const file = line?.[1];
  if (!file) throw new Error(`no vault source ${source} in secrets.map`);
  if (file.startsWith("/")) return file;
  if (file.startsWith("~/")) return `${home}/${file.slice(2)}`;
  return `${mapDir}/${file}`;
}

/** The variable that holds the key in the product's vault file and its dev app. */
export const KEY_VARIABLE_OF_PRODUCT = "AI_API_KEY";

/**
 * Puts back every listed product's service account after the users database was cleared and reseeded, then proves each key.
 * Each new key goes to the three places a key lives (`writers.vault`, `writers.app`, `writers.watch`), then one call per
 * key to the edge must answer 200. Any failure stops with the product named; no key is ever logged or put in a message.
 *
 * @param {{ catalog: Record<string, {studios: string[], app: string, vault: string}>,
 *           issue: (product: string, opts: {studios: string[]}) => Promise<{key: string}>,
 *           writers: { vault: (product: string, entry: object, key: string) => void, app: (product: string, entry: object, key: string) => void|string[], watch: (product: string, key: string) => void },
 *           edgeUrl: string, fetchImpl?: typeof fetch, log: (line: string) => void }} p
 */
export async function restoreServiceAccounts({ catalog, issue, writers, edgeUrl, fetchImpl = fetch, log }) {
  const keys = new Map();
  for (const [product, entry] of Object.entries(catalog)) {
    let made;
    try {
      made = await issue(product, { studios: [...entry.studios] });
      writers.vault(product, entry, made.key);
      writers.app(product, entry, made.key);
      writers.watch(product, made.key);
    } catch (e) {
      throw new Error(`service account of ${product} not put back: ${scrub(e.message, made?.key)}`);
    }
    keys.set(product, made.key);
    log(`  ${product}: service account back, key written to its vault file, its dev app (${entry.app}) and ${keyVariable(product)}`);
  }
  const results = await checkKeys({ products: [...keys.keys()], keyOf: (p) => keys.get(p), edgeUrl, fetchImpl });
  const bad = results.filter((r) => !r.ok);
  if (bad.length) throw new Error(`service key check failed: ${bad.map((r) => r.message).join("; ")}`);
  for (const r of results) log(`  ${r.message}`);
}

function scrub(message, key) {
  return key ? String(message).replaceAll(key, "<key>") : String(message);
}
