// node --test scripts/test — the measurement-only RunPod pod: it measures and ends, and never joins the dev broker.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

const root = new URL("../../", import.meta.url).pathname;
const read = (p) => readFileSync(join(root, p), "utf8");
const measure = read("infra/gpu/runpod-measure.sh");
const bootstrap = read("infra/gpu/runpod-bootstrap.sh");
const lib = read("scripts/lib/gpu-runpod.sh");
const guard = read("scripts/runpod-guard.py"); // the v2 client builds the pod body (orazaka#508)
const watchdog = (t) => t.slice(t.indexOf("cat > /usr/local/bin/orazaka-watchdog"), t.indexOf("chmod +x /usr/local/bin/orazaka-watchdog"));

test("the measurement pod never learns the relay, the broker, a service token or starts the consumer", () => {
  for (const forbidden of ["chisel", "RELAY_URL", "RELAY_AUTH", "RABBIT_PASSWORD", "SERVICE_TOKEN", "BROKER_CA_B64", "app.consumer", "SPRING_RABBITMQ"]) {
    assert.ok(!measure.includes(forbidden), `runpod-measure.sh must not mention ${forbidden}`);
  }
});

test("it arms the same watchdog as the worker pod, first, and ends itself after the grace", () => {
  assert.equal(watchdog(measure), watchdog(bootstrap));
  assert.ok(measure.indexOf("orazaka-watchdog &") < measure.indexOf("pip install"), "the watchdog is armed before any build");
  assert.match(measure, /orazaka-watchdog --destroy/);
  assert.match(measure, /MEASURE_GRACE_MINUTES:-15/);
});

test("the measure subcommand sends the pod only what it needs", () => {
  assert.match(lib, /measure\) shift; MEASURE=1 runpod_start/);
  const block = guard.slice(guard.indexOf('if e.get("MEASURE")'), guard.indexOf('if e.get("HF_TOKEN")'));
  assert.match(block, /WORKER_REF.*PROFILE.*MAX_HOURS.*IDLE_MINUTES.*MEASURE_ARGS.*MEASURE_PIP/s);
  assert.ok(!/RELAY|RABBIT|SERVICE_TOKEN|BROKER/.test(block.split("elif")[0]), "measure env carries no broker credential");
  assert.match(guard, /"measure" if e.get\("MEASURE"\)/);
});

test("one pod at a time: the measure pod is named orazaka-gpu-* so the existing guard refuses a second one", () => {
  assert.match(guard, /"orazaka-gpu-" \+/);
  assert.match(lib, /RP_PREFIX="orazaka-gpu"[\s\S]*rp_live\(\) \{ rp_py list "\$RP_PREFIX"/);
});

test("the measures write where the next pod and the operator can read them", () => {
  assert.match(measure, /\/models\/measure\//);
  assert.match(measure, /results\.jsonl/);
});

test("an EXITED pod does not hold the slot: only a pod that runs refuses a second one (its stopped disk costs cents, not a GPU)", () => {
  assert.match(lib, /rp_live\(\) \{[^\n]*\$2 != "EXITED" && \$2 != "TERMINATED"/);
  assert.match(lib, /running=\$\(rp_live \| wc -l/);
});

test("stop ends the pods that run, never an EXITED one: a stopped pod keeps its disk (weights, work) for whoever owns it", () => {
  const stop = lib.slice(lib.indexOf("runpod_stop() {"), lib.indexOf("runpod_ledger_close() {"));
  assert.match(stop, /done < <\(rp_live\)/); // rp_live drops EXITED and TERMINATED pods
});
