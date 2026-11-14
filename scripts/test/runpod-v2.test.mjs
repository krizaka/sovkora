// node --test scripts/test — orazaka#508: RunPod API v2 against a STUB (no network, no spend). The stub keeps pods in memory and
// answers the v2 routes (/v2/pods, /v2/pods/{id}, /v2/pods/{id}/action, /v2/network-volumes) plus the GraphQL balance.
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { chmodSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { podToken } from "./pod-token.fixture.mjs";
import { bunnyTarget, capReached, planFromPrompts, spentUsd } from "../runpod-batch.mjs";

const root = fileURLToPath(new URL("../..", import.meta.url));

/** A v2 stub. `pods` is mutable; `seen` lists "METHOD path" in order; `bodies` the JSON bodies. */
async function stub({ pods = [], failCreateFor = [], cost = 0.5 } = {}) {
  const state = { pods, seen: [], bodies: [], auth: new Set(), nextId: 1 };
  const server = createServer((req, res) => {
    let raw = "";
    req.on("data", (c) => (raw += c));
    req.on("end", () => {
      state.auth.add(req.headers.authorization);
      const url = new URL(req.url, "http://x");
      const path = url.pathname;
      state.seen.push(`${req.method} ${path}`);
      const body = raw ? JSON.parse(raw) : undefined;
      if (body) state.bodies.push(body);
      const send = (code, data) => { res.writeHead(code, { "content-type": "application/json" }); res.end(data === undefined ? "" : JSON.stringify(data)); };
      if (path === "/graphql") return send(200, { data: { myself: { clientBalance: 42.5, currentSpendPerHr: 0 } } });
      if (path === "/v2/network-volumes" && req.method === "GET") return send(200, { networkVolumes: [{ id: "vol1", name: "orazaka-gpu-models", dataCenter: "CA-MTL-1" }] });
      if (path === "/v2/network-volumes") return send(201, { id: "vol2" });
      if (path === "/v2/pods" && req.method === "GET") return send(200, { pods: state.pods.filter((p) => p.status !== "TERMINATED").map(({ runtime, ...p }) => p), pagination: { nextCursor: null, hasNextPage: false } });
      if (path === "/v2/pods" && req.method === "POST") {
        if (failCreateFor.includes(body.gpu.id)) return send(400, { message: "no capacity" });
        const pod = { id: `pod${state.nextId++}`, name: body.name, status: "RUNNING", cost, gpu: { id: body.gpu.id }, runtime: { uptime: 60, gpus: [{ util: 90 }] } };
        state.pods.push(pod);
        return send(201, pod);
      }
      const one = path.match(/^\/v2\/pods\/([^/]+)(\/action)?$/);
      const pod = one && state.pods.find((p) => p.id === one[1]);
      if (!pod) return send(404, { message: "not found" });
      if (one[2]) { pod.status = body.action === "terminate" ? "TERMINATED" : "EXITED"; return send(200, pod); }
      return send(200, pod);
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  server.unref(); // a test that fails before its close() must not keep the runner alive (a CI hang of 90 min, orazaka#530)
  const base = `http://127.0.0.1:${server.address().port}`;
  return { state, base, close: () => { server.close(); server.closeAllConnections(); } };
}

const run = (cmd, args, env) => new Promise((resolve) => {
  execFile(cmd, args, { encoding: "utf8", env: { PATH: process.env.PATH, HOME: process.env.HOME, GPU_RETRY_SLEEP: "0", ...env } }, (error, stdout, stderr) =>
    resolve({ code: error ? error.code : 0, stdout, stderr }));
});
const guard = (stubbed, args, extra = {}) => run("python3", [join(root, "scripts/runpod-guard.py"), ...args], {
  AI_GPU_RUNPOD_API_KEY: "k-test", RUNPOD_API_BASE: `${stubbed.base}/v2`, RUNPOD_GRAPHQL_URL: `${stubbed.base}/graphql`, GPU_LOCAL_DIR: mkdtempSync(join(tmpdir(), "rp-")), ...extra });

test("count: « pods: 0 running » when nothing bills, exit 3 when a pod does; an EXITED pod is not billed compute", async () => {
  const s = await stub({ pods: [{ id: "a", name: "orazaka-gpu-big", status: "EXITED", cost: 2 }] });
  const none = await guard(s, ["count"]);
  assert.equal(none.stdout.trim(), "pods: 0 running");
  assert.equal(none.code, 0);
  s.state.pods.push({ id: "b", name: "orazaka-gpu-big", status: "RUNNING", cost: 2 });
  const one = await guard(s, ["count"]);
  assert.equal(one.stdout.trim(), "pods: 1 running");
  assert.equal(one.code, 3);
  assert.deepEqual([...s.state.auth], ["Bearer k-test"]);
  s.close();
});

test("create posts the nested v2 body and terminate uses the action endpoint", async () => {
  const s = await stub();
  const dir = mkdtempSync(join(tmpdir(), "rp-body-"));
  const boot = join(dir, "boot.sh"); writeFileSync(boot, "echo hi\n");
  const body = join(dir, "body.json");
  const env = { WORKER_REF: "w", PROFILE: "big", MAX_HOURS: "1", IDLE_MINUTES: "30", RELAY_URL: "u", RELAY_AUTH: "a", RABBIT_USER: "gpu-pod-20261114120000-abcdef", RABBIT_PASSWORD: "r", POD_KEY: "20261114120000-abcdef", POD_TOKEN: podToken("20261114120000-abcdef"), BROKER_CA_B64: "c", RP_IMAGE: "img", DC: "CA-MTL-1", VOLUME: "vol1", PUBKEY: "ssh-ed25519 AAA", BOOT: boot, DISK_GB: "" };
  assert.equal((await guard(s, ["body", body, "NVIDIA L40S"], env)).code, 0);
  const made = await guard(s, ["create", body]);
  assert.match(made.stdout.trim(), /^pod1 0\.5 NVIDIA L40S$/);
  const sent = s.state.bodies[0];
  assert.equal(sent.image, "img");
  assert.deepEqual(sent.gpu, { id: "NVIDIA L40S", count: 1 });
  assert.deepEqual(sent.mounts, { network: [{ volumeId: "vol1", path: "/models" }] });
  assert.equal(sent.disk, 60);
  assert.deepEqual(sent.entrypoint, ["bash", "-c"]);
  assert.equal(sent.gpuTypeIds, undefined); // no v1 field survives
  assert.equal(sent.imageName, undefined);
  await guard(s, ["terminate", "pod1"]);
  assert.deepEqual(s.state.seen.slice(-1), ["POST /v2/pods/pod1/action"]);
  assert.deepEqual(s.state.bodies.at(-1), { action: "terminate" });
  assert.equal((await guard(s, ["count"])).stdout.trim(), "pods: 0 running");
  s.close();
});

test("volumes are read from /v2/network-volumes (wrapper key, dataCenter)", async () => {
  const s = await stub();
  assert.equal((await guard(s, ["volume-id", "orazaka-gpu-models", "CA-MTL-1"])).stdout.trim(), "vol1");
  assert.equal((await guard(s, ["volume-id", "orazaka-gpu-models", "US-GA-2"])).stdout.trim(), "");
  assert.equal((await guard(s, ["volume-create", "n", "US-GA-2", "50"])).stdout.trim(), "vol2");
  assert.deepEqual(s.state.bodies.at(-1), { name: "n", dataCenter: "US-GA-2", size: 50 });
  s.close();
});

test("the guard ends (terminates) a pod past MAX_HOURS, reading uptime from the pod's own runtime", async () => {
  const s = await stub({ pods: [{ id: "old", name: "orazaka-gpu-big", status: "RUNNING", cost: 3, runtime: { uptime: 7 * 3600, gpus: [{ util: 99 }] } }, { id: "young", name: "orazaka-gpu-big", status: "RUNNING", cost: 3, runtime: { uptime: 600, gpus: [{ util: 99 }] } }] });
  const out = await guard(s, ["once"], { GPU_MAX_HOURS: "6" });
  assert.equal(out.code, 0);
  assert.match(out.stdout, /terminated old — lived 7\.0 h/);
  assert.deepEqual(s.state.pods.map((p) => p.status), ["TERMINATED", "RUNNING"]);
  s.close();
});

test("a failing call prints the API's message and exits 1 (no traceback, no key)", async () => {
  const s = await stub();
  const out = await guard(s, ["terminate", "nope"]);
  assert.equal(out.code, 1);
  assert.match(out.stderr, /terminate answered 404/);
  assert.doesNotMatch(out.stderr + out.stdout, /k-test/);
  s.close();
});

/** Runs `runpod batch` from gpu-runpod.sh against the stub, with `node` replaced by a script that renders (or fails). */
async function batch(s, renderScript) {
  const dir = mkdtempSync(join(tmpdir(), "rp-batch-"));
  const bin = join(dir, "bin"); mkdirSync(bin);
  writeFileSync(join(bin, "node"), `#!/bin/sh\ncase " $*" in *" --validate-only"*) exit 0;; esac\n${renderScript}\n`); chmodSync(join(bin, "node"), 0o755);
  writeFileSync(join(bin, "ssh-keygen"), `#!/bin/sh\nwhile [ $# -gt 0 ]; do [ "$1" = -f ] && f="$2"; shift; done\necho k > "$f"; echo "ssh-ed25519 AAA" > "$f.pub"\n`); chmodSync(join(bin, "ssh-keygen"), 0o755);
  writeFileSync(join(dir, "prompts.txt"), "# a comment\na red kite\n\na green kite\n");
  writeFileSync(join(dir, "driver.sh"), `set -euo pipefail
ROOT="${root}"; LOCAL="${dir}/local"; LEDGER="${dir}/ledger.tsv"; ENV_FILE=x; RELAY_URL=u
die() { echo "gpu-dev: $*" >&2; exit 1; }; say() { echo "gpu-dev: $*"; }
gpu_out_env() { printf "GPU_OUT_URL=https://z.example/krizaka-gpu-out/d/%s\\nGPU_OUT_HEADER=AccessKey\\nGPU_OUT_KEY=zone-key\\n" "$1"; }
secret() { echo "s3cret-$1"; }; mint_service_token() { echo tok; }; broker_ca_b64() { echo ca; }; ledger_add() { :; }
source "$ROOT/scripts/lib/gpu-runpod.sh"
runpod batch "${dir}/prompts.txt" --cap 1 --out "${dir}/out" --gpu "NVIDIA L40S"
`);
  const result = await run("bash", [join(dir, "driver.sh")], {
    PATH: `${bin}:${process.env.PATH}`, GPU_BATCH_SKIP_PREFLIGHT: "1", GPU_WORKER_REF: "w".repeat(40), GPU_LOCAL_DIR: join(dir, "local"),
    RUNPOD_API_BASE: `${s.base}/v2`, RUNPOD_GRAPHQL_URL: `${s.base}/graphql`,
  });
  return result;
}

test("batch: create → render → terminate, then « pods: 0 running »; the cap reaches the render script", async () => {
  const s = await stub();
  const r = await batch(s, 'echo "node $*" >> "$0.log"; exit 0');
  assert.equal(r.code, 0, r.stderr);
  assert.match(r.stdout, /pods: 0 running/);
  assert.deepEqual(s.state.seen.filter((x) => /pods/.test(x) && !/GET/.test(x)), ["POST /v2/pods", "POST /v2/pods/pod1/action"]);
  assert.equal(s.state.pods[0].status, "TERMINATED");
  assert.match(r.stdout, /2 prompts, cap \$1/); // comments and blanks are not prompts
  s.close();
});

test("batch: the pod is terminated even when the render FAILS (trap), and the failure status is kept", async () => {
  const s = await stub();
  const r = await batch(s, "exit 7");
  assert.equal(r.code, 7);
  assert.equal(s.state.pods[0].status, "TERMINATED");
  assert.match(r.stdout, /pods: 0 running/);
  s.close();
});

test("batch: a pod dearer per hour than the whole cap is refused and terminated at once, nothing rendered", async () => {
  const s = await stub({ cost: 2.5 });
  const r = await batch(s, 'echo rendered >> "$0.log"; exit 0');
  assert.equal(r.code, 1);
  assert.match(r.stderr, /more than the whole cap/);
  assert.equal(s.state.pods[0].status, "TERMINATED");
  assert.match(r.stdout, /pods: 0 running/);
  s.close();
});

test("batch cap maths: spend is hours × rate, a prompt is not started when it would cross the cap", () => {
  assert.equal(spentUsd(3, 0, 3600_000), 3);
  assert.equal(spentUsd(2.4, 1000, (1000 + 900) * 1000), 0.6);
  assert.equal(capReached(0.5, 1), false);
  assert.equal(capReached(1, 1), true);
  assert.equal(capReached(0.9, 1, 0.2), true);
});

test("batch plan: one clip per prompt, comments and blank lines dropped, ids sortable; Bunny paths are slugs", () => {
  const plan = planFromPrompts("# x\n\nfirst\nsecond\n");
  assert.deepEqual(plan.clips, [{ id: "r01", scene: "first" }, { id: "r02", scene: "second" }]);
  const t = bunnyTarget("2026-10-30-kites", "r01", { MEDIA_STORAGE_ZONE: "z", MEDIA_CDN_HOSTNAME: "cdn.example" });
  assert.equal(t.path, "site/renders/2026-10-30-kites/r01.png");
  assert.equal(t.put, "https://storage.bunnycdn.com/z/site/renders/2026-10-30-kites/r01.png");
  assert.equal(t.cdn, "https://cdn.example/site/renders/2026-10-30-kites/r01.png");
  assert.throws(() => bunnyTarget("../x", "r01", {}), /slug/);
});

test("without a volume the body mounts nothing and sizes the container disk (orazaka#508)", async () => {
  const s = await stub();
  const dir = mkdtempSync(join(tmpdir(), "rp-novol-"));
  const boot = join(dir, "boot.sh"); writeFileSync(boot, "echo hi\n");
  const body = join(dir, "body.json");
  const env = { WORKER_REF: "w", PROFILE: "big", MAX_HOURS: "1", IDLE_MINUTES: "30", RELAY_URL: "u", RELAY_AUTH: "a", RABBIT_USER: "gpu-pod-20261114120000-abcdef", RABBIT_PASSWORD: "r", POD_KEY: "20261114120000-abcdef", POD_TOKEN: podToken("20261114120000-abcdef"), BROKER_CA_B64: "c", RP_IMAGE: "img", DC: "CA-MTL-1", VOLUME: "", PUBKEY: "ssh-ed25519 AAA", BOOT: boot, DISK_GB: "100" };
  assert.equal((await guard(s, ["body", body, "NVIDIA GeForce RTX 4090"], env)).code, 0);
  const made = JSON.parse(readFileSync(body, "utf8"));
  assert.equal(made.mounts, undefined);
  assert.equal(made.disk, 100);
  s.close();
});

test("planFromPrompts: a [name] prefix resolves reference pictures; an unknown name stops before any pod time", () => {
  const plan = planFromPrompts("# c\n[owl, cahier] An owl reads.\nPlain shot", { owl: "asset-1", cahier: "asset-2" });
  assert.deepEqual(plan.clips[0], { id: "r01", scene: "An owl reads.", references: ["asset-1", "asset-2"] });
  assert.deepEqual(plan.clips[1], { id: "r02", scene: "Plain shot" });
  assert.throws(() => planFromPrompts("[duck] x", {}), /reference "duck" is not in --refs/);
});
