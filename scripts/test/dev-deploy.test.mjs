// node --test scripts/test — the dev deployment tool: specs are filled, never leaked, accounts are sound.
import assert from "node:assert/strict";
import { existsSync, mkdtempSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { connectionVars, grantSql, parseScopes, password, personaAccount, readEnvFile, render, seedSql, serviceAccount, upsertEnv, writeEnvKeys } from "../dev-deploy.mjs";

const spec = (name) => readFileSync(new URL(`../../infra/deploy/${name}.app.yaml`, import.meta.url), "utf8");

test("readEnvFile reads KEY=value lines, skips comments, keeps '=' in values", () => {
  assert.deepEqual(readEnvFile("# c\nA=1\n\nB=x=y\n"), { A: "1", B: "x=y" });
});

test("render fills every placeholder and names the missing ones without values", () => {
  assert.equal(render("a: __X__", { X: "1" }), "a: 1");
  assert.throws(() => render("a: __X__ __Y__", { X: "1" }), /missing in the secrets file: Y$/);
  assert.throws(() => render('a: "__X__"', { X: 'b"' }), /cannot quote/);
});

for (const name of ["sovkora-dev"]) {
  test(`${name}: every secret is a SECRET placeholder and sign-up stays closed`, () => {
    const text = spec(name);
    for (const line of text.split("\n").filter((l) => /type: SECRET/.test(l))) {
      assert.match(line, /value: "__[A-Z0-9_]+__"/, line);
    }
    for (const line of text.split("\n").filter((l) => /(PASSWORD|PASS|SECRET|KEY|SALT)\b/.test(l) && /key:/.test(l))) {
      if (/_USERNAME|_USER\b|DEFAULT_USER/.test(line)) continue;
      assert.match(line, /type: SECRET/, `not a SECRET: ${line}`);
    }
    assert.match(text, /AUTH_SIGNUP_ENABLED, scope: [A-Z_]+, value: "false"/);
    assert.doesNotMatch(text, /AUTH_SIGNUP_ENABLED, scope: [A-Z_]+, value: "true"/);
  });
}

test("sovkora-dev: only the edge, the web and the GPU relay are routed; the broker is reached over TLS in the VPC", () => {
  const text = spec("sovkora-dev");
  const routed = [...text.matchAll(/- component:\n\s+name: (\S+)/g)].map((m) => m[1]);
  assert.deepEqual(routed.sort(), ["core", "gpu-relay", "web"]);
  assert.match(text, /^vpc:\n  id: \S+/m);
  assert.match(text, /RABBITMQ_PORT, scope: RUN_TIME, value: "5671"/);
  assert.match(text, /SPRING_RABBITMQ_SSL_ENABLED, scope: RUN_TIME, value: "true"/);
});

test("sovkora-dev serves the operator console on admin.orazaka.com: alias domain, its own session and flag, IP allow-list", () => {
  const text = spec("sovkora-dev");
  assert.match(text, /- domain: www\.orazaka\.com\n    type: PRIMARY/);
  // The alias is in the spec since the cut-over of 2026-10-21 (the domain left `orazaka-admin-dev` first: a domain belongs to one app).
  assert.match(text, /- domain: admin\.orazaka\.com\n    type: ALIAS/);
  // Same `web` component: no component of its own, no extra route, nothing paid.
  assert.deepEqual([...text.matchAll(/^  - name: (\S+)/gm)].map((m) => m[1]).sort(), ["core", "gpu-relay", "web"]);
  // The console's own flag, key and allow-list; the public site's NEXTAUTH_SECRET stays a different placeholder.
  assert.match(text, /AUTH_CONSOLE_LOGIN_ENABLED, scope: RUN_TIME, value: "true"/);
  assert.match(text, /AUTH_CONSOLE_SECRET, scope: RUN_TIME, type: SECRET, value: "__NEXTAUTH_SECRET_ADMIN__"/);
  assert.match(text, /NEXTAUTH_SECRET, scope: RUN_TIME, type: SECRET, value: "__NEXTAUTH_SECRET_WEB__"/);
  assert.match(text, /AUTH_ALLOWED_IPS, scope: RUN_TIME, value: "[0-9., ]+"/);
  assert.match(text, /AUTH_TRUST_HOST, scope: RUN_TIME, value: "true"/);
  // The console's BFF reaches the edge inside the app, not through the public address.
  assert.match(text, /ROUTER_URL, scope: RUN_TIME, value: "http:\/\/core:8088"/);
});

test("broker cloud-init: TLS only, bound to the private address, secrets as placeholders", () => {
  const text = readFileSync(new URL("../../infra/deploy/broker/cloud-init.yaml", import.meta.url), "utf8");
  // cloud-init drops the whole file on a non-ASCII byte ("unacceptable character #x0080").
  assert.match(text, /^[\x00-\x7f]*$/);
  assert.match(text, /listeners\.tcp = none/);
  assert.match(text, /listeners\.ssl\.default = 5671/);
  assert.match(text, /-p \$\{PRIVATE_IP\}:5671:5671/);
  for (const p of ["BROKER_USERS_SCRIPT_B64", "BROKER_CA_PEM_B64", "BROKER_SERVER_PEM_B64", "BROKER_SERVER_KEY_B64"]) {
    assert.match(text, new RegExp(`__${p}__`));
  }
});

test("the GPU relay allows exactly the broker, the job service and the reverse ports of the Mac and the pods (LocalAI 8085, sd-server 8086, pipeline 8188, Ollama 11434)", () => {
  const entry = readFileSync(new URL("../../infra/deploy/gpu-relay/entrypoint.sh", import.meta.url), "utf8");
  const allowed = [...entry.matchAll(/^\s+"(\^[^"]+)",?$/gm)].map((m) => m[1]);
  assert.deepEqual(allowed, ["^broker-dev\\\\\\\\.orazaka\\\\\\\\.com:5671\\$", "^core:8090\\$", "^R:0\\\\\\\\.0\\\\\\\\.0\\\\\\\\.0:(8085|8086|8188|11434)\\$"]);
  assert.match(entry, /-lt 32/);
  const dockerfile = readFileSync(new URL("../../infra/deploy/gpu-relay/Dockerfile", import.meta.url), "utf8");
  assert.match(dockerfile, /sha256sum -c/);
});

test("the relay exposes LocalAI's port to the app as well as allowing it", () => {
  const spec = readFileSync(new URL("../../infra/deploy/sovkora-dev.app.yaml", import.meta.url), "utf8");
  assert.match(spec, /internal_ports: \[8085, 8086, 8188, 11434\]/);
});

test("www.orazaka.com is the public address; www serves /edge and the BFF; the apex answers 308 with www (epic krizaka/.github#119)", () => {
  const spec = readFileSync(new URL("../../infra/deploy/sovkora-dev.app.yaml", import.meta.url), "utf8").replace(/^\s*#.*$/gm, "");
  assert.match(spec, /- domain: www\.orazaka\.com\n\s+type: PRIMARY/);
  assert.match(spec, /- domain: orazaka\.com\n\s+type: ALIAS/);
  assert.doesNotMatch(spec, /domain: dev\.orazaka\.com/, "dev.orazaka.com was removed on 2026-11-05 (krizaka-internal#2600)");
  assert.match(spec, /- domain: admin\.orazaka\.com\n\s+type: ALIAS/, "the console alias stays");
  const rules = spec.slice(spec.indexOf("ingress:"));
  assert.match(rules, /exact: orazaka\.com[\s\S]*?authority: www\.orazaka\.com[\s\S]*?redirect_code: 308/);
  assert.match(rules, /name: core\s+match:\s+path:\s+prefix: \/edge/, "the engine's API is host-agnostic");
  assert.match(spec, /key: NEXTAUTH_URL, scope: RUN_TIME, value: "https:\/\/www\.orazaka\.com"/);
  assert.doesNotMatch(spec, /APP_DEV_HOST/, "no dev host to redirect any more");
  assert.match(spec, /key: APP_SEARCH_INDEXING, scope: RUN_AND_BUILD_TIME, value: "on"/, "www is indexed");
  const cors = /key: CORS_ALLOWED_ORIGINS[^\n]*value: "([^"]+)"/.exec(spec)?.[1].split(",");
  for (const origin of ["https://www.orazaka.com", "https://admin.orazaka.com"]) assert.ok(cors?.includes(origin), origin);
});

test("password: long, mixed, different each time", () => {
  const a = password();
  assert.ok(a.length >= 28 && /[A-Za-z]/.test(a) && /\d/.test(a) && /[^A-Za-z0-9]/.test(a));
  assert.notEqual(a, password());
});

test("seedSql gives the admin ROLE_ADMIN, the user only ROLE_USER, and quotes values", () => {
  const sql = seedSql([
    { username: "qa_user", email: "u@x", hash: "$2y$10$h", tier: "free", roles: ["ROLE_USER"] },
    { username: "o'q", email: "a@x", hash: "$2y$10$k", tier: "admin", roles: ["ROLE_USER", "ROLE_ADMIN"] },
  ]);
  assert.match(sql, /^BEGIN;[\s\S]*COMMIT;\n$/);
  assert.equal((sql.match(/'ROLE_ADMIN'/g) ?? []).length, 1);
  assert.match(sql, /'o''q'/);
  assert.match(sql, /ON CONFLICT \(username\) DO UPDATE/);
});


test("the demo persona on dev is a first name on a reserved e-mail domain, a plain user, never a QA account", () => {
  const p = personaAccount();
  assert.equal(p.username, "Eric");
  assert.match(p.email, /@example\.com$/);
  assert.deepEqual(p.roles, ["ROLE_USER"]);
  assert.equal(p.tier, "free");
  assert.doesNotMatch(`${p.username} ${p.email}`, /qa|admin|svc/i);
  const sql = seedSql([{ ...p, hash: "$2y$10$h" }]);
  assert.equal((sql.match(/'ROLE_ADMIN'/g) ?? []).length, 0);
});

test("brokerUsersScript: two users, the GPU worker confined to orazaka.* names, guest removed, quoting safe", async () => {
  const { brokerUsersScript, BROKER_PERMISSIONS } = await import("../dev-deploy.mjs");
  const script = brokerUsersScript({ orazaka: "core'pw", "gpu-worker": "gpu-pw" });
  assert.match(script, /delete_user guest/);
  assert.match(script, /add_user 'orazaka' 'core'\\''pw'/);
  assert.match(script, /set_permissions -p \/ 'gpu-worker' '\^orazaka\\\..\*'/);
  const re = new RegExp(BROKER_PERMISSIONS["gpu-worker"]);
  assert.ok(re.test("orazaka.jobs.batch") && !re.test("amq.rabbitmq.log"));
});

test("a product's service account is a plain user named after it, never an admin", () => {
  const a = serviceAccount("yomizaka");
  assert.equal(a.username, "svc_yomizaka");
  assert.equal(a.email, "svc+yomizaka@orazaka.com");
  assert.deepEqual(a.roles, ["ROLE_USER"]);
  assert.throws(() => serviceAccount("Yomizaka; DROP"), /not a product id/);
  assert.throws(() => serviceAccount(undefined), /not a product id/);
});

test("the Studio grants are one row per Studio, with a reason, idempotent, injection-proof", () => {
  const sql = grantSql("550e8400-e29b-41d4-a716-446655440002", ["compliance-check", "document-authenticity"], "yomizaka");
  assert.match(sql, /'studio\.compliance-check'/);
  assert.match(sql, /'studio\.document-authenticity'/);
  assert.match(sql, /yomizaka: a Krizaka product runs this Studio through Orazaka/);
  assert.match(sql, /ON CONFLICT \(actor_id, entitlement_key\) DO UPDATE/);
  assert.throws(() => grantSql("x", ["a'); DROP TABLE x; --"], "p"), /not a Studio key/);
  assert.throws(() => grantSql("x", [], "p"), /no Studio/);
  assert.throws(() => grantSql("", ["a"], "p"), /no actor id/);
});

test("upsertEnv replaces a key in place, appends a new one, keeps the rest", () => {
  assert.equal(upsertEnv("A=1\nAI_API_KEY=old\n", { AI_API_KEY: "new", B: "2" }), "A=1\nAI_API_KEY=new\nB=2\n");
  assert.equal(upsertEnv("", { A: "1" }), "A=1\n");
});

test("serverCertExtensions: what Python's strict verification requires of the broker's leaf", async () => {
  const { serverCertExtensions } = await import("../dev-deploy.mjs");
  const ext = serverCertExtensions("broker-dev.orazaka.com");
  assert.match(ext, /^subjectAltName=DNS:broker-dev\.orazaka\.com$/m);
  assert.match(ext, /^authorityKeyIdentifier=keyid,issuer$/m);
  assert.match(ext, /^subjectKeyIdentifier=hash$/m);
  assert.match(ext, /^basicConstraints=critical,CA:FALSE$/m);
});

test("CA_EXTENSIONS: a CA strict verification accepts (key usage present)", async () => {
  const { CA_EXTENSIONS } = await import("../dev-deploy.mjs");
  assert.ok(CA_EXTENSIONS.includes("keyUsage=critical,keyCertSign,cRLSign"));
  assert.ok(CA_EXTENSIONS.includes("basicConstraints=critical,CA:TRUE"));
});

test("connectionVars: the default pair for Studio callers, AI_<NAME>_URL / _API_KEY for an engine client", () => {
  assert.deepEqual(connectionVars(undefined, "https://e/edge", "oz_k"), { AI_PROVIDER: "orazaka", AI_API_URL: "https://e/edge", AI_API_KEY: "oz_k" });
  assert.deepEqual(connectionVars("AI_ENGINE", "https://e/edge", "oz_k"), { AI_ENGINE_URL: "https://e/edge", AI_ENGINE_API_KEY: "oz_k" });
  assert.throws(() => connectionVars("PRODUCT_AI", "u", "k"), /not an AI_ prefix/);
  assert.throws(() => connectionVars("AI_X; rm", "u", "k"), /not an AI_ prefix/);
});

test("parseScopes: area:action only, none = unscoped", () => {
  assert.deepEqual(parseScopes(undefined), []);
  assert.deepEqual(parseScopes("ai:embed, ai:chat"), ["ai:embed", "ai:chat"]);
  assert.throws(() => parseScopes("embed"), /not a scope/);
  assert.throws(() => parseScopes("AI:embed"), /not a scope/);
  assert.throws(() => parseScopes("*:*"), /not a scope/);
});

test("the core image build cache is busted at every apply: CORE_REBUILD is filled, and the Dockerfile reads it", () => {
  const core = spec("sovkora-dev");
  assert.match(core, /key: CORE_REBUILD, scope: BUILD_TIME, value: "__CORE_REBUILD__"/);
  const filled = render('X: "__CORE_REBUILD__"', {});
  assert.match(filled, /X: "\d{4}-\d{2}-\d{2}T[\d-]+Z"/);
  assert.match(readFileSync(new URL("../../infra/deploy/Dockerfile", import.meta.url), "utf8"), /ARG CORE_REBUILD[\s\S]*\$\{CORE_REBUILD\}/);
});

test("the recommender (ADR-078): knowledge runs in core with its own database, its migration is idempotent", () => {
  const dev = spec("sovkora-dev");
  assert.match(dev, /key: KNOWLEDGE_DB_URL, scope: RUN_TIME, value: "jdbc:postgresql:\/\/__DATABASE_HOST__:25061\/pool-knowledge\?sslmode=require&prepareThreshold=0"/);
  assert.match(dev, /key: KNOWLEDGE_DB_PASSWORD, scope: RUN_TIME, type: SECRET, value: "__DATABASE_PASSWORD_ORAZAKA_KNOWLEDGE_DB__"/);
  const docker = readFileSync(new URL("../../infra/deploy/Dockerfile", import.meta.url), "utf8");
  assert.match(docker, /services\/knowledge\/sovkora-knowledge-service[ ,]/);
  assert.match(docker, /\/out\/knowledge\.jar/);
  const runner = readFileSync(new URL("../../infra/deploy/run-services.sh", import.meta.url), "utf8");
  assert.match(runner, /knowledge:knowledge\.jar:\d+ [\w:. ]*edge:edge\.jar/, "knowledge starts before the edge");
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-15-recommendations.sql", import.meta.url), "utf8");
  for (const statement of sql.match(/CREATE (TABLE|INDEX) [^\n]*/g)) assert.match(statement, /IF NOT EXISTS/, statement);
  assert.match(sql, /EXCEPTION WHEN duplicate_object THEN NULL/);
  assert.match(sql, /ON CONFLICT \(tenant_id, version\) DO NOTHING/);
});

test("the analytics migration (ADR-081) is idempotent, seeds kinds and settings v1, and has its command", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-17-analytics.sql", import.meta.url), "utf8");
  for (const statement of sql.match(/CREATE (TABLE|INDEX) [^\n]*/g)) assert.match(statement, /IF NOT EXISTS/, statement);
  assert.match(sql, /CREATE OR REPLACE VIEW analytics_event_all/);
  assert.equal(sql.match(/EXCEPTION WHEN duplicate_object THEN NULL/g).length, 2);
  assert.match(sql, /ON CONFLICT \(tenant_id, kind\) DO NOTHING/);
  assert.match(sql, /ON CONFLICT \(tenant_id, version\) DO NOTHING/);
  const script = readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8");
  assert.match(script, /command === "analytics"\) analytics\(\)/);
});

test("sovkora-dev: outputs go to the private Bunny zone, the key is a SECRET, dev keeps them 30 days", () => {
  const text = spec("sovkora-dev");
  assert.match(text, /MEDIA_STORAGE_PROVIDER, scope: RUN_TIME, value: bunny/);
  assert.match(text, /MEDIA_STORAGE_ZONE, scope: RUN_TIME, value: orazaka-media-dev/);
  assert.match(text, /MEDIA_STORAGE_API_KEY, scope: RUN_TIME, type: SECRET, value: "__MEDIA_STORAGE_API_KEY__"/);
  assert.match(text, /MEDIA_STORAGE_ENDPOINT, scope: RUN_TIME, value: ny\.storage\.bunnycdn\.com/); // the zone's own region host
  assert.match(text, /MEDIA_RETENTION_DAYS, scope: RUN_TIME, value: "30"/);
});

test("connection budget: every JVM of the core goes through its PgBouncer pool (25061, prepareThreshold=0); only Flyway's migrator URLs are direct", () => {
  const dev = spec("sovkora-dev");
  const urls = [...dev.matchAll(/key: ((?:(?!MIGRATOR)\w)+_URL|SPRING_DATASOURCE_URL), scope: RUN_TIME, value: "(jdbc:postgresql:[^"]+)"/g)];
  assert.equal(urls.length, 6, "orazaka_db, users, studio, knowledge, billing, automation");
  for (const [, key, url] of urls) {
    assert.match(url, /:25061\/pool-[a-z]+\?sslmode=require&prepareThreshold=0$/, `${key} must use its pool`);
  }
  assert.doesNotMatch(dev, /__DATABASE_PORT__/, "no direct (25060) datasource left in the core");
});

test("sovkora-dev: Flyway never migrates through the transaction pooler (2026-11-04: studio, knowledge and automation V1 stalled and were cut)", () => {
  const dev = spec("sovkora-dev");
  for (const [svc, db] of [["STUDIO", "orazaka_studio_db"], ["KNOWLEDGE", "orazaka_knowledge_db"], ["AUTOMATION", "orazaka_automation_db"]]) {
    assert.match(dev, new RegExp(`key: DATABASE_${svc}_MIGRATOR_URL, scope: RUN_TIME, value: "jdbc:postgresql://__DATABASE_HOST__:25060/${db}\\?sslmode=require"`), `${svc} migrates on the direct port`);
  }
});

test("service accounts sit on the `service` rate-limit tier, whose migration is idempotent and moves the existing svc_* users", () => {
  assert.equal(serviceAccount("explorer").tier, "service");
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-20-service-rate-tier.sql", import.meta.url), "utf8");
  assert.match(sql, /VALUES \('service', 1000, 20, FALSE\)\s*ON CONFLICT \(tier_key\) DO NOTHING/);
  assert.match(sql, /INSERT INTO rate_limit_tiers \(id, capacity, refill_tokens, refill_seconds\)\s*VALUES \('service', 1000, 1000, 60\)/, "users.rate_limit_tier has a foreign key to rate_limit_tiers");
  assert.match(sql, /UPDATE users SET rate_limit_tier = 'service'\s*WHERE username LIKE 'svc\\_%'/);
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "service-tier"\) serviceTier\(\)/);
});

test("sovkora-dev: the universal-links settings are declared and empty by default (the web app then serves no AASA / assetlinks)", () => {
  const text = spec("sovkora-dev");
  assert.match(text, /key: APP_IOS_TEAM_ID, scope: RUN_TIME, value: "__APP_IOS_TEAM_ID__"/);
  assert.match(text, /key: APP_ANDROID_CERT_SHA256, scope: RUN_TIME, value: "__APP_ANDROID_CERT_SHA256__"/);
  assert.equal(render('X: "__APP_IOS_TEAM_ID__"', {}), 'X: ""');
});

test("the webhook migration is idempotent, seeds its limits as rows, and has its command", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-20-studio-webhooks.sql", import.meta.url), "utf8");
  for (const statement of sql.match(/CREATE (TABLE|INDEX) [^\n]*/g)) assert.match(statement, /IF NOT EXISTS/, statement);
  assert.match(sql, /ON CONFLICT \(config_key\) DO NOTHING/);
  for (const key of ["max-attempts", "backoff-seconds", "backoff-cap-seconds", "timeout-seconds", "batch-size"]) assert.match(sql, new RegExp(`webhook\\.${key}`));
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "webhooks"\) webhooks\(\)/);
});

test("dev-deploy reads its secrets through secret.sh @dev or --env-file, never a hard-coded file of the old layout", () => {
  const source = readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8");
  assert.doesNotMatch(source, new RegExp(["orazaka", "dev\\.env"].join("-")), "the company folder was restructured on 2026-10-18 (env/dev.env, env/org.env)");
  assert.match(source, /envFileFromArgs\(process\.argv/);
  assert.match(source, /devSecrets\(/);
  const audit = readFileSync(new URL("../audit-studio-forms.mjs", import.meta.url), "utf8");
  assert.doesNotMatch(audit, new RegExp(["orazaka", "dev\\.env"].join("-")));
});

test("the run-event migration is idempotent (triggers are dropped before they are created) and has its command", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-20-run-events.sql", import.meta.url), "utf8");
  assert.match(sql, /ADD COLUMN IF NOT EXISTS event_seq/);
  assert.match(sql, /CREATE TABLE IF NOT EXISTS studio_run_event/);
  for (const trigger of sql.match(/CREATE TRIGGER (\w+)/g)) {
    const name = trigger.split(" ")[2];
    assert.match(sql, new RegExp(`DROP TRIGGER IF EXISTS ${name} ON`), name);
  }
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "run-events"\) runEvents\(\)/);
});

test("the result cache lists compliance-check in dev, once, without dropping what an operator listed (ADR-089 §4)", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-23-studio-run-result-cache.sql", import.meta.url), "utf8");
  assert.match(sql, /\('run\.result-cache\.studios', 'document-authenticity,compliance-check', 'list'/);
  assert.match(sql, /ON CONFLICT \(config_key\) DO NOTHING;/);
  // Re-runnable: compliance-check is appended only where it is missing, the rest of the list is kept.
  assert.match(sql, /config_value \|\| ',compliance-check'/);
  assert.match(sql, /AND NOT \('compliance-check' = ANY \(string_to_array\(replace\(config_value, ' ', ''\), ','\)\)\);/);
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "run-result-cache"\) runResultCache\(\)/);
});

test("the run-link migration (orazaka-studio#62 parent, #63 resume) is additive, idempotent and has its command", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-23-studio-run-parent.sql", import.meta.url), "utf8");
  assert.match(sql, /ADD COLUMN IF NOT EXISTS parent_run_id UUID REFERENCES studio_run\(id\) ON DELETE CASCADE/);
  assert.match(sql, /CREATE INDEX IF NOT EXISTS idx_studio_run_parent/);
  assert.match(sql, /ADD COLUMN IF NOT EXISTS resumed_from UUID;/);
  assert.doesNotMatch(sql, /DROP (TABLE|COLUMN)/);
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "run-parent"\) runParent\(\)/);
});

test("the runtime-config admin migration (orazaka-studio#72, #71) is additive, idempotent and has its command", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-23-studio-runtime-config-admin.sql", import.meta.url), "utf8");
  assert.match(sql, /\\c orazaka_studio_db/);
  for (const column of ["default_value TEXT", "min_value BIGINT", "max_value BIGINT", "max_items INTEGER"]) {
    assert.match(sql, new RegExp(`ADD COLUMN IF NOT EXISTS ${column}`), column);
  }
  assert.match(sql, /CREATE TABLE IF NOT EXISTS studio_runtime_config_audit/);
  assert.match(sql, /CREATE INDEX IF NOT EXISTS idx_studio_runtime_config_audit_at/);
  // The saga's two settings exist on dev, with their bounds, and an admin's value survives a re-run.
  for (const key of ["run.blueprint-cache-size", "run.outcome-listener.concurrency"]) assert.ok(sql.includes(`'${key}'`), key);
  assert.match(sql, /ON CONFLICT \(config_key\) DO UPDATE SET\s+default_value = EXCLUDED\.default_value/);
  assert.doesNotMatch(sql, /config_value = EXCLUDED/);
  assert.doesNotMatch(sql, /DROP (TABLE|COLUMN)/);
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "runtime-config-admin"\) runtimeConfigAdmin\(\)/);
});

test("the searchKnowledge rename (orazaka-ai-engine#59) is copied, additive and idempotent, and has its command", () => {
  const tool = readFileSync(new URL("../../infra/migrations/2026-10-23-search-knowledge-tool.sql", import.meta.url), "utf8");
  const sources = readFileSync(new URL("../../infra/migrations/2026-10-23-search-knowledge-sources.sql", import.meta.url), "utf8");
  for (const sql of [tool, sources]) {
    assert.match(sql, /SELECT 'searchKnowledge'/);
    assert.match(sql, /WHERE tool_id = 'searchWeb'/);
    assert.match(sql, /ON CONFLICT \(tool_id(, content)?\) DO NOTHING/);
    assert.doesNotMatch(sql, /\b(DELETE|DROP|UPDATE)\b/);
  }
  assert.match(sources, /\\c orazaka_knowledge_db/);
  assert.ok(!existsSync(new URL("../../infra/initdb/90-dev-fixtures.sql", import.meta.url)), "dev data is seeded by dev-reset, never by initdb");
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "search-knowledge"\) searchKnowledge\(\)/);
});

test("the MCP token columns become TEXT for sealed envelopes (orazaka-ai-engine#58), keeping every value", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-23-mcp-token-text.sql", import.meta.url), "utf8");
  for (const table of ["user_mcp_servers", "platform_mcp_servers"]) {
    assert.ok(sql.includes(`ALTER TABLE ${table} ALTER COLUMN auth_token TYPE TEXT;`), table);
  }
  assert.doesNotMatch(sql, /\b(DROP|DELETE|UPDATE)\b/);
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "mcp-token-text"\) mcpTokenText\(\)/);
});

test("the research tables (ADR-087 slice 1) are additive, idempotent, off by default and have their command", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-23-research-tables.sql", import.meta.url), "utf8");
  for (const table of ["research_source", "research_document", "research_fetch", "research_consent"]) {
    assert.ok(sql.includes(`CREATE TABLE IF NOT EXISTS ${table} (`), table);
  }
  assert.ok(sql.includes("('research.enabled', 'false', 'boolean'"));
  // The design of ADR-087 §5: a hit is served only through the source that read it, and a citation names the final URL.
  assert.ok(sql.includes("PRIMARY KEY (source_id, url, content_hash)"));
  assert.ok(sql.includes("final_url      TEXT        NOT NULL"));
  assert.ok(sql.includes("('research.max-calls', '16', 'int'"));
  // The same block as orazaka-job-service's initdb, so a fresh volume and a migrated database agree.
  const jobService = new URL("../../services/job/sovkora-job-service/infra/initdb/30-jobs-config.sql", import.meta.url);
  // (Checked when the workspace holds a job-service checkout that already carries the block — orazaka-job-service#65.)
  const block = existsSync(jobService)
    ? readFileSync(jobService, "utf8").match(/-- =+\n-- GROUNDED RESEARCH \(ADR-087[\s\S]*?ON CONFLICT \(source_key\) DO NOTHING;/)
    : null;
  if (block) {
    assert.ok(sql.includes(block[0]), "the migration carries the job-service block verbatim");
  }
  assert.match(sql, /ON CONFLICT \(config_key\) DO NOTHING/);
  assert.match(sql, /ON CONFLICT \(source_key\) DO NOTHING/);
  assert.ok(sql.includes("('CitationVerifierInterceptor','Citation Verifier Interceptor',12, FALSE,"));
  // Foreign keys say ON DELETE; no statement drops, deletes or updates rows.
  assert.doesNotMatch(sql.replace(/ON DELETE (SET NULL|CASCADE)/g, ""), /\b(DROP|DELETE|UPDATE)\b/);
  assert.doesNotMatch(sql, /, TRUE\)\s*(,|ON CONFLICT)/);
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "research-tables"\) researchTables\(\)/);
});

test("the law corpus (ADR-089 cut 2) is additive, idempotent, every switch off, and has its command", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-24-law-corpus.sql", import.meta.url), "utf8");
  for (const table of ["law_instrument", "law_article_version", "law_import", "law_retrieval", "rule_proposal", "kind_demand"]) {
    assert.ok(sql.includes(`CREATE TABLE IF NOT EXISTS ${table} (`), table);
  }
  assert.ok(sql.includes("ADD COLUMN IF NOT EXISTS default_value"));
  assert.ok(sql.includes("PRIMARY KEY (instrument_id, ref, language, in_force_from)"));
  // Every switch is off: the corpus, the lecture, and both sources.
  assert.ok(sql.includes("('law.corpus.enabled', 'false', 'boolean'"));
  assert.ok(sql.includes("('law.lecture.enabled', 'false', 'boolean'"));
  assert.ok(sql.includes("('law-corpus', 'OFFICIAL', 'law-corpus', NULL, '{}', 86400, FALSE)"));
  assert.ok(sql.includes("604800, FALSE)"));
  assert.doesNotMatch(sql, /, TRUE\)/);
  assert.equal((sql.match(/\('law\./g) ?? []).length, 13);
  // The cache is keyed by language too (a French retrieval is never served for English).
  assert.match(sql, /language\s+VARCHAR\(2\)\s+NOT NULL CHECK \(language IN \('fr','en'\)\)/);
  assert.ok(sql.includes("PRIMARY KEY (jurisdiction, kind_id, language, as_of_bucket)"));
  // ... and on a database that has #69's table (no language): ALTER, guarded so it runs once.
  assert.match(sql, /IF NOT EXISTS \(SELECT 1 FROM information_schema\.columns\s+WHERE table_name = 'law_retrieval' AND column_name = 'language'\) THEN\s+ALTER TABLE law_retrieval ADD COLUMN language VARCHAR\(2\) NOT NULL CHECK \(language IN \('fr','en'\)\);/);
  assert.match(sql, /ALTER TABLE law_retrieval ADD PRIMARY KEY \(jurisdiction, kind_id, language, as_of_bucket\);/);
  // The same block as orazaka-job-service's initdb, so a fresh volume and a migrated database agree.
  const jobService = new URL("../../services/job/sovkora-job-service/infra/initdb/30-jobs-config.sql", import.meta.url);
  const block = existsSync(jobService)
    ? readFileSync(jobService, "utf8").match(/-- =+\n-- LAW CORPUS \(ADR-089[\s\S]*?ON CONFLICT \(source_key\) DO NOTHING;/)
    : null;
  if (block) {
    assert.ok(sql.includes(block[0]), "the migration carries the job-service block verbatim");
  }
  assert.match(sql, /ON CONFLICT \(config_key\) DO NOTHING/);
  assert.match(sql, /ON CONFLICT \(source_key\) DO NOTHING/);
  // Nothing is dropped, deleted or rewritten: foreign keys say ON UPDATE CASCADE, the rest is additive.
  assert.doesNotMatch(sql.replace(/ON UPDATE CASCADE/g, "").replace("DROP CONSTRAINT law_retrieval_pkey", ""), /\b(DROP|DELETE|UPDATE)\b/, "the only DROP is the cache key of job-service#69, replaced by the one with the language");
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "law-corpus"\) lawCorpus\(\)/);
});

test("the external checks (orazaka-packs#138) are additive, idempotent, fail closed on dev, and have their command", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-25-external-checks.sql", import.meta.url), "utf8");
  const code = sql.replace(/--.*$/gm, ""); // the statements, without the comments that explain them
  // The three checks: enabled, one handler (DB-driven routing), drained by the job service, billed per CALL.
  for (const key of ["courtrecords", "credit", "identity"]) {
    assert.ok(code.includes(`('orazaka.core.checks.${key}', 'check.external', 'job.text.process', 'CALL', 'EXTERNAL_CHECK', 'INTERACTIVE',`), key);
  }
  assert.equal((code.match(/\('orazaka\.core\.checks\./g) ?? []).length, 3);
  assert.equal((code.match(/::jsonb, true\)/g) ?? []).length, 3);
  assert.match(code, /ON CONFLICT \(feature_key\) DO NOTHING;/);
  // The billable_capability check gets its name and accepts EXTERNAL_CHECK; the old unnamed one is dropped if present.
  assert.ok(code.includes("DROP CONSTRAINT IF EXISTS orazaka_capabilities_billable_capability_check;"));
  assert.ok(code.includes("DROP CONSTRAINT IF EXISTS ck_orazaka_capabilities_billable_capability;"));
  assert.match(code, /ADD CONSTRAINT ck_orazaka_capabilities_billable_capability\s+CHECK \(billable_capability IN \('CHAT','IMAGE','AUDIO','VIDEO','AGENT','EXTERNAL_CHECK'\)\);/);
  // The audit: append-only, the subject only as an HMAC, refusal if and only if REFUSED.
  assert.ok(code.includes("CREATE TABLE IF NOT EXISTS external_check_audit ("));
  assert.ok(code.includes("subject_ref   CHAR(64)     NOT NULL"));
  const audit = code.match(/CREATE TABLE IF NOT EXISTS external_check_audit \(([\s\S]*?)\n\);/)[1];
  assert.doesNotMatch(audit, /\b(name|last_name|first_name|birth_date|address)\b/, "no personal-data column");
  assert.ok(code.includes("CHECK ((outcome = 'REFUSED') = (refusal IS NOT NULL))"));
  assert.ok(code.includes("DROP TRIGGER IF EXISTS trg_external_check_audit_append_only ON external_check_audit;"));
  assert.match(code, /CREATE TRIGGER trg_external_check_audit_append_only BEFORE UPDATE ON external_check_audit/);
  // Off on dev until an operator seals a pepper: no checks.subject-pepper row is seeded (no row, no check).
  assert.doesNotMatch(code, /subject-pepper/);
  assert.doesNotMatch(code, /INSERT INTO (?!orazaka_capabilities)/, "the capability rows are the only rows it writes");
  // Nothing is deleted or rewritten; the only DROPs are the IF EXISTS of a constraint or trigger replaced by name.
  assert.doesNotMatch(code.replace(/DROP (CONSTRAINT|TRIGGER) IF EXISTS/g, "").replace("BEFORE UPDATE ON", ""), /\b(DROP|DELETE|UPDATE|TRUNCATE)\b/);
  // The same block as orazaka-job-service's initdb, so a fresh volume and a migrated database agree.
  const jobService = new URL("../../services/job/sovkora-job-service/infra/initdb/30-jobs-config.sql", import.meta.url);
  // (Checked when the workspace holds a job-service checkout that already carries the block — orazaka-job-service#74.)
  const block = existsSync(jobService)
    ? readFileSync(jobService, "utf8").match(/-- =+\n-- EXTERNAL CHECKS \(orazaka-packs#138[\s\S]*?ON CONFLICT \(feature_key\) DO NOTHING;/)
    : null;
  if (block) {
    // Statements only (comments are history), and the billable capabilities as this migration wrote them: NONE was added
    // by 2026-10-27-capability-billable-none.sql (orazaka-packs#162), which the initdb carries from the start.
    const statements = (text) => text.replace(/--.*$/gm, "").replace("'EXTERNAL_CHECK','NONE'", "'EXTERNAL_CHECK'").replace(/\s+/g, " ");
    assert.ok(statements(sql).includes(statements(block[0])), "the migration carries the job-service block verbatim");
  }
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "external-checks"\) externalChecks\(\)/);
});

test("the price book rows of the external checks and of research (krizaka-billing#57) are billing's migration, run on krizaka_billing_db", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-25-pricebook-external-check-research.sql", import.meta.url), "utf8");
  const code = sql.replace(/--.*$/gm, "");
  assert.match(code, /\(2, 'EXTERNAL_CHECK', 'mock',\s+'CALL', /);
  assert.match(code, /\(2, 'RESEARCH',\s+'law-corpus', 'CALL', /);
  assert.equal((code.match(/^\(2, '/gm) ?? []).length, 2);
  assert.match(code, /ON CONFLICT DO NOTHING;/);
  assert.doesNotMatch(code, /\b(DROP|DELETE|UPDATE|TRUNCATE)\b/);
  // The same file as krizaka-billing's own migration (checked when the workspace holds a billing checkout that has it).
  const billing = new URL("../../.krizaka-java/krizaka-billing/infra/migrations/2026-10-25-pricebook-external-check-research.sql", import.meta.url);
  if (existsSync(billing)) {
    assert.equal(sql, readFileSync(billing, "utf8"), "the migration is billing's, unchanged");
  }
  const deploy = readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8");
  assert.match(deploy, /command === "pricebook-external-check-research"\) pricebookExternalCheckResearch\(\)/);
  assert.match(deploy, /function pricebookExternalCheckResearch\(\) \{[^}]*"krizaka_billing_db", "krizaka_billing_db", "DATABASE_PASSWORD_KRIZAKA_BILLING_DB"/);
});

test("sovkora-dev is indexed: APP_SEARCH_INDEXING is on (krizaka-com AGENTS 2.4; it drifted to off twice and hid the site from Google)", () => {
  assert.match(spec("sovkora-dev"), /key: APP_SEARCH_INDEXING, scope: RUN_AND_BUILD_TIME, value: "on"/);
});

test("sovkora-dev enforces the per-key rate limit (ADR-083 slice 2: service accounts are on the `service` tier, 1000 a minute)", () => {
  assert.match(spec("sovkora-dev"), /key: ABUSE_RATE_LIMIT_MODE, scope: RUN_TIME, value: enforce/);
});

test("automations (ADR-084): the service is the eighth JVM of the core, with its own database through a pool of ONE", () => {
  const dev = spec("sovkora-dev");
  assert.match(dev, /key: AUTOMATION_DB_URL, scope: RUN_TIME, value: "jdbc:postgresql:\/\/__DATABASE_HOST__:25061\/pool-automation\?sslmode=require&prepareThreshold=0"/);
  assert.match(dev, /key: AUTOMATION_DB_USERNAME, scope: RUN_TIME, value: orazaka_automation_db/);
  assert.match(dev, /key: AUTOMATION_DB_PASSWORD, scope: RUN_TIME, type: SECRET, value: "__DATABASE_PASSWORD_ORAZAKA_AUTOMATION_DB__"/);
  assert.match(dev, /key: AUTOMATION_DB_MAX_POOL_SIZE, scope: RUN_TIME, value: "1"/, "the pool is one connection: the code holds none across a remote call");
  assert.match(dev, /key: AUTOMATION_PORT, scope: RUN_TIME, value: "8091"/);
  assert.match(dev, /key: AUTOMATION_INTERNAL_URL, scope: RUN_TIME, value: "http:\/\/localhost:8091"/, "the edge routes /api/v1/automations and /hooks/ to it");
  assert.match(dev, /key: STUDIO_INTERNAL_URL, scope: RUN_TIME, value: "http:\/\/localhost:8096"/, "it starts Studio runs over the Studio service's REST surface");
  assert.match(dev, /key: APP_PUBLIC_EDGE_URL, scope: RUN_TIME, value: "https:\/\/www\.orazaka\.com\/edge"/, "the address a sender is shown");
  const docker = readFileSync(new URL("../../infra/deploy/Dockerfile", import.meta.url), "utf8");
  assert.match(docker, /services\/automation\/sovkora-automation-service \\/);
  assert.match(docker, /\/out\/automation\.jar/);
  const runner = readFileSync(new URL("../../infra/deploy/run-services.sh", import.meta.url), "utf8");
  assert.match(runner, /automation:automation\.jar:\d+ edge:edge\.jar/, "automation starts before the edge");
});

test("the automation migration is idempotent, extends the rule set without replacing it, and has its command", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-20-automations.sql", import.meta.url), "utf8");
  for (const statement of sql.match(/CREATE (TABLE|INDEX|UNIQUE INDEX) [^\n]*/g)) assert.match(statement, /IF NOT EXISTS/, statement);
  for (const statement of sql.match(/ALTER TABLE [^\n]*ADD COLUMN [^\n]*/g)) assert.match(statement, /ADD COLUMN IF NOT EXISTS/, statement);
  assert.match(sql, /ON CONFLICT \(version\) DO NOTHING/);
  assert.match(sql, /secret_sealed/, "a webhook secret is stored sealed");
  assert.doesNotMatch(sql, /\bsecret\s+(VARCHAR|TEXT)/i, "never a clear secret column");
  assert.match(sql, /PRIMARY KEY \(trigger_id, webhook_id\)/, "the replay guard is the primary key");
  const script = readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8");
  assert.match(script, /command === "automations"\) automations\(\)/);
});

test("the automations migration is the model part of the service's Flyway V1 baseline (no drift) — judged when the service is cloned", (t) => {
  const initdb = new URL("../../services/automation/sovkora-automation-service/src/main/resources/db/migration/V1__automation_baseline.sql", import.meta.url);
  if (!existsSync(initdb)) return t.skip("orazaka-automation-service is not cloned here");
  const marker = "-- ─────────────────────────────────────────────────────────────────────────────\n-- THE AUTOMATION MODEL";
  const bootstrap = readFileSync(initdb, "utf8");
  const migration = readFileSync(new URL("../../infra/migrations/2026-10-20-automations.sql", import.meta.url), "utf8");
  assert.ok(bootstrap.includes(marker), "the model marker moved");
  assert.equal(migration.slice(migration.indexOf(marker)), bootstrap.slice(bootstrap.indexOf(marker)));
});

test("the dev persona Mira: a fictional plain user on a reserved .test domain, never admin, her own secret keys", () => {
  const m = personaAccount("mira");
  assert.equal(m.username, "Mira");
  assert.match(m.email, /@orazaka\.test$/);
  assert.deepEqual(m.roles, ["ROLE_USER"]);
  assert.equal(m.secret, "MIRA");
  assert.equal(personaAccount().username, "Eric");
  assert.throws(() => personaAccount("admin"), /unknown persona/);
});

test("a product's key comes with its OWN plan, never a shared one", async () => {
  const { planSql } = await import("../dev-deploy.mjs");
  const yomizaka = planSql("yomizaka", "u-1");
  const akinaza = planSql("akinaza", "u-2");
  assert.match(yomizaka, /'svc-yomizaka'/);
  assert.ok(!yomizaka.includes("svc-akinaza") && !akinaza.includes("svc-yomizaka"));
  assert.match(yomizaka, /'retention\.none', 'boolean', 'true'/);
  assert.match(yomizaka, /is_public[\s\S]*FALSE/);
  assert.throws(() => planSql("Bad Id", "u"), /not a product id/);
});

test("the Krizaka account is the company as an SME customer: a plain user, free plan, French — never an admin or a service account", () => {
  const k = personaAccount("krizaka");
  assert.equal(k.username, "Krizaka");
  assert.equal(k.email, "orazaka@krizaka.com");
  assert.deepEqual(k.roles, ["ROLE_USER"]);
  assert.equal(k.tier, "free");
  assert.equal(k.secret, "KRIZAKA");
  const sql = seedSql([{ ...k, hash: "$2y$10$h" }]);
  assert.match(sql, /'\{"language":"fr"\}'/);
  assert.equal((sql.match(/'ROLE_ADMIN'/g) ?? []).length, 0);
  assert.doesNotMatch(k.username, /^svc_/);
  assert.match(seedSql([{ ...personaAccount(), hash: "h" }]), /'\{"language":"en"\}'/, "the others stay in English");
});

test("models: the bake-off winners, SeedVR2 and the video capability's `upscale` input, idempotent, and the command exists (password from the secrets file, never printed)", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-17-bakeoff-models.sql", import.meta.url), "utf8");
  for (const name of ["ltx-video-0.9.8-13b-distilled", "wan2.2-i2v-a14b-hq", "seedvr2-3b"]) {
    assert.match(sql, new RegExp(`\\('${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}',`), name);
    assert.match(sql, new RegExp(`UPDATE orazaka_models SET [^\\n]*WHERE model_name = '${name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}';`), `${name} has its registry metadata`);
  }
  assert.match(sql, /ON CONFLICT \(model_name\) DO NOTHING/);
  assert.match(sql, /"upscale": \{"type": "string", "enum": \["none", "1080p", "2160p"\], "default": "none"/);
  assert.match(sql, /WHERE feature_key = 'orazaka\.core\.media\.video'/);
  assert.doesNotMatch(sql, /^\\c /m); // the command connects to orazaka_db itself
  const deploy = readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8");
  assert.match(deploy, /command === "models"\) models\(\)/);
  assert.match(deploy, /DATABASE_PASSWORD_ORAZAKA_DB/);
});

test("writeEnvKeys replaces only its own keys in place, keeps every other line, and the file stays 0600", () => {
  const file = join(mkdtempSync(join(tmpdir(), "qa-store-")), "store.env");
  writeFileSync(file, "# Section 11\nOTHER_KEY=keep-me\n\nORAZAKA_USER_PASSWORD=old\n", { mode: 0o644 });
  writeEnvKeys(file, { ORAZAKA_USER_PASSWORD: "new", ORAZAKA_USER_EMAIL: "qa+user@orazaka.com" });
  assert.equal(readFileSync(file, "utf8"), "# Section 11\nOTHER_KEY=keep-me\n\nORAZAKA_USER_PASSWORD=new\nORAZAKA_USER_EMAIL=qa+user@orazaka.com\n");
  assert.equal(statSync(file).mode & 0o777, 0o600, "an existing readable file is closed before the secret lands");
  assert.deepEqual(readdirSync(dirname(file)), ["store.env"], "no temporary file is left behind");
});

test("the seed stores the QA accounts in the QA store, never in a document", () => {
  const code = readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8");
  const seed = code.slice(code.indexOf("function seed() {"), code.indexOf("\n}\n", code.indexOf("function seed() {")));
  for (const key of ["ORAZAKA_USER_EMAIL", "ORAZAKA_USER_PASSWORD", "ORAZAKA_ADMIN_EMAIL", "ORAZAKA_ADMIN_PASSWORD"]) assert.ok(seed.includes(`storeSecret("${key}"`), key);
  assert.doesNotMatch(code, /QA\.md|Mot de passe/, "no password goes to a Markdown sheet");
  assert.doesNotMatch(code, /appendFileSync\(ENV_FILE/, "a key is replaced, never appended twice");
});

test("the semantic cache (orazaka-ai-engine#40) is additive, idempotent, OFF on dev, and has its command", () => {
  const sql = readFileSync(new URL("../../infra/migrations/2026-10-22-semantic-cache.sql", import.meta.url), "utf8");
  const code = sql.replace(/--.*$/gm, "");
  assert.ok(code.includes("CREATE EXTENSION IF NOT EXISTS vector;"));
  assert.ok(code.includes("CREATE TABLE IF NOT EXISTS semantic_cache_entry ("));
  assert.match(code, /CONSTRAINT uq_semantic_cache_key UNIQUE \(tenant_id, scope_guard_hash, model_tier, exact_key\)/, "an entry is never shared across tenants");
  assert.match(code, /CREATE INDEX IF NOT EXISTS idx_semantic_cache_expiry/);
  assert.match(code, /CREATE INDEX IF NOT EXISTS idx_semantic_cache_embedding\s+ON semantic_cache_entry USING hnsw \(embedding vector_cosine_ops\)/);
  // OFF: the interceptor row is disabled, and an existing row (an operator's choice) is never overwritten.
  assert.match(code, /\('SemanticCacheInterceptor','Semantic Cache Interceptor',8, FALSE,/);
  assert.match(code, /ON CONFLICT \(interceptor_key\) DO NOTHING;/);
  assert.doesNotMatch(code, /\b(DROP|DELETE|UPDATE|TRUNCATE)\b/);
  assert.doesNotMatch(code, /^\\c /m, "the command connects to orazaka_db itself");
  // The same table as orazaka-job-service's initdb, so a fresh volume and a migrated database agree.
  const jobService = new URL("../../services/job/sovkora-job-service/infra/initdb/30-jobs-config.sql", import.meta.url);
  const table = existsSync(jobService)
    ? readFileSync(jobService, "utf8").match(/CREATE TABLE IF NOT EXISTS semantic_cache_entry \([\s\S]*?USING hnsw \(embedding vector_cosine_ops\);/)
    : null;
  if (table) assert.ok(sql.includes(table[0]), "the migration carries the job-service table verbatim");
  assert.match(readFileSync(new URL("../dev-deploy.mjs", import.meta.url), "utf8"), /command === "semantic-cache"\) semanticCache\(\)/);
});

test("the Eric persona carries the Studio grants the product shows (compliance-check), the others none", () => {
  assert.deepEqual(personaAccount().studios, ["compliance-check"]);
  assert.equal(personaAccount("mira").studios, undefined);
  assert.match(grantSql("id-1", personaAccount().studios, "persona Eric"), /'studio\.compliance-check'/);
});

test("seedSql never creates a second account for an e-mail that already signs in (eric.morel login 500: two rows, « Éric » renamed by `names` and a new « Eric »)", () => {
  const sql = seedSql([{ ...personaAccount(), hash: "$2y$10$h" }]);
  assert.match(sql, /UPDATE users SET[^;]*WHERE lower\(email\) = lower\('eric\.morel@example\.com'\)/, "an existing account is refreshed by e-mail, whatever its display name");
  assert.match(sql, /WHERE NOT EXISTS \(SELECT 1 FROM users WHERE lower\(email\) = lower\('eric\.morel@example\.com'\)\)/, "the insert is skipped when the e-mail exists");
  assert.match(sql, /FROM users WHERE lower\(email\) = lower\('eric\.morel@example\.com'\) ON CONFLICT/, "authorities follow the e-mail, not the username");
});

test("personaActorSql finds the persona by e-mail, so the Studio grant lands on the account that signs in", async () => {
  const { personaActorSql } = await import("../dev-deploy.mjs");
  assert.match(personaActorSql(personaAccount()), /WHERE lower\(email\) = lower\('eric\.morel@example\.com'\)/);
});

test("re-running `persona eric` keeps his password: the secret is reused, the hash is replaced only when absent or not matching", async () => {
  const { planPersonaPassword } = await import("../dev-deploy.mjs");
  const matches = (pw, hash) => hash === `h(${pw})`;
  // first run: no secret, no account → a new password and its hash, both stored
  const first = planPersonaPassword({ stored: "", hash: "", matches, generate: () => "new-pw" });
  assert.deepEqual({ ...first }, { password: "new-pw", setHash: true, storeSecret: true });
  // re-run: the secret is there and the stored hash matches → nothing changes
  assert.deepEqual({ ...planPersonaPassword({ stored: "kept", hash: "h(kept)", matches, generate: () => "other" }) }, { password: "kept", setHash: false, storeSecret: false });
  // drift: the account holds another password → the hash follows the secret (the secret never moves)
  assert.deepEqual({ ...planPersonaPassword({ stored: "kept", hash: "h(old)", matches, generate: () => "other" }) }, { password: "kept", setHash: true, storeSecret: false });
  // account created by hand, secret lost → a new password for both
  assert.deepEqual({ ...planPersonaPassword({ stored: "", hash: "h(x)", matches, generate: () => "new-pw" }) }, { password: "new-pw", setHash: true, storeSecret: true });
});

test("seedSql without a hash leaves the stored password alone (update and conflict), with one it sets it", () => {
  const keep = seedSql([{ ...personaAccount(), hash: undefined }]);
  assert.doesNotMatch(keep.split("INSERT")[0], /password_hash/, "the UPDATE does not touch password_hash");
  assert.doesNotMatch(keep.split("ON CONFLICT (username)")[1].split(";")[0], /password_hash/, "nor does the username conflict branch");
  assert.match(seedSql([{ ...personaAccount(), hash: "$2y$10$h" }]), /UPDATE users SET password_hash = '\$2y\$10\$h'/);
});

test("sovkora-dev: DigitalOcean alerts the team on a failed deployment or domain, memory and restarts (the same as krizaka-com)", () => {
  const text = spec("sovkora-dev");
  assert.match(text, /^alerts:\n  - rule: DEPLOYMENT_FAILED\n  - rule: DOMAIN_FAILED\n/m);
  const services = text.split(/^services:\n/m)[1].split(/^ {2}- name: /m).slice(1);
  assert.deepEqual(services.map((s) => s.split("\n")[0]).sort(), ["core", "gpu-relay", "web"]);
  for (const service of services) {
    assert.match(service, /^ {4}alerts:\n {6}- rule: MEM_UTILIZATION\n {8}operator: GREATER_THAN\n {8}value: 85\n/m, service.split("\n")[0]);
    assert.match(service, /- rule: RESTART_COUNT\n {8}operator: GREATER_THAN\n {8}value: 2\n/, service.split("\n")[0]);
  }
});
