// The console is reachable from the allow-listed addresses only (AUTH_ALLOWED_IPS, DigitalOcean's do-connecting-ip).
import { test } from "node:test";
import assert from "node:assert/strict";
import { clientIp, ipAllowed, parseAllowList } from "../core/config/ip-allow.ts";

const h = (o: Record<string, string>) => ({ get: (n: string) => o[n.toLowerCase()] ?? null });

test("the list is comma-separated and trimmed", () => {
  assert.deepEqual(parseAllowList(" 1.2.3.4, 5.6.7.8 ,,"), ["1.2.3.4", "5.6.7.8"]);
  assert.deepEqual(parseAllowList(undefined), []);
});

test("production: only a listed do-connecting-ip passes", () => {
  const list = ["142.122.41.109"];
  assert.equal(ipAllowed(clientIp(h({ "do-connecting-ip": "142.122.41.109" }), true), list, true), true);
  assert.equal(ipAllowed(clientIp(h({ "do-connecting-ip": "203.0.113.9" }), true), list, true), false);
});

test("production: a spoofed x-forwarded-for is ignored", () => {
  const list = ["142.122.41.109"];
  assert.equal(clientIp(h({ "x-forwarded-for": "142.122.41.109" }), true), null);
  assert.equal(ipAllowed(clientIp(h({ "x-forwarded-for": "142.122.41.109" }), true), list, true), false);
});

test("production: an empty list lets nobody in; development lets everybody", () => {
  assert.equal(ipAllowed("142.122.41.109", [], true), false);
  assert.equal(ipAllowed(null, [], false), true);
});
