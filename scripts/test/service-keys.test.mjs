import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { logLine } from "../lib/dev-reset.mjs";
import { checkKeys, droppedAccountsAlert, keyVariable, keysToRevoke, productsOfAccounts, serviceAccountRefusal, serviceAccountsSql } from "../lib/service-keys.mjs";
import { main } from "../dev-service-keys.mjs";

const read = (rel) => readFileSync(new URL(rel, import.meta.url), "utf8");

test("the service accounts are read from the users table, the underscore escaped, and named by product", () => {
  assert.match(serviceAccountsSql(), /username LIKE 'svc\\_%' ESCAPE '\\'/);
  assert.deepEqual(productsOfAccounts("svc_verikora\nsvc_folio_viv\n\nsomeone"), ["verikora", "folio_viv"]);
  assert.deepEqual(productsOfAccounts(""), []);
});

test("clearing the users database is refused while a product's service account would go, and names the product", () => {
  const refusal = serviceAccountRefusal("krizaka_users_db", ["verikora"], []);
  assert.match(refusal, /verikora/);
  assert.match(refusal, /--drop-service-accounts verikora/);
  assert.equal(serviceAccountRefusal("krizaka_users_db", ["verikora"], ["verikora"]), null);
  assert.match(serviceAccountRefusal("krizaka_users_db", ["verikora", "folio"], ["verikora"]), /folio/);
  assert.equal(serviceAccountRefusal("krizaka_users_db", [], []), null);
  assert.equal(serviceAccountRefusal("krizaka_billing_db", ["verikora"], []), null);
});

test("a reset that drops accounts says so in its alert and in its run log", () => {
  assert.match(droppedAccountsAlert(["verikora"]), /verikora/);
  const line = JSON.parse(logLine({ actor: "a", at: "t", product: "orazaka", target: "dev", steps: [], result: "done", droppedServiceAccounts: ["verikora"] }));
  assert.deepEqual(line.droppedServiceAccounts, ["verikora"]);
});

test("the reset checks the service accounts before it clears anything, and the flag is documented", () => {
  const source = read("../dev-reset.mjs");
  const check = source.indexOf("serviceAccountRefusal(");
  assert.ok(check > 0);
  assert.ok(check < source.indexOf("clearSql(db)"), "the refusal comes before the clear");
  assert.match(source, /--drop-service-accounts/);
});

test("a rotation makes the new key first and revokes only the previous keys of that name", () => {
  const keys = [{ id: "1", name: "verikora-dev" }, { id: "2", name: "other" }, { id: "3", name: "verikora-dev" }];
  assert.deepEqual(keysToRevoke(keys, "verikora-dev", { id: "9" }).map((k) => k.id), ["1", "3"]);
  assert.deepEqual(keysToRevoke(keys, "verikora-dev", { id: "3" }).map((k) => k.id), ["1"]);
  const source = read("../dev-deploy.mjs");
  assert.ok(source.indexOf('body: scopes.length ? { name, scopes } : { name }') < source.indexOf("keysToRevoke("), "created before revoked");
  assert.match(source, /revoked the previous/);
});

test("the key check calls the installations endpoint with the product's key and accepts a 200", async () => {
  const calls = [];
  const fetchImpl = async (url, init) => (calls.push({ url, auth: init.headers.authorization }), { status: 200 });
  const [r] = await checkKeys({ products: ["verikora"], keyOf: () => "k", edgeUrl: "https://edge.test", fetchImpl });
  assert.equal(r.ok, true);
  assert.deepEqual(calls, [{ url: "https://edge.test/api/v1/studios/installations", auth: "Bearer k" }]);
});

test("a fake 401 fails the check and names the product; the key is never in the message", async () => {
  const lines = [];
  const code = await main(["verikora", "folioviv"], { SERVICE_KEY_VERIKORA: "oz_secret", SERVICE_KEY_FOLIOVIV: "oz_ok" },
    async (_url, init) => ({ status: init.headers.authorization.endsWith("oz_secret") ? 401 : 200 }), { log: (l) => lines.push(l) });
  assert.equal(code, 1);
  assert.ok(lines.some((l) => l.startsWith("::error title=Service key of verikora::") && l.includes("401")));
  assert.ok(lines.some((l) => l.includes("folioviv: the service key is accepted")));
  assert.ok(!lines.join("\n").includes("oz_secret"));
});

test("a product without its key is skipped, an unreachable edge and a 500 fail", async () => {
  assert.equal(await main(["verikora"], {}, async () => assert.fail("no call"), { log: () => {} }), 0);
  assert.equal(await main(["verikora"], { SERVICE_KEY_VERIKORA: "k" }, async () => ({ status: 500 }), { log: () => {} }), 1);
  assert.equal(await main(["verikora"], { SERVICE_KEY_VERIKORA: "k" }, async () => { throw new TypeError("fetch failed"); }, { log: () => {} }), 1);
  assert.equal(keyVariable("folio-viv"), "SERVICE_KEY_FOLIO_VIV");
});

test("the workflow runs the key check on a schedule with one secret per watched product", () => {
  const wf = read("../../.github/workflows/dev-service-keys.yml");
  assert.match(wf, /schedule:/);
  assert.match(wf, /SERVICE_KEY_VERIKORA: \$\{\{ secrets\.SERVICE_KEY_VERIKORA \}\}/);
  assert.match(wf, /node scripts\/dev-service-keys\.mjs/);
  assert.match(wf, /timeout-minutes:/);
});
