// node --test scripts/test — a pod that destroys itself leaves its end reason and boot log OFF the pod, in ITS OWN output zone (krizaka-internal#2121).
// Incident 2026-11-01: an H200 pod (run 7 of the vertical film) vanished ~19 min after creation; the API said 404 and nothing on our side
// said why. The pod's watchdog now PUTs <day>/<run>/<pod>/end.json and boot.log straight to the dedicated zone (krizaka-gpu-out) BEFORE the delete,
// with that zone's key only: a compromised pod touches that zone, never krizaka-assets.
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { clipKind, expiredDays, podEnvProblems, podOutputEnv, readSettings } from "../lib/gpu-out.mjs";

const root = new URL("../../", import.meta.url).pathname;
const bootstrap = readFileSync(join(root, "infra/gpu/runpod-bootstrap.sh"), "utf8");
const watchdog = bootstrap.slice(bootstrap.indexOf("<<'WD'\n") + 7, bootstrap.indexOf("\nWD\n"));
const dir = mkdtempSync(join(tmpdir(), "watchdog-trace-"));
const file = join(dir, "watchdog.sh");
writeFileSync(file, watchdog);

/** A fake `curl` that records each call (method, url, body) in `out` instead of using the network. */
function fakeCurl(bin, out) {
  writeFileSync(
    join(bin, "curl"),
    `#!/usr/bin/env bash
n=$(ls "${out}" | wc -l); body=""; method=GET; url=""
while [[ $# -gt 0 ]]; do case "$1" in -X) method=$2; shift;; --data-binary) body=\${2#@}; shift;; -w|-o|-H|-m) shift;; -K) shift; cat >/dev/null;; http*) url=$1;; esac; shift; done
{ echo "$method $url"; [[ -n "$body" ]] && cat "$body"; } > "${out}/$n"
[[ "$method" == DELETE ]] && printf 204
exit 0
`,
    { mode: 0o755 },
  );
}

test("guard: a watchdog destroy leaves a readable end reason and the boot log before the delete", () => {
  const work = mkdtempSync(join(tmpdir(), "wd-trace-"));
  const [bin, out, state] = ["bin", "out", "state/state"].map((d) => join(work, d));
  [bin, out, state].forEach((d) => mkdirSync(d, { recursive: true }));
  writeFileSync(join(work, "state", "boot-epoch"), String(Math.floor(Date.now() / 1000) - 19 * 60));
  writeFileSync(join(state, "progress"), "62 115\n");
  fakeCurl(bin, out);
  const r = spawnSync("bash", [file, "--destroy", "weights download failed"], {
    encoding: "utf8",
    timeout: 10000,
    env: {
      ...process.env, PATH: `${bin}:${process.env.PATH}`, ORAZAKA_STATE: join(work, "state"), RUNPOD_POD_ID: "pod123", RUNPOD_API_KEY: "k",
      GPU_OUT_URL: "https://zone.example/krizaka-gpu-out/2026-11-01/run-1", GPU_OUT_HEADER: "AccessKey", GPU_OUT_KEY: "sentinel-value",
    },
  });
  assert.equal(r.status, 0, r.stderr);
  const calls = readdirSync(out).sort((a, b) => a - b).map((f) => readFileSync(join(out, f), "utf8"));
  const iEnd = calls.findIndex((c) => c.startsWith("PUT https://zone.example/krizaka-gpu-out/2026-11-01/run-1/pod123/end.json"));
  const iDelete = calls.findIndex((c) => c.startsWith("DELETE "));
  assert.ok(iEnd >= 0, `end.json is written (${calls.map((c) => c.split("\n")[0])})`);
  assert.ok(iEnd < iDelete, "the reason is left BEFORE the pod is deleted");
  const trace = JSON.parse(calls[iEnd].split("\n").slice(1).join("\n"));
  assert.equal(trace.reason, "weights download failed");
  assert.equal(trace.pod, "pod123");
  assert.equal(trace.downloadProgress.trim(), "62 115");
  assert.ok(trace.ageMinutes >= 18 && trace.ageMinutes <= 20, `age ${trace.ageMinutes}`);
  assert.ok(calls.some((c) => c.startsWith("PUT https://zone.example/krizaka-gpu-out/2026-11-01/run-1/pod123/boot.log")), "the boot log is left too");
  assert.ok(!r.stdout.includes("sentinel-value") && !r.stderr.includes("sentinel-value"), "the zone key is never printed");
});

test("a pod without storage settings still destroys itself and says the reason stays on the pod", () => {
  const work = mkdtempSync(join(tmpdir(), "wd-nostore-"));
  const [bin, out] = ["bin", "out"].map((d) => join(work, d));
  [bin, out].forEach((d) => mkdirSync(d));
  fakeCurl(bin, out);
  const env = { ...process.env, PATH: `${bin}:${process.env.PATH}`, ORAZAKA_STATE: work, RUNPOD_POD_ID: "p", RUNPOD_API_KEY: "k" };
  for (const k of ["GPU_OUT_URL", "GPU_OUT_KEY", "GPU_OUT_HEADER"]) delete env[k];
  const r = spawnSync("bash", [file, "--destroy", "idle"], { encoding: "utf8", timeout: 10000, env });
  assert.equal(r.status, 0);
  assert.match(r.stdout, /no output zone/);
  assert.match(r.stdout, /destroyed through the API/);
});

test("a rented pod holds the output zone's key and no site credential: only the gpu-out pair, never the krizaka-assets key (krizaka-internal#2121)", () => {
  const files = ["scripts/runpod-guard.py", "scripts/lib/gpu-runpod.sh", "infra/gpu/runpod-bootstrap.sh", "infra/gpu/runpod-measure.sh", "infra/gpu/host.sh"];
  for (const f of files) {
    const text = readFileSync(join(root, f), "utf8");
    assert.ok(!/MEDIA_STORAGE|MEDIA_ACCOUNT|BUNNY_|krizaka-assets/i.test(text), `${f} names a site storage credential or the sites' zone`);
  }
  // the pod's variables are an allow-list: the gpu-out pair passes, the sites' key does not
  const ok = podEnvProblems({ GPU_OUT_URL: "u", GPU_OUT_HEADER: "AccessKey", GPU_OUT_KEY: "zone-key", POD_TOKEN: "t" }, ["assets-key"]);
  assert.deepEqual(ok, []);
  assert.equal(podEnvProblems({ SERVICE_TOKEN: "t" }).length, 1, "a pod never carries a SERVICE token (krizaka-internal#3645)");
  assert.ok(podEnvProblems({ MEDIA_STORAGE_API_KEY: "assets-key" }, ["assets-key"]).length >= 2, "MEDIA_STORAGE_API_KEY is refused by name and by value");
  assert.ok(podEnvProblems({ GPU_OUT_KEY: "assets-key" }, ["assets-key"]).length === 1, "the sites' key under the gpu-out name is refused by value");
  assert.ok(podEnvProblems({ MEDIA_ACCOUNT_API_KEY: "x" }).length >= 1, "the Bunny account key is refused");
  assert.ok(podEnvProblems({ AWS_SECRET_ACCESS_KEY: "x" }).length === 1, "an unknown variable is refused");
});

test("the pod's environment is built from the settings: the zone under the run's prefix, nothing else, a refused provider starts nothing", () => {
  const s = readSettings(readFileSync(join(root, "infra/gpu/gpu-out.settings.json"), "utf8"));
  assert.equal(s.coordinates.zone, "krizaka-gpu-out");
  const env = podOutputEnv(s, "zone-key", { day: "2026-11-01", run: "run-1" });
  assert.deepEqual(Object.keys(env).sort(), ["GPU_OUT_HEADER", "GPU_OUT_KEY", "GPU_OUT_URL"]);
  assert.equal(env.GPU_OUT_URL, "https://storage.bunnycdn.com/krizaka-gpu-out/2026-11-01/run-1");
  assert.throws(() => podOutputEnv({ ...s, provider: "s3" }, "k", { day: "2026-11-01", run: "r" }), /cannot write to a "s3" store/);
  assert.throws(() => podOutputEnv(s, "", { day: "2026-11-01", run: "r" }), /empty/);
  assert.throws(() => podOutputEnv(s, "k", { day: "2026-11-01", run: "../x" }), /safe path/);
  assert.equal(s.credentialRef, "MEDIA_GPU_STORAGE_API_KEY", "the settings hold a vault name, never a value");
});

test("retention: only date folders at least retentionDays old expire; a clip is promoted only when its bytes are mp4/webm", () => {
  const keys = ["2026-10-20/r/p/end.json", "2026-10-25/r/p/a.mp4", "2026-10-26/r/p/b.mp4", "2026-11-01/r/p/c.mp4", "stray.txt"];
  assert.deepEqual(expiredDays(keys, "2026-11-02", 7), ["2026-10-20", "2026-10-25", "2026-10-26"]);
  assert.deepEqual(expiredDays(keys, "2026-11-02", 30), []);
  assert.equal(clipKind(Uint8Array.from([0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d])), "mp4");
  assert.equal(clipKind(Uint8Array.from([0x1a, 0x45, 0xdf, 0xa3, 0, 0, 0, 0, 0, 0, 0, 0])), "webm");
  assert.equal(clipKind(new TextEncoder().encode("<html>not a clip</html>")), null);
  assert.equal(clipKind(new Uint8Array(0)), null);
});

test("the worker process gets the pod's output zone, so a clip is written there before the sink (krizaka-internal#2149)", () => {
  const launch = bootstrap.split("\n").find((l) => l.includes("python -u -m app.consumer") === false && l.startsWith("env $UPSCALER_ENV"));
  for (const name of ["GPU_OUT_URL", "GPU_OUT_HEADER", "GPU_OUT_KEY"]) assert.match(launch, new RegExp(`${name}=`));
});
