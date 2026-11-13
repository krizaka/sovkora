// node --test scripts/test — the pilot's prod broker (ADR SOV-105, krizaka-internal#2132): the dev cloud-init, with prod's
// own values only where they differ, reachable from the prod VPC alone over TLS, and holding nothing worth keeping.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const read = (path) => readFileSync(new URL(`../../${path}`, import.meta.url), "utf8");
const prod = JSON.parse(read("infra/deploy/broker/prod.json"));
const cloudInit = read("infra/deploy/broker/cloud-init.yaml");
const spec = read("infra/deploy/sovkora-prod.app.yaml");

test("the prod broker is the dev size in Toronto, in the prod VPC, under its own name", () => {
  assert.equal(prod.region, "tor1");
  assert.equal(prod.size, "s-1vcpu-512mb-10gb");
  assert.match(prod.vpc, /prod/);
  for (const value of [prod.name, prod.tag, prod.vpc, prod.host]) assert.doesNotMatch(value, /(^|[^a-z])dev([^a-z]|$)/);
});

test("only TLS 5671, only from the prod VPC: no SSH, no public address, no other port", () => {
  assert.equal(prod.port, 5671);
  assert.deepEqual(prod.inbound, [{ protocol: "tcp", ports: "5671", from: "vpc" }]);
  assert.match(cloudInit, /listeners\.tcp = none/);
  assert.match(cloudInit, /-p \$\{PRIVATE_IP\}:5671:5671/);
});

test("no data volume and no backup: a message is never the only copy of a job's state (SOV-105)", () => {
  assert.deepEqual(prod.volumes, []);
  assert.equal(prod.backups, false);
  // The container mounts its configuration and its TLS files, read-only, and nothing else: no queue data survives it.
  const mounts = [...cloudInit.matchAll(/-v [^:\s]+:(\S+)/g)].map((m) => m[1]);
  assert.deepEqual(mounts, ["/etc/rabbitmq/rabbitmq.conf:ro", "/etc/rabbitmq/tls:ro"]);
});

test("one user, the platform's: the pilot has no GPU worker", () => {
  assert.deepEqual(prod.users, { sovkora: ".*" });
});

test("the prod engine reaches this broker by its private name over TLS", () => {
  assert.match(spec, new RegExp(`RABBITMQ_HOST, scope: RUN_TIME, value: ${prod.host.replaceAll(".", "\\.")} \\}`));
  assert.match(spec, new RegExp(`RABBITMQ_PORT, scope: RUN_TIME, value: "${prod.port}"`));
  assert.match(spec, /SPRING_RABBITMQ_SSL_ENABLED, scope: RUN_TIME, value: "true"/);
});
