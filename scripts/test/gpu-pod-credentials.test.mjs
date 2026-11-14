// node --test scripts/test — krizaka-internal#3614: a Studio-serving RunPod pod joins the broker as ITS OWN user, made by the
// launcher before the pod and deleted when it ends; the create body carries none of the vault's shared secrets. RunPod is a
// stub (no network, no spend); the broker is a stub shell that records the rabbitmqctl scripts it is sent. The real broker is
// exercised by workers/media/test_gpu_pod_broker.py (RabbitMQ 4.2 in a container).
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../..", import.meta.url));
const KEY = /^[0-9]{14}-[0-9a-f]{6}$/;
const POD = "orazaka-gpu"; // the launcher's pod-name prefix (RP_PREFIX in scripts/lib/gpu-runpod.sh)
// Every value the vault holds that more than one pod (or the platform) shares. The stub secret() answers "s3cret-<KEY>".
const SHARED = ["RABBITMQ_PASS_GPU", "RABBITMQ_PASS", "IDENTITY_JWT_SECRET", "AI_GPU_RUNPOD_API_KEY", "GPU_WATCHDOG_DO_TOKEN"];

/** A RunPod v2 stub: pods in memory, the bodies it was sent. */
async function runpod({ failCreate = false } = {}) {
  const state = { pods: [], bodies: [], nextId: 1 };
  const server = createServer((req, res) => {
    let raw = "";
    req.on("data", (c) => (raw += c));
    req.on("end", () => {
      const path = new URL(req.url, "http://x").pathname;
      const body = raw ? JSON.parse(raw) : undefined;
      const send = (code, data) => { res.writeHead(code, { "content-type": "application/json" }); res.end(JSON.stringify(data ?? {})); };
      if (path === "/graphql") return send(200, { data: { myself: { clientBalance: 42.5, currentSpendPerHr: 0 } } });
      if (path === "/v2/network-volumes") return send(200, { networkVolumes: [{ id: "vol1", name: "models", dataCenter: "CA-MTL-1" }] });
      if (path === "/v2/pods" && req.method === "GET") return send(200, { pods: state.pods.filter((p) => p.status !== "TERMINATED"), pagination: { hasNextPage: false } });
      if (path === "/v2/pods" && req.method === "POST") {
        state.bodies.push(body);
        if (failCreate) return send(400, { message: "no capacity" });
        const pod = { id: `pod${state.nextId++}`, name: body.name, status: "RUNNING", cost: 0.5, gpu: { id: body.gpu.id } };
        state.pods.push(pod);
        return send(201, pod);
      }
      const one = path.match(/^\/v2\/pods\/([^/]+)\/action$/);
      const pod = one && state.pods.find((p) => p.id === one[1]);
      if (!pod) return send(404, { message: "not found" });
      pod.status = "TERMINATED";
      return send(200, pod);
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  server.unref();
  return { state, base: `http://127.0.0.1:${server.address().port}`, close: () => { server.close(); server.closeAllConnections(); } };
}

const run = (cmd, args, env) => new Promise((resolve) => {
  execFile(cmd, args, { encoding: "utf8", env: { PATH: process.env.PATH, HOME: process.env.HOME, GPU_RETRY_SLEEP: "0", ...env } }, (error, stdout, stderr) =>
    resolve({ code: error ? error.code : 0, stdout, stderr }));
});

/** Sources gpu-runpod.sh with the vault, the output zone and the broker stubbed, then runs `runpod <args…>` lines. */
async function launcher(s, commands, { users = [], env = {} } = {}) {
  const dir = mkdtempSync(join(tmpdir(), "rp-cred-"));
  const bin = join(dir, "bin"); mkdirSync(bin);
  writeFileSync(join(bin, "ssh-keygen"), `#!/bin/sh\nwhile [ $# -gt 0 ]; do [ "$1" = -f ] && f="$2"; shift; done\necho k > "$f"; echo "ssh-ed25519 AAA" > "$f.pub"\n`);
  chmodSync(join(bin, "ssh-keygen"), 0o755);
  // The broker: each call's script appended to broker.log; list_users answers the users file (one per line).
  const log = join(dir, "broker.log");
  writeFileSync(join(dir, "users"), users.map((u) => `${u}\t[]\n`).join(""));
  writeFileSync(join(bin, "fake-broker"), `#!/bin/sh\nscript=$(cat)\nprintf '%s\\n----\\n' "$script" >> "${log}"\ncase "$script" in *"delete_user"*) ;; *"list_users"*) cat "${dir}/users" ;; esac\n`);
  chmodSync(join(bin, "fake-broker"), 0o755);
  writeFileSync(join(dir, "driver.sh"), `set -euo pipefail
ROOT="${root}"; LOCAL="${dir}/local"; LEDGER="${dir}/ledger.tsv"; ENV_FILE=x; RELAY_URL=u
die() { echo "gpu-dev: $*" >&2; exit 1; }; say() { echo "gpu-dev: $*"; }
gpu_out_env() { printf "GPU_OUT_URL=https://z.example/krizaka-gpu-out/d/%s\\nGPU_OUT_HEADER=AccessKey\\nGPU_OUT_KEY=zone-key\\n" "$1"; }
secret() { echo "s3cret-$1"; }; mint_service_token() { echo tok; }; broker_ca_b64() { echo ca; }; ledger_add() { :; }
source "$ROOT/scripts/lib/gpu-runpod.sh"
${commands.map((c) => `runpod ${c}`).join("\n")}
`);
  const result = await run("bash", [join(dir, "driver.sh")], {
    PATH: `${bin}:${process.env.PATH}`, GPU_WORKER_REF: "w".repeat(40), GPU_LOCAL_DIR: join(dir, "local"), GPU_RUNPOD_DCS: "CA-MTL-1",
    GPU_RUNPOD_GPUS: "NVIDIA L40S", GPU_RUNPOD_VOLUME: "models", RUNPOD_API_BASE: `${s.base}/v2`, RUNPOD_GRAPHQL_URL: `${s.base}/graphql`, GPU_BROKER_SHELL: join(bin, "fake-broker"), ...env,
  });
  const calls = existsSync(log) ? readFileSync(log, "utf8").split("\n----\n").filter((c) => c.trim()) : [];
  return { ...result, calls };
}

test("start: the pod's own broker user is made BEFORE the pod, and the create body carries it and no shared secret", async () => {
  const s = await runpod();
  const r = await launcher(s, ["start"]);
  assert.equal(r.code, 0, r.stderr);
  const body = s.state.bodies[0];
  const env = body.env;
  assert.match(env.RABBIT_USER, /^gpu-pod-/);
  const key = env.RABBIT_USER.slice("gpu-pod-".length);
  assert.match(key, KEY);
  assert.ok(body.name.endsWith(`-${key}`), "the pod's name ends with its key: the end of the pod finds its user");
  const created = r.calls.find((c) => c.includes("add_user"));
  assert.ok(created.includes(`add_user gpu-pod-${key} `), "rabbitmqctl add_user for this pod");
  assert.ok(created.includes(env.RABBIT_PASSWORD), "the password the pod gets is the one the broker was given");
  assert.ok(env.RABBIT_PASSWORD.length >= 40, "a fresh, long password");
  assert.ok(created.includes(" orazaka.events "), "the topic permissions name the events exchange");
  assert.match(created, /set_topic_permissions /);
  // (b) none of the vault's shared values travels in the body.
  const sent = JSON.stringify(body);
  for (const name of SHARED) assert.ok(!sent.includes(`s3cret-${name}`), `${name} must not reach the pod`);
  assert.notEqual(env.RABBIT_USER, "gpu-worker");
  // The shared values left, each named: the relay credential (it reaches only the relay's allow-list, and nothing behind it
  // without the pod's own broker user and token) and the optional read-only download token of public weights.
  assert.deepEqual([...sent.matchAll(/s3cret-[A-Z_]+/g)].map((m) => m[0]), ["s3cret-AI_GPU_RELAY_PASSWORD", "s3cret-AI_HUGGINGFACE_TOKEN"]);
  s.close();
});

test("stop: the terminated pod's broker user is deleted with it", async () => {
  const s = await runpod();
  const r = await launcher(s, ["start", "stop"]);
  assert.equal(r.code, 0, r.stderr);
  const user = s.state.bodies[0].env.RABBIT_USER;
  assert.equal(s.state.pods[0].status, "TERMINATED");
  const deleted = r.calls.filter((c) => c.includes("delete_user"));
  assert.equal(deleted.length, 1);
  assert.ok(deleted[0].includes(`delete_user ${user}\n`) && deleted[0].includes(`close_all_user_connections ${user} `));
  assert.match(r.stdout, /deleted the broker user of pod pod1/);
  s.close();
});

test("start: when no datacentre has the GPU, the user made for it is deleted and nothing runs", async () => {
  const s = await runpod({ failCreate: true });
  const r = await launcher(s, ["start"]);
  assert.equal(r.code, 1);
  assert.match(r.stderr, /nothing was created/);
  const user = s.state.bodies[0].env.RABBIT_USER;
  assert.ok(r.calls.some((c) => c.includes(`delete_user ${user}\n`)));
  s.close();
});

test("start sweeps first: the user of a pod that no longer runs (its watchdog ended it) is deleted, a live pod's is kept", async () => {
  const s = await runpod();
  s.state.pods.push({ id: "live1", name: `${POD}-big-20261114100000-aaaaaa`, status: "RUNNING", cost: 0.5, gpu: { id: "NVIDIA L40S" } });
  const r = await launcher(s, ["start"], { users: ["gpu-pod-20261114100000-aaaaaa", "gpu-pod-20261114090000-bbbbbb", "platform"] });
  assert.equal(r.code, 1); // a pod already runs: the start refuses, and the sweep ran before
  const deleted = r.calls.filter((c) => c.includes("delete_user")).join("\n");
  assert.match(deleted, /delete_user gpu-pod-20261114090000-bbbbbb\n/);
  assert.doesNotMatch(deleted, /20261114100000-aaaaaa/);
  assert.doesNotMatch(deleted, /delete_user platform\n/);
  s.close();
});

test("measure and stills pods get no broker user at all, and no broker is called", async () => {
  for (const mode of ["MEASURE", "STILLS"]) {
    const s = await runpod();
    const r = await launcher(s, ["start"], { env: { [mode]: "1" } });
    assert.equal(r.code, 0, r.stderr);
    const env = s.state.bodies[0].env;
    for (const k of ["RABBIT_USER", "RABBIT_PASSWORD", "SERVICE_TOKEN"]) assert.equal(env[k], undefined, `${mode}: ${k}`);
    assert.deepEqual(r.calls, [], `${mode}: the broker is never asked`);
    s.close();
  }
});

test("the body builder refuses a shared broker user", async () => {
  const dir = mkdtempSync(join(tmpdir(), "rp-body-"));
  const boot = join(dir, "boot.sh"); writeFileSync(boot, "echo hi\n");
  const env = { WORKER_REF: "w", PROFILE: "big", MAX_HOURS: "1", IDLE_MINUTES: "30", RELAY_URL: "u", RELAY_AUTH: "a", RABBIT_USER: "gpu-worker", RABBIT_PASSWORD: "r", SERVICE_TOKEN: "t", BROKER_CA_B64: "c", RP_IMAGE: "img", DC: "CA-MTL-1", VOLUME: "", PUBKEY: "ssh-ed25519 AAA", BOOT: boot, DISK_GB: "" };
  const r = await run("python3", [join(root, "scripts/runpod-guard.py"), "body", join(dir, "body.json"), "NVIDIA L40S"], { AI_GPU_RUNPOD_API_KEY: "k", ...env });
  assert.notEqual(r.code, 0);
  assert.match(r.stderr, /its own gpu-pod-\* user/);
});
