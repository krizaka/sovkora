import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { EXCLUDED_DATABASES, bootstrapSql, clearSql, databasesOf, grantsSql, logLine, orazakaDatasetSteps, parseServiceProducts, plan, refusals, restoreServiceAccounts, specWithSecret, unrestorable, vaultFileOf } from "../lib/dev-reset.mjs";

const DEV = "krizaka-dev-pg-do-user-123456-0.b.db.ondigitalocean.com";
const ok = { product: "orazaka", target: "dev", appEnv: "dev", host: DEV, expectedHost: DEV };

test("the dev cluster's host, APP_ENV=dev and a product: allowed", () => {
  assert.deepEqual(refusals(ok), []);
  assert.deepEqual(refusals({ ...ok, host: `private-${DEV}` }), []);
  assert.deepEqual(refusals({ product: "all", target: "local", appEnv: "local", host: "localhost" }), []);
});

test("a production or commercial host is refused, wherever it appears", () => {
  for (const bad of ["krizaka-prod-pg-do-user-1-0.b.db.ondigitalocean.com", "db.verikora.com", "folioviv.com", "sovkora.com", "kridoc.com"]) {
    assert.match(refusals({ ...ok, host: bad }).join(), /production or commercial/);
  }
  assert.match(refusals({ ...ok, urls: ["https://www.sovkora.com"] }).join(), /production or commercial/);
});

test("a host that is not the dev cluster, or not the one OpenTofu names, is refused", () => {
  assert.match(refusals({ ...ok, host: "other-pg-do-user-1-0.b.db.ondigitalocean.com" }).join(), /not the dev cluster/);
  assert.match(refusals({ ...ok, expectedHost: "krizaka-dev-pg-do-user-999-0.b.db.ondigitalocean.com" }).join(), /OpenTofu/);
  assert.match(refusals({ ...ok, expectedHost: undefined }).join(), /no expected dev host/);
  assert.match(refusals({ ...ok, host: undefined }).join(), /no database host/);
  assert.match(refusals({ ...ok, target: "local", appEnv: "local" }).join(), /local Postgres/);
});

test("APP_ENV must equal the target", () => {
  assert.match(refusals({ ...ok, appEnv: "prod" }).join(), /APP_ENV/);
  assert.match(refusals({ ...ok, appEnv: undefined }).join(), /APP_ENV/);
  assert.match(refusals({ ...ok, target: "prod", appEnv: "prod" }).join(), /target must be local or dev/);
});

test("only the 8 product databases: excluded and foreign ones are refused before connecting", () => {
  for (const db of EXCLUDED_DATABASES) assert.match(refusals({ ...ok, databases: [db] }).join(), /never reset/);
  assert.match(refusals({ ...ok, databases: ["yomizaka_db"] }).join(), /not a database of orazaka/);
  assert.match(refusals({ ...ok, product: "orochia" }).join(), /unknown product/);
  assert.equal(databasesOf("all").length, 8);
  assert.ok(!databasesOf("all").some((d) => EXCLUDED_DATABASES.includes(d)));
});

test("the order: identities, plans, engine, then the apps; each product cleared, deployed, seeded, smoked", () => {
  assert.deepEqual(databasesOf("all"), [
    "krizaka_users_db", "krizaka_billing_db", "orazaka_db", "orazaka_studio_db", "orazaka_knowledge_db", "orazaka_automation_db", "yomizaka_db", "akinaza_db",
  ]);
  assert.deepEqual(plan("akinaza", "dev").map((s) => s.step), ["clear", "deploy", "seed", "smoke"]);
  assert.equal(plan("akinaza", "local")[1].step, "migrate");
});

test("the clear runs as the owner in its own database only, in one transaction, and keeps the database", () => {
  const sql = clearSql("orazaka_db");
  assert.match(sql, /current_database\(\) <> 'orazaka_db' OR current_user <> 'orazaka_db'/);
  assert.match(sql, /BEGIN;[\s\S]*DROP OWNED BY orazaka_db CASCADE;[\s\S]*CREATE SCHEMA IF NOT EXISTS public AUTHORIZATION orazaka_db;[\s\S]*COMMIT;/);
  assert.doesNotMatch(sql, /DROP DATABASE|DROP ROLE|TRUNCATE/);
  assert.throws(() => clearSql("x; DROP DATABASE y"), /not an identifier/);
});

test("extensions and grants come back, idempotent, and the runtime role never gets DDL", () => {
  assert.equal(bootstrapSql("orazaka_db", ["vector", "vector"]).match(/CREATE EXTENSION IF NOT EXISTS vector;/g).length, 1);
  const grants = grantsSql("orazaka_db");
  assert.match(grants, /IF EXISTS \(SELECT 1 FROM pg_roles WHERE rolname = 'orazaka_db_app'\)/);
  assert.match(grants, /GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO orazaka_db_app/);
  assert.doesNotMatch(grants, /TRUNCATE|CREATE ON|ALL PRIVILEGES/);
});

test("the log line names steps, never a host or a credential", () => {
  const line = logLine({ actor: "a", at: "2026-11-02T00:00:00Z", product: "orazaka", target: "dev", steps: plan("orazaka", "dev"), result: "dry-run" });
  assert.match(line, /orazaka:clear:krizaka_users_db/);
  assert.doesNotMatch(line, /ondigitalocean|password/i);
});

test("the Orazaka dev dataset: the three personas first, then the cast's names; no production command", () => {
  assert.deepEqual(orazakaDatasetSteps(), [["persona", "eric"], ["persona", "mira"], ["persona", "krizaka"], ["names"]]);
});

// ── The service accounts a reset drops are put back (krizaka-internal#2416) ──
const CATALOG = parseServiceProducts(JSON.stringify({
  verikora: { studios: ["compliance-check", "document-authenticity"], app: "verikora-dev", vault: "verikora" },
  other: { studios: ["x"], app: "other-dev", vault: "other" },
}));
const KEY = "oz_the-new-key-9f8e7d";

function harness({ status = () => 200, failWriter } = {}) {
  const calls = [];
  const out = [];
  const writers = Object.fromEntries(["vault", "app", "watch"].map((w) => [w, (product, ...rest) => {
    calls.push([w, product, rest.at(-1)]);
    if (failWriter === w) throw new Error(`the ${w} write broke with ${rest.at(-1)}`);
  }]));
  const issue = async (product, opts) => {
    calls.push(["issue", product, opts.studios.join(",")]);
    return { key: `${KEY}-${product}` };
  };
  const fetchImpl = async (url, init) => {
    calls.push(["check", url, init.headers.authorization]);
    return { status: status(init.headers.authorization) };
  };
  const run = () => restoreServiceAccounts({ catalog: CATALOG, issue, writers, edgeUrl: "https://edge.test/edge", fetchImpl, log: (l) => out.push(l) });
  return { calls, out, run };
}

test("the list of products is data: verikora with its two Studios, its app and its vault source", () => {
  const real = parseServiceProducts(readFileSync(new URL("../dev-service-products.json", import.meta.url), "utf8"));
  assert.deepEqual(real.verikora.studios, ["compliance-check", "document-authenticity"]);
  assert.equal(real.verikora.app, "verikora-dev");
  assert.throws(() => parseServiceProducts('{"p":{"studios":[],"app":"a","vault":"v"}}'), /service product p/);
  assert.throws(() => parseServiceProducts('{"p":{"studios":["s"],"app":"a"}}'), /service product p/);
});

test("a reset puts back each listed account, writes the three places, then checks each key", async () => {
  const h = harness();
  await h.run();
  for (const product of ["verikora", "other"]) {
    const key = `${KEY}-${product}`;
    for (const w of ["vault", "app", "watch"]) assert.ok(h.calls.some((c) => c[0] === w && c[1] === product && c[2] === key), `${w} ${product}`);
    assert.ok(h.calls.some((c) => c[0] === "check" && c[2] === `Bearer ${key}`));
  }
  assert.deepEqual(h.calls.find((c) => c[0] === "issue"), ["issue", "verikora", "compliance-check,document-authenticity"]);
  assert.equal(h.calls.filter((c) => c[0] === "check").length, 2);
  assert.match(h.calls.find((c) => c[0] === "check")[1], /\/edge\/api\/v1\/studios\/installations$/);
  const lastWrite = h.calls.map((c) => c[0]).lastIndexOf("watch");
  assert.ok(h.calls.findIndex((c) => c[0] === "check") > lastWrite, "the check comes after every write");
});

test("a key the edge refuses fails the reset, naming the product", async () => {
  const h = harness({ status: (auth) => (auth.endsWith("-verikora") ? 401 : 200) });
  await assert.rejects(h.run(), (e) => /service key check failed/.test(e.message) && /verikora/.test(e.message) && !/other:/.test(e.message));
  await assert.rejects(harness({ status: () => 504 }).run(), /HTTP 504/);
});

test("a writer that fails fails the reset naming the product, and the check never runs", async () => {
  for (const w of ["vault", "app", "watch"]) {
    const h = harness({ failWriter: w });
    await assert.rejects(h.run(), /service account of verikora not put back/);
    assert.ok(!h.calls.some((c) => c[0] === "check"));
  }
});

test("no key appears in anything the reset prints, nor in an error", async () => {
  const failing = await harness({ failWriter: "app" }).run().then(() => "", (e) => e.message);
  assert.match(failing, /verikora/);
  assert.doesNotMatch(failing, /oz_/);
  const ok = harness();
  const printed = [];
  const { log, error } = console;
  console.log = (...a) => printed.push(a.join(" "));
  console.error = (...a) => printed.push(a.join(" "));
  try {
    await ok.run();
  } finally {
    console.log = log;
    console.error = error;
  }
  for (const line of [...ok.out, ...printed]) assert.doesNotMatch(line, /oz_|Bearer/);
  assert.ok(ok.out.some((l) => /verikora: service account back/.test(l)));
});

test("an account the list cannot put back is named before anything is cleared", () => {
  assert.deepEqual(unrestorable(["verikora", "mystery"], CATALOG), ["mystery"]);
  assert.deepEqual(unrestorable(["verikora"], CATALOG), []);
});

test("the app's key is set as a SECRET on the components that declare it, and on no other", () => {
  const live = {
    name: "verikora-dev",
    services: [{ name: "web", envs: [{ key: "AI_API_KEY", scope: "RUN_TIME", type: "SECRET", value: "EV[1:old]" }, { key: "OTHER", value: "x" }] }],
    jobs: [{ name: "backup", envs: [{ key: "OTHER", value: "y" }] }],
  };
  const { spec, components } = specWithSecret(live, "AI_API_KEY", "oz_new");
  assert.deepEqual(components, ["web"]);
  assert.equal(spec.services[0].envs[0].value, "oz_new");
  assert.equal(spec.services[0].envs[0].type, "SECRET");
  assert.equal(spec.services[0].envs[0].scope, "RUN_TIME");
  assert.deepEqual(spec.jobs[0].envs, [{ key: "OTHER", value: "y" }]);
  assert.equal(live.services[0].envs[0].value, "EV[1:old]", "the live spec is not mutated");
  assert.throws(() => specWithSecret({ name: "a", services: [{ name: "w", envs: [] }] }, "AI_API_KEY", "v"), /no component of a declares AI_API_KEY/);
});

test("a product's vault file is the first file of its source in the secrets map", () => {
  const map = "# comment\ndev env/dev.env env/org.env\nverikora env/verikora.env env/dev.env\n";
  assert.equal(vaultFileOf(map, "verikora", "/priv", "/home/u"), "/priv/env/verikora.env");
  assert.equal(vaultFileOf("p ~/x/p.env", "p", "/priv", "/home/u"), "/home/u/x/p.env");
  assert.throws(() => vaultFileOf(map, "nope", "/priv", "/home/u"), /no vault source nope/);
});
