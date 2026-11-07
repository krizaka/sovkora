// node --test scripts/test — the core container must fit its memory (2026-10-29: 8 JVMs + 2 Python workers on `basic-s`, 2 GiB,
// ran at 93-99 % memory and restarted several times an hour; each restart is ~4 minutes without the edge).
// The model is MEASURED, not computed: infra/deploy/core-memory-measured.json holds the steady resident set of each JVM under a
// 2 GiB cgroup; the flags that made it are guarded below, and a heap ceiling raised without a new measurement fails.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const read = (p) => readFileSync(new URL(`../../${p}`, import.meta.url), "utf8");
const MIB = { "basic-xs": 1024, "basic-s": 2048, "basic-m": 4096, "basic-l": 8192 };
const measured = JSON.parse(read("infra/deploy/core-memory-measured.json"));
const sh = read("infra/deploy/run-services.sh");
const heaps = Object.fromEntries(
  /SERVICES="\$\{ORAZAKA_SERVICES:-([^}]+)\}"/.exec(sh)[1].trim().split(/\s+/).map((e) => {
    const [name, , heap] = e.split(":");
    return [name, Number(heap)];
  }),
);
const coreSize = () => MIB[/- name: core\n[\s\S]*?instance_size_slug: (\S+)/.exec(read("infra/deploy/sovkora-dev.app.yaml"))[1]];

test("the measured steady set, with the workers, uses at most 85 % of the container", () => {
  const workers = Object.values(measured.workersMiB).filter(Number.isFinite).reduce((a, b) => a + b, 0);
  const need = measured.after.cgroupMiB + workers;
  assert.ok(need <= coreSize() * 0.85, `core holds ${need} MiB at steady state (JVMs ${measured.after.cgroupMiB} + workers ${workers}) of ${coreSize()} MiB`);
});

test("every JVM runs on a heap ceiling that was measured: a raised ceiling needs a new measurement", () => {
  assert.deepEqual(Object.keys(heaps).sort(), Object.keys(measured.heapCeilingMiB).sort());
  for (const [name, heap] of Object.entries(heaps)) assert.ok(heap <= measured.heapCeilingMiB[name], `${name}: heap ${heap} MiB > measured ${measured.heapCeilingMiB[name]} MiB`);
});

test("the JVMs run with the footprint flags the measurement was made with", () => {
  const opts = /COMMON_OPTS="\$\{ORAZAKA_JAVA_OPTS:-([^}]+)\}"/.exec(sh)[1];
  for (const flag of ["-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-XX:CICompilerCount=1", "-XX:ActiveProcessorCount=2", "-XX:MaxHeapFreeRatio=30", "-XX:-UsePerfData"]) {
    assert.ok(opts.includes(flag), `${flag} is missing from the JVM defaults`);
  }
  assert.match(sh, /export MALLOC_ARENA_MAX="\$\{MALLOC_ARENA_MAX:-2\}"/);
  assert.match(sh, /-Xms16m/);
});
