// node --test scripts/test — the GPU host: what a droplet receives, and what it never does.
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createHmac } from "node:crypto";
import { mkdirSync, mkdtempSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

const root = new URL("../../", import.meta.url).pathname;
const read = (path) => readFileSync(join(root, path), "utf8");
const JWT = "j".repeat(48);

function render(profile) {
  const dir = mkdtempSync(join(tmpdir(), "gpu-dev-"));
  const env = join(dir, "orazaka-dev.env");
  writeFileSync(
    env,
    [
      `IDENTITY_JWT_SECRET=${JWT}`,
      "RABBITMQ_PASS_GPU=rabbit-secret-value",
      `RABBITMQ_CA_CERT_BASE64=${Buffer.from("-----BEGIN CERTIFICATE-----\nTEST\n-----END CERTIFICATE-----\n").toString("base64")}`,
      "AI_GPU_RELAY_PASSWORD=relay-secret-value-0123456789abcdef",
      "GPU_WATCHDOG_DO_TOKEN=dop_v1_watchdog",
    ].join("\n"),
  );
  const out = join(dir, "user-data.yaml");
  execFileSync("bash", [join(root, "scripts/gpu-dev.sh"), "user-data", profile, out], {
    env: { ...process.env, ORAZAKA_DEV_ENV_FILE: env, GPU_REGIONS: "tor1" },
  });
  const text = readFileSync(out, "utf8");
  const files = {};
  for (const m of text.matchAll(/- path: (\S+)\n\s+encoding: b64\n\s+permissions: '(\d+)'\n\s+owner: \S+\n\s+content: (\S+)/g)) {
    files[m[1]] = { mode: m[2], content: Buffer.from(m[3], "base64").toString() };
  }
  return { text, files, mode: statSync(out).mode & 0o777 };
}

test("user-data: the host scripts are the repository's, secrets are 0600 and the identity secret never leaves", () => {
  const { text, files, mode } = render("big");
  assert.equal(mode, 0o600);
  assert.match(text, /^#cloud-config\n/);
  assert.equal(files["/opt/orazaka-gpu/host.sh"].content, read("infra/gpu/host.sh"));
  assert.equal(files["/opt/orazaka-gpu/watchdog.sh"].content, read("infra/gpu/watchdog.sh"));
  for (const secret of ["/etc/orazaka-gpu/worker.env", "/etc/orazaka-gpu/relay.env", "/etc/orazaka-gpu/do-token"]) {
    assert.equal(files[secret].mode, "0600", secret);
  }
  assert.ok(!text.includes(JWT) && !Object.values(files).some((f) => f.content.includes(JWT)));
  assert.match(files["/etc/orazaka-gpu/host.env"].content, /^PROFILE=big$/m);
  assert.match(files["/etc/orazaka-gpu/host.env"].content, /^VOLUME_NAME=orazaka-gpu-models-tor1$/m);
  assert.match(files["/etc/orazaka-gpu/host.env"].content, /^IDLE_MINUTES=30$/m);
  assert.match(files["/etc/orazaka-gpu/relay.env"].content, /^AUTH=gpu:relay-secret-value/);
  // The narrow broker user, never the platform's; the dev CA to verify the broker with.
  assert.match(files["/etc/orazaka-gpu/worker.env"].content, /^SPRING_RABBITMQ_USERNAME=gpu-worker$/m);
  assert.match(files["/etc/orazaka-gpu/broker-ca.pem"].content, /BEGIN CERTIFICATE/);
  assert.match(text, /ufw allow from 127\.0\.0\.1 to any port 22/);
});

test("user-data: the worker's SERVICE token is signed, scoped to SERVICE, and expires with the droplet", () => {
  const { files } = render("small");
  const token = files["/etc/orazaka-gpu/worker.env"].content.match(/^ORAZAKA_SERVICE_TOKEN=(\S+)$/m)[1];
  const [h, p, s] = token.split(".");
  assert.equal(createHmac("sha256", JWT).update(`${h}.${p}`).digest("base64url"), s);
  const claims = JSON.parse(Buffer.from(p, "base64url").toString());
  assert.deepEqual(claims.roles, ["SERVICE"]);
  const hours = (claims.exp - claims.iat) / 3600;
  assert.ok(hours > 8 && hours <= 9, `token lives ${hours} h`);
  assert.match(files["/etc/orazaka-gpu/host.env"].content, /^IMAGE_OFFLOAD=true$/m); // small shares 20 GB
});

test("the droplet opens no port but SSH from the caller, and reaches the platform only through the relay", () => {
  const host = read("infra/gpu/host.sh");
  assert.doesNotMatch(host, /\s-p\s+\d+:\d+/, "no published port");
  assert.match(host, /iptables -I DOCKER-USER -d 169\.254\.169\.254 -j DROP/);
  assert.match(host, /client .*"\$RELAY_URL"[\s\S]*0\.0\.0\.0:5671:broker-dev\.orazaka\.com:5671 0\.0\.0\.0:8090:core:8090[\s\S]*R:0\.0\.0\.0:8086:sd-server:8086 R:0\.0\.0\.0:8188:worker:8188 R:0\.0\.0\.0:11434:ollama:11434/);
  // A private registry never blocks a start: the image is built once per worker commit and kept on the volume.
  assert.match(host, /docker pull -q "\$WORKER_IMAGE"[\s\S]*docker load -q -i[\s\S]*docker build -q -f "\$src\/workers\/media\/Dockerfile\.cuda"[\s\S]*docker save/);
  // The watchdog is armed before anything that can fail.
  assert.ok(host.indexOf("orazaka-gpu-watchdog.timer") < host.indexOf("--name orazaka-fetch"));
});

test("the watchdog destroys (never powers off) on idle, lifetime or a stuck setup", () => {
  const wd = read("infra/gpu/watchdog.sh");
  assert.match(wd, /-X DELETE[\s\S]*\/v2\/droplets\/\$\{id\}/);
  assert.doesNotMatch(wd, /power_?off|shutdown|halt/i);
  assert.match(wd, /IDLE_MINUTES="\$\{IDLE_MINUTES:-30\}"/);
  for (const reason of ["idle for", "lifetime of", "setup unfinished"]) assert.ok(wd.includes(reason), reason);
});

test("gpu-dev.sh: stop always destroys, and the sizes are the stated ones", () => {
  const script = read("scripts/gpu-dev.sh");
  assert.match(script, /doctl compute droplet delete \$ids --force/);
  // The GPU image takes no root password: the droplet is created with this machine's SSH key.
  assert.match(script, /--ssh-keys "\$fingerprint"/);
  assert.doesNotMatch(script, /power-?off|droplet-action shutdown/);
  assert.match(script, /big\) echo "gpu-l40sx1-48gb:1\.57 gpu-6000adax1-48gb:1\.57"/);
  assert.match(script, /small\) echo "gpu-4000adax1-20gb:0\.76"/);
});

// RunPod spend guard (SOV-096): the rule alone, on fixtures — no network, no key.
function decide(fixture) {
  const input = JSON.stringify(fixture);
  return JSON.parse(execFileSync("python3", [join(root, "scripts/runpod-guard.py"), "decide"], { input, encoding: "utf8" }));
}

test("runpod guard: a busy pod is kept, an idle one is stopped after 20 minutes", () => {
  const busy = { id: "a", name: "busy", uptime: 3600, util: 80 };
  const idle = { id: "b", name: "idle", uptime: 3600, util: 0 };
  assert.deepEqual(decide({ now: 10_000, pods: [busy, idle], idle_since: { b: 10_000 - 19 * 60 } }), []);
  const stopped = decide({ now: 10_000, pods: [busy, idle], idle_since: { a: 0, b: 10_000 - 21 * 60 } });
  assert.deepEqual(stopped.map(([id]) => id), ["b"]);
});

test("runpod guard: a pod older than 6 hours is stopped even when busy", () => {
  const stopped = decide({ now: 1, pods: [{ id: "c", name: "old", uptime: 6 * 3600 + 1, util: 90 }] });
  assert.deepEqual(stopped.map(([id]) => id), ["c"]);
});

test("runpod guard: gpu-dev.sh routes `runpod` to the guard and the key never travels as an argument", () => {
  const script = read("scripts/lib/gpu-runpod.sh");
  assert.match(script, /once\|loop\)[\s\S]*runpod-guard\.py/);
  assert.doesNotMatch(read("scripts/runpod-guard.py"), /sys\.argv\[\d\].*key/i);
});

test("the volume's creation message never reaches stdout (it is read as the volume id), and the host builds with buildx", () => {
  const script = read("scripts/gpu-dev.sh");
  assert.match(script, /kept between droplets\)" >&2/);
  const host = read("infra/gpu/host.sh");
  assert.match(host, /docker buildx version[\s\S]*apt-get install -y -qq docker-buildx[\s\S]*docker build/);
  // one image per worker commit: a new main commit is a new tar, hence a rebuild
  assert.match(host, /sovkora-media-worker-\$\{sha\}\.tar/);
});

test("GPU_SERVICES=video starts neither the image engine nor Ollama (their memory is the video model's)", () => {
  const script = read("scripts/gpu-dev.sh");
  assert.match(script, /SERVICES=\{os\.environ\.get\('GPU_SERVICES', 'all'\)\}/);
  const host = read("infra/gpu/host.sh");
  assert.match(host, /if \[\[ "\$\{SERVICES:-all\}" != "video" \]\]; then\s+docker run -d --name sd-server/);
  assert.match(host, /if \[\[ "\$\{SERVICES:-all\}" != "video" \]\]; then\s+mkdir -p \/models\/ollama/);
});

test("runpod: the key comes from the secrets file and is never echoed, a pod is destroyed (never left), and the volume keeps the weights", () => {
  const script = read("scripts/lib/gpu-runpod.sh");
  assert.match(script, /secret AI_GPU_RUNPOD_API_KEY/);
  assert.doesNotMatch(script, /echo[^\n]*rp_key|printf[^\n]*rp_key|say[^\n]*\$\(rp_key\)/);
  assert.match(script, /rp_py terminate "\$id"/); // v2: POST /pods/{id}/action terminate (orazaka#508)
  const guard = read("scripts/runpod-guard.py");
  assert.match(guard, /"mounts": \{"network": \[\{"volumeId": e\["VOLUME"\], "path": "\/models"\}\]\}/);
  assert.doesNotMatch(script + guard, /rest\.runpod\.io|\/v1\b/); // the v1 API is gone with its sunset
  assert.match(script, /GPU_RUNPOD_MIN_BALANCE/); // a budget guard before any pod
  assert.match(script, /ledger_add .*rp-/); // the same ledger as the droplets
  // gpu-dev.sh stop destroys RunPod pods too, even when no droplet runs
  const main = read("scripts/gpu-dev.sh");
  assert.equal((main.match(/runpod_stop/g) ?? []).length >= 2, true);
  assert.match(main, /runpod\) shift; runpod "\$@"/);
});

test("runpod bootstrap: a watchdog first, the worker built from its public repository, weights on the volume, the tunnel dials out", () => {
  const boot = read("infra/gpu/runpod-bootstrap.sh");
  assert.ok(boot.indexOf("orazaka-watchdog armed") < 0 && boot.indexOf("watchdog armed") < boot.indexOf("building the worker runtime"));
  assert.match(boot, /api\.runpod\.io\/v2\/pods\/\$\{RUNPOD_POD_ID\}/); // the pod ends itself
  assert.match(boot, /git clone -q --filter=blob:none --no-checkout https:\/\/github\.com\/krizaka\/sovkora\.git/);
  assert.match(boot, /MEDIA_MODELS_DIR=\/models/);
  assert.match(boot, /127\.0\.0\.1:15671:broker-dev\.orazaka\.com:5671/);
  assert.doesNotMatch(boot, /ghcr\.io/); // the private registry is never needed
});

test("runpod bootstrap: the venv lives on the network volume, so a later pod reuses it (no rebuild, no idle build for the spend guard to stop)", () => {
  const boot = read("infra/gpu/runpod-bootstrap.sh");
  assert.match(boot, /VENV=\/models\/venv/);
  assert.match(boot, /ln -sfn \/models\/venv \/opt\/venv/);
  assert.doesNotMatch(boot, /python3 -m venv \/opt\/venv/);
});

test("runpod bootstrap: the optional video upscaler (SeedVR2 3B, Apache-2.0) is a pinned checkout with its own venv on the volume, and the worker is told where", () => {
  const boot = read("infra/gpu/runpod-bootstrap.sh");
  assert.match(boot, /UP=\/models\/up/);
  assert.match(boot, /SEEDVR2_REF=[0-9a-f]{40}/); // pinned, never a branch
  assert.match(boot, /git clone -q https:\/\/github\.com\/numz\/ComfyUI-SeedVR2_VideoUpscaler/);
  assert.match(boot, /SEEDVR2_HOME=\/models\/up\/seedvr2 SEEDVR2_PYTHON=\/models\/up\/venv\/bin\/python/); // main's UPSCALER_ENV: set where the volume holds the install (this block puts it there)
  assert.match(boot, /ln -sfn \$UP\/seedvr \/models\/seedvr2/); // the weights live where upscale.py looks
  assert.ok(boot.indexOf("SEEDVR2_HOME") < boot.indexOf("app.consumer"), "the worker starts with the upscaler's location in its environment");
});

test("runpod watchdog: the pod destroys ITSELF — through the API with the pod's own key, then runpodctl; with no key it says so and falls back", () => {
  const boot = read("infra/gpu/runpod-bootstrap.sh");
  const watchdog = boot.slice(boot.indexOf("<<'WD'\n") + 7, boot.indexOf("\nWD\n"));
  const dir = mkdtempSync(join(tmpdir(), "rp-watchdog-"));
  const bin = join(dir, "bin"); mkdirSync(bin);
  const script = join(dir, "watchdog.sh"); writeFileSync(script, watchdog, { mode: 0o755 });
  const calls = join(dir, "calls.log");
  // stubs: curl records the request and answers 204; runpodctl records its arguments
  writeFileSync(join(bin, "curl"), `#!/bin/sh\necho "curl $*" >> ${calls}\nprintf 204\n`, { mode: 0o755 });
  writeFileSync(join(bin, "runpodctl"), `#!/bin/sh\necho "runpodctl $*" >> ${calls}\n`, { mode: 0o755 });
  const run = (env) => execFileSync("bash", [script, "--destroy", "idle for 31 min"], { encoding: "utf8", env: { PATH: `${bin}:/usr/bin:/bin`, ...env } });
  const withKey = run({ RUNPOD_POD_ID: "pod123", RUNPOD_API_KEY: "k-test" });
  assert.match(withKey, /destroyed through the API \(204\)/);
  assert.match(readFileSync(calls, "utf8"), /-X DELETE .*Bearer k-test.*api\.runpod\.io\/v2\/pods\/pod123/);
  // no key in the environment: the REST call is skipped, runpodctl (authenticated by the pod) removes it
  writeFileSync(calls, "");
  const noKey = run({ RUNPOD_POD_ID: "pod123" });
  assert.match(noKey, /no RUNPOD_API_KEY/);
  assert.match(noKey, /destroyed through runpodctl/);
  assert.equal(readFileSync(calls, "utf8").trim(), "runpodctl remove pod pod123");
  // no pod id at all: it refuses rather than guess
  assert.throws(() => run({}), /Command failed/);
});

test("runpod bootstrap (cinema, ADR-080): the profile's image engine is served on :8086 through the relay, sound is enabled, scratch weights go to the container disk", () => {
  const boot = read("infra/gpu/runpod-bootstrap.sh");
  assert.match(boot, /MEDIA_SCRATCH_DIR=\/var\/lib\/orazaka-scratch/);
  assert.match(boot, /get\('image'\)/); // read from models.yaml, never a hard-coded model
  assert.match(boot, /\/models\/bin\/sd-server-\$\{SDCPP_REF:0:12\}/); // built once, kept on the volume
  assert.match(boot, /IMAGE_REMOTE="R:0\.0\.0\.0:8086:127\.0\.0\.1:8086"/);
  assert.match(boot, /R:0\.0\.0\.0:8188:127\.0\.0\.1:8188 \$IMAGE_REMOTE/);
  assert.match(boot, /MEDIA_SOUND_ENABLED="\$SOUND_FLAG"/);
  assert.match(boot, /fonts-dejavu-core/); // the cut's titles
  assert.match(boot, /SEEDVR2_HOME=\/models\/up\/seedvr2/); // the upscaler on the volume, when it is there
  assert.match(boot, /MEDIA_SPEECH_PORT=8086 MEDIA_IMAGE_UPSTREAM=127\.0\.0\.1:8089/); // speech in front of sd-server
  assert.match(boot, /get\('speech'\)/);
  assert.match(boot, /MEDIA_FONT_FILE="\$FONT"/); // the Krizaka type for titles
  const lib = read("scripts/lib/gpu-runpod.sh");
  assert.match(read("scripts/runpod-guard.py"), /"disk": int\(e\.get\("DISK_GB"\) or 60\)/);
  assert.match(lib, /HF_TOKEN="\$\(secret AI_HUGGINGFACE_TOKEN/); // read from the secrets file, never an argument
  assert.match(lib, /worker_ref="\$\{GPU_WORKER_REF:-/);
});

test("a RunPod batch runs on a remote GPU: it never takes a local heavy-lock slot (orazaka#508)", () => {
  for (const file of ["scripts/gpu-dev.sh", "scripts/lib/gpu-runpod.sh", "scripts/runpod-batch.mjs"]) {
    assert.doesNotMatch(read(file), /heavy-lock|HEAVY_SLOTS/, `${file} must not take the Mac's slots`);
  }
});

test("secrets come from the secret tool by logical source, never from ~/Documents (the vault moved to ~/.krizaka-private, 2026-10-27)", () => {
  for (const file of ["scripts/gpu-dev.sh", "scripts/lib/gpu-runpod.sh"]) {
    assert.doesNotMatch(read(file), /Documents\/krizaka\/env/, `${file} must not read secrets from ~/Documents`);
  }
  // No ORAZAKA_DEV_ENV_FILE: the values are read through KRIZAKA_SECRET_TOOL (`secret.sh @dev KEY`), and user-data renders.
  const dir = mkdtempSync(join(tmpdir(), "gpu-dev-tool-"));
  const tool = join(dir, "secret.sh");
  const values = {
    IDENTITY_JWT_SECRET: JWT,
    RABBITMQ_PASS_GPU: "rabbit-from-tool",
    AI_GPU_RELAY_PASSWORD: "relay-from-tool-0123456789abcdef",
    GPU_WATCHDOG_DO_TOKEN: "dop_v1_tool",
    RABBITMQ_CA_CERT_BASE64: Buffer.from("-----BEGIN CERTIFICATE-----\nTOOL\n-----END CERTIFICATE-----\n").toString("base64"),
  };
  writeFileSync(
    tool,
    `#!/bin/sh\necho "$1 $2" >> "${join(dir, "calls")}"\ncase "$2" in\n${Object.entries(values)
      .map(([k, v]) => `  ${k}) [ "$1" = "@dev" ] && printf '%s' '${v}' && exit 0 ;;`)
      .join("\n")}\nesac\nexit 1\n`,
    { mode: 0o755 },
  );
  const out = join(dir, "user-data.yaml");
  const env = { ...process.env, KRIZAKA_SECRET_TOOL: tool, GPU_REGIONS: "tor1" };
  delete env.ORAZAKA_DEV_ENV_FILE;
  execFileSync("bash", [join(root, "scripts/gpu-dev.sh"), "user-data", "small", out], { env });
  const text = readFileSync(out, "utf8");
  const relay = [...text.matchAll(/content: (\S+)/g)].map((m) => Buffer.from(m[1], "base64").toString()).join("\n");
  assert.match(relay, /AUTH=gpu:relay-from-tool/);
  assert.ok(readFileSync(join(dir, "calls"), "utf8").split("\n").every((l) => !l || l.startsWith("@dev ")));
});

test("runpod batch passes --no-upload through when GPU_BATCH_NO_UPLOAD is set (the results stay local for review)", () => {
  assert.match(read("scripts/lib/gpu-runpod.sh"), /\$\{GPU_BATCH_NO_UPLOAD:\+--no-upload\}/);
});
