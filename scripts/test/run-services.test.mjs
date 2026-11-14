// node --test scripts/test — the core container's supervisor (infra/deploy/run-services.sh).
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const script = fileURLToPath(new URL("../../infra/deploy/run-services.sh", import.meta.url));

/** Runs bash with the script in library mode, then `body`. */
function bash(body, timeout = 15000) {
  return spawnSync("bash", ["-c", `RUN_SERVICES_LIB=1 source "${script}"; ${body}`], {
    encoding: "utf8",
    timeout,
    env: { ...process.env, ORAZAKA_WORKER_BACKOFF_START: "1", ORAZAKA_WORKER_BACKOFF_MAX: "2" },
  });
}

test("library mode defines the supervisor and starts nothing", () => {
  const r = bash("type supervise >/dev/null && echo defined");
  assert.equal(r.status, 0, r.stderr);
  assert.equal(r.stdout.trim(), "defined");
});

test("a failing worker is restarted with a doubling, capped backoff while the platform keeps running", () => {
  // The worker fails at once; the "JVM" (sleep 4) is what the container waits on — it must end normally.
  const r = bash(`
    log=$(mktemp); supervise flaky false > "$log" 2>&1 &
    sup=$!
    sleep 4 & jvm=$!
    wait -n $jvm; jvm_status=$?
    kill $sup; cat "$log"; rm -f "$log"
    echo "jvm=$jvm_status"`);
  const restarts = (r.stdout.match(/worker flaky exited \(status 1\)/g) ?? []).length;
  assert.ok(restarts >= 2, `restarted ${restarts}×:\n${r.stdout}${r.stderr}`);
  assert.match(r.stdout, /restarting in 1s/);
  assert.match(r.stdout, /restarting in 2s/);
  assert.doesNotMatch(r.stdout, /restarting in 4s/); // capped at ORAZAKA_WORKER_BACKOFF_MAX
  assert.match(r.stdout, /jvm=0/);
});

test("a stopped container takes its worker down: SIGTERM ends the supervisor and the worker it supervises (no stray consumer)", () => {
  // (pgrep -f would match this very script on Linux, so the pattern is written so that it cannot match its own text.)
  // Before: the supervisor had no TERM handler, so the container's `wait` hung on it until the platform's SIGKILL, and the
  // worker kept consuming the broker's queue next to the new deployment's worker (orazaka validation queue, 2026-10-20).
  const r = bash(`
    n=31337; supervise w sleep $n > /dev/null 2>&1 &
    sup=$!
    sleep 1
    kill -TERM $sup
    for i in 1 2 3 4 5 6 7 8 9 10; do kill -0 $sup 2>/dev/null || break; sleep 0.5; done
    kill -0 $sup 2>/dev/null && echo "supervisor-alive" || echo "supervisor-gone"
    for i in 1 2 3 4 5 6 7 8 9 10; do pgrep -f "sleep 3133[7]" >/dev/null || break; sleep 0.2; done
    pgrep -f "sleep 3133[7]" >/dev/null && echo "worker-alive" || echo "worker-gone"
    pkill -f "sleep 3133[7]" 2>/dev/null; true`);
  assert.match(r.stdout, /supervisor-gone/, r.stdout + r.stderr);
  assert.match(r.stdout, /worker-gone/, r.stdout + r.stderr);
});

test("the container stops its supervised workers when it stops (stop_all signals them)", () => {
  const text = readFileSync(script, "utf8");
  assert.match(text, /workers\+=\("\$!"\)/);
  assert.match(text, /for pid in "\$\{pids\[@\]\}" "\$\{workers\[@\]\}"; do kill -TERM/);
});

// ── The hosted-video worker: dev's cheap, always-on consumer for the models a provider serves (no GPU) ──
const root = (path) => readFileSync(new URL(`../../${path}`, import.meta.url), "utf8");

test("the supervisor starts the hosted-video worker by default, beside the validation worker", () => {
  const sh = root("infra/deploy/run-services.sh");
  assert.match(sh, /ORAZAKA_WORKERS-validation hosted-video/);
  assert.match(sh, /hosted-video\)\s*\n\s*echo "\[run-services\] starting the hosted-video worker \(supervised\)"/);
});

test("the hosted-video worker is the media worker in hosted-only mode, on the lean venv, handing its output to the job service", () => {
  const sh = root("infra/deploy/run-services.sh");
  const fn = sh.slice(sh.indexOf("run_hosted_video_worker()"), sh.indexOf("# The Python workers of the packs"));
  assert.match(fn, /MEDIA_WORKER_HOSTED_ONLY=true/);
  assert.match(fn, /orazaka-worker-media/);
  assert.match(fn, /MEDIA_ASSET_SINK_URL=http:\/\/localhost:8090/);
  assert.match(fn, /JOB_SERVICE_INTERNAL_URL=http:\/\/localhost:8090/);
  assert.match(fn, /AI_INFERENCE_KEY="\$\{AI_INFERENCE_KEY_ALL:-\$\{AI_INFERENCE_KEY:-\}\}"/, "the all-models key lists the hosted Wan");
  assert.match(fn, /exec env -i[\s\S]*-m app\.consumer/);
  // The worker resolves its upload root from its own file (…/orazaka-worker-media/app/main.py, 4 levels up = « / » in the image):
  // « Permission denied: /var/orazaka-uploads ». The image's own uploads folder (owned by the orazaka user) is named explicitly.
  assert.match(fn, /UPLOAD_DIR="\$\{ORAZAKA_APP_DIR:-\/app\}\/var\/orazaka-uploads"/);
  // The media worker reads Spring's variable names for the broker (SPRING_RABBITMQ_*), the container carries RABBITMQ_*: without
  // this map it dialled localhost as guest and never connected (dev, 2026-10-20).
  for (const [spring, plain] of [["HOST", "HOST"], ["PORT", "PORT"], ["USERNAME", "USER"], ["PASSWORD", "PASS"]]) {
    assert.match(fn, new RegExp(`SPRING_RABBITMQ_${spring}="\\$\\{RABBITMQ_${plain}:-\\}"`), `SPRING_RABBITMQ_${spring} from RABBITMQ_${plain}`);
  }
});

test("the image carries the worker and only its lean dependencies: no torch, no GPU library", () => {
  const docker = root("infra/deploy/Dockerfile");
  assert.match(docker, /cp -r workers\/media \/out\/orazaka-worker-media/);
  const pip = docker.match(/pip install --no-cache-dir ([^\n]*)/)?.[1] ?? "";
  for (const needed of ["pika==", "pyyaml==", "cryptography==", "pillow=="]) assert.ok(pip.toLowerCase().includes(needed), `${needed} in ${pip}`);
  for (const heavy of ["torch", "diffusers", "mlx", "transformers"]) assert.ok(!pip.includes(heavy), heavy);
});

test("the app spec gives the core container the all-models inference key as a SECRET placeholder", () => {
  const spec = root("infra/deploy/sovkora-dev.app.yaml");
  assert.match(spec, /key: AI_INFERENCE_KEY_ALL, scope: RUN_TIME, type: SECRET, value: "__AI_INFERENCE_KEY_ALL__"/);
});

test("dev data gives hosted video its own queue, and migrates the table of the first version in place", () => {
  const sql = root("infra/deploy/dev-data.sql");
  assert.match(sql, /ADD COLUMN IF NOT EXISTS hosted BOOLEAN NOT NULL DEFAULT FALSE/);
  assert.match(sql, /\('video', TRUE,\s+'job\.hostedvideo\.generate'\)/);
  assert.match(sql, /\('video', FALSE,\s+'job\.video\.generate'\)/);
  assert.match(sql, /ON CONFLICT \(category, hosted\) DO NOTHING/);
});

// ── Start order: the job service answers before the services that call it at startup are started ──
test("wait_port returns as soon as something listens, and gives up (status 1) after its timeout", async () => {
  const { createServer } = await import("node:net");
  const server = createServer().listen(0, "127.0.0.1");
  await new Promise((resolve) => server.once("listening", resolve));
  const { port } = server.address();
  const up = bash(`wait_port 127.0.0.1 ${port} 5 && echo up=0 || echo up=1`);
  assert.match(up.stdout, /up=0/, up.stderr);
  await new Promise((resolve) => server.close(resolve));
  const started = Date.now();
  const down = bash(`wait_port 127.0.0.1 ${port} 2 && echo down=0 || echo down=1`);
  assert.match(down.stdout, /down=1/, down.stderr);
  assert.ok(Date.now() - started < 8000, "bounded by its timeout");
});

test("the supervisor waits for the job service before it starts the services that call it (8 JVMs in 2 GB started Studio before job: PackInstallException, exit 1)", () => {
  const sh = root("infra/deploy/run-services.sh");
  assert.match(sh, /job\) wait_port localhost 8090 "\$\{ORAZAKA_JOB_READY_TIMEOUT:-240\}"/);
});

// ── The dev chat route is data the service applies at startup (krizaka-internal#3285, ADR-095) ──
test("the core container's supervisor adds the dev-only migration location of the job service, and the job service reads it", () => {
  const sh = root("infra/deploy/run-services.sh");
  assert.match(sh, /export DATABASE_JOB_MIGRATIONS_LOCATIONS="\$\{DATABASE_JOB_MIGRATIONS_LOCATIONS:-classpath:db\/migration,classpath:db\/dev\}"/);
  const yml = root("services/job/sovkora-job-service/src/main/resources/application.yml");
  assert.match(yml, /locations: \$\{DATABASE_JOB_MIGRATIONS_LOCATIONS:classpath:db\/migration\}/);
});

test("the dev route migration puts the chat default on the hosted per-token provider and nothing else is a chat default", () => {
  const sql = root("services/job/sovkora-job-service/src/main/resources/db/dev/R__dev_chat_route.sql");
  assert.match(sql, /'mistral-3-14B'[\s\S]*'do-inference'/);
  assert.match(sql, /UPDATE orazaka_models SET is_default = \(model_name = 'mistral-3-14B'\) WHERE category = 'chat'/);
});

// ── A worker started inside core receives a closed list of variables, never core's whole environment (krizaka-internal#3295) ──
import { chmodSync, mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

/** The names of a bash array `NAME=(a b c)` of the script, in order. */
function closedList(name) {
  const m = root("infra/deploy/run-services.sh").match(new RegExp(`^${name}=\\(([^)]*)\\)`, "m"));
  assert.ok(m, `${name} is declared once as an array in run-services.sh`);
  return m[1].split(/\s+/).filter(Boolean);
}

// The live names are read from the code, not typed: a rename of the product's prefix moves the code and this test follows it.
const liveScript = root("infra/deploy/run-services.sh");
const APP_DIR_VAR = liveScript.match(/\$\{(\w+_APP_DIR):-\/app\}/)[1];
const PYTHON_VAR = liveScript.match(/\$\{(\w+_VALIDATION_PYTHON):-/)[1];
const PACKS_DIR = liveScript.match(/^  cd "\$\{\w+_APP_DIR:-\/app\}\/([^"]+)\/\$pack\/worker" \|\| return 1$/m)[1];
const WORKER_DIRS = [
  ...[...liveScript.matchAll(/^  cd "\$\{\w+_APP_DIR:-\/app\}\/([^"$]+)" \|\| return 1$/gm)].map((m) => m[1]),
  ...[...liveScript.matchAll(/^run_\w+_worker\(\) \{ run_pack_worker ([\w-]+) /gm)].map((m) => `${PACKS_DIR}/${m[1]}/worker`),
];
// The functional name first, then the retired code-named one the worker still reads until the pods are rebuilt (#3619).
const SERVICE_TOKEN_VARS = [...root("workers/media/app/registration.py").matchAll(/environ\.get\("(\w+_SERVICE_TOKEN)"\)/g)].map((m) => m[1]);
const [SERVICE_TOKEN_VAR] = SERVICE_TOKEN_VARS;
const IDENTITY_SECRET_VAR = root("workers/media/app/registration.py").match(/environ\.get\("(IDENTITY_JWT_SECRET)"\)/)[1];

/** What the worker process really receives: the worker function runs with a poisoned core environment and a stand-in interpreter that prints its env. */
function workerEnvironment(fn) {
  const dir = mkdtempSync(join(tmpdir(), "worker-env-"));
  for (const sub of WORKER_DIRS) mkdirSync(join(dir, sub), { recursive: true });
  const python = join(dir, "fake-python");
  writeFileSync(python, "#!/bin/sh\nexec /usr/bin/env\n");
  chmodSync(python, 0o755);
  const poison = {
    DATABASE_PASSWORD: "x", DATABASE_URL: "x", IDENTITY_JWT_SECRET: "x", ...Object.fromEntries(SERVICE_TOKEN_VARS.map((v) => [v, "x"])), SERVICE_TOKEN: "x",
    MEDIA_ENCRYPTION_KEY: "x", STRIPE_SECRET: "x", BILLING_API_KEY: "x", AI_INFERENCE_KEY_ALL: "all", AI_INFERENCE_KEY: "k",
    RABBITMQ_HOST: "broker", RABBITMQ_PORT: "5671", RABBITMQ_USER: "u", RABBITMQ_PASS: "p", SPRING_RABBITMQ_SSL_ENABLED: "true",
  };
  const r = spawnSync("bash", ["-c", `RUN_SERVICES_LIB=1 source "${script}"; ${fn}`], {
    encoding: "utf8", timeout: 15000,
    env: { ...process.env, ...poison, [APP_DIR_VAR]: dir, [PYTHON_VAR]: python },
  });
  assert.equal(r.status, 0, r.stderr);
  return Object.fromEntries(r.stdout.split("\n").filter(Boolean).map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]));
}

const BASE = () => closedList("WORKER_ENV_BASE");
const BROKER = () => closedList("WORKER_ENV_BROKER");
const FIXED = { validation: ["SPRING_RABBITMQ_SSL_CA_FILE"], data: ["SPRING_RABBITMQ_SSL_CA_FILE"], hostedVideo: ["MEDIA_WORKER_HOSTED_ONLY", "MEDIA_WORKER_PORT", "MEDIA_ASSET_SINK_URL", "JOB_SERVICE_INTERNAL_URL", "UPLOAD_DIR", "AI_INFERENCE_KEY", "SPRING_RABBITMQ_HOST", "SPRING_RABBITMQ_PORT", "SPRING_RABBITMQ_USERNAME", "SPRING_RABBITMQ_PASSWORD", "SPRING_RABBITMQ_SSL_CA_FILE"] };
const WORKERS = {
  validation: { fn: "run_validation_worker", list: () => [...BASE(), ...BROKER(), ...closedList("WORKER_ENV_VALIDATION")], fixed: FIXED.validation },
  data: { fn: "run_data_worker", list: () => [...BASE(), ...BROKER()], fixed: FIXED.data },
  "hosted-video": { fn: "run_hosted_video_worker", list: () => [...BASE(), ...BROKER(), ...closedList("WORKER_ENV_HOSTED_VIDEO")], fixed: FIXED.hostedVideo },
};

for (const [name, w] of Object.entries(WORKERS)) {
  test(`the ${name} worker's environment is the closed list and nothing else (no database secret, no SERVICE token, no other secret)`, () => {
    const env = workerEnvironment(w.fn);
    const allowed = new Set([...w.list(), ...w.fixed, "PWD", "OLDPWD", "SHLVL", "_"]);
    const extra = Object.keys(env).filter((k) => !allowed.has(k));
    assert.deepEqual(extra, [], `variables outside the closed list reach the ${name} worker`);
    for (const secret of ["DATABASE_PASSWORD", "DATABASE_URL", IDENTITY_SECRET_VAR, ...SERVICE_TOKEN_VARS, "SERVICE_TOKEN", "MEDIA_ENCRYPTION_KEY", "STRIPE_SECRET", "BILLING_API_KEY", "AI_INFERENCE_KEY_ALL"]) {
      assert.ok(!(secret in env), `${secret} must not reach the ${name} worker`);
    }
  });

  test(`the ${name} closed list names no secret but the ones the worker needs`, () => {
    const needed = new Set(["RABBITMQ_PASS", "AI_INFERENCE_KEY", "SPRING_RABBITMQ_PASSWORD"]);
    for (const v of [...w.list(), ...w.fixed]) {
      if (/(PASSWORD|SECRET|KEY|TOKEN|PASS)$/.test(v) || /^(DATABASE|IDENTITY|SERVICE)_/.test(v)) assert.ok(needed.has(v), `${v} is a secret the ${name} worker does not need`);
    }
    assert.ok(!(name !== "hosted-video" && [...w.list(), ...w.fixed].includes("AI_INFERENCE_KEY")), `${name} never calls a provider`);
  });
}

/** Names a Python source reads from the environment: `.get("X")`, `read("X")`, `_first(src, "X", "Y")`, and the ENV_SWITCH constant. */
function readNames(...files) {
  const names = new Set();
  for (const f of files) {
    const src = root(f);
    for (const m of src.matchAll(/(?:\.get|read|environ)\(\s*"([A-Z][A-Z0-9_]{3,})"/g)) names.add(m[1]);
    for (const m of src.matchAll(/_first\(\s*\w+((?:,\s*"[A-Z][A-Z0-9_]+")+)/g)) for (const n of m[1].matchAll(/"([A-Z0-9_]+)"/g)) names.add(n[1]);
    for (const m of src.matchAll(/^ENV_SWITCH = "([A-Z0-9_]+)"/gm)) names.add(m[1]);
  }
  return names;
}

test("every variable the validation worker reads is in its closed list, or its CA file the script sets itself", () => {
  const read = readNames("packs/worker-common/worker_broker.py", "packs/document-validation/worker/validation_worker.py", "packs/document-validation/worker/lecture.py");
  const have = new Set([...WORKERS.validation.list(), ...WORKERS.validation.fixed]);
  // RABBITMQ_PASSWORD / SPRING_RABBITMQ_PASSWORD are deprecated names (worker warns); the script passes RABBITMQ_PASS only.
  const deprecated = new Set(["RABBITMQ_PASSWORD", "SPRING_RABBITMQ_PASSWORD", "RABBITMQ_SSL_CA_FILE"]);
  for (const v of read) assert.ok(have.has(v) || deprecated.has(v), `${v} is read by the validation worker but is not in the closed list`);
});

test("the supervisor starts the data-analytics worker by default, and it reads nothing but the broker", () => {
  const sh = root("infra/deploy/run-services.sh");
  assert.match(sh, /ORAZAKA_WORKERS-validation hosted-video data\}/);
  assert.match(sh, /data\)\s*\n\s*echo "\[run-services\] starting the data-analytics worker \(supervised\)"/);
  const read = readNames("packs/worker-common/worker_broker.py", ...["data_worker", "facts", "dashboard"].map((m) => `packs/data-analytics/worker/${m}.py`));
  const have = new Set([...WORKERS.data.list(), ...WORKERS.data.fixed]);
  const deprecated = new Set(["RABBITMQ_PASSWORD", "SPRING_RABBITMQ_PASSWORD", "RABBITMQ_SSL_CA_FILE"]);
  for (const v of read) assert.ok(have.has(v) || deprecated.has(v), `${v} is read by the data worker but is not in the closed list`);
});

test("every variable the hosted-video worker reads on its hosted-only path is in its closed list, or refused on purpose", () => {
  const read = readNames(...["broker", "asset_sink", "remote_video", "spend", "registration", "activity"].map((m) => `workers/media/app/${m}.py`));
  const have = new Set([...WORKERS["hosted-video"].list(), ...WORKERS["hosted-video"].fixed]);
  // Refused on purpose: with neither the worker never registers with the job service and still consumes (registration.py).
  const refused = new Set([...SERVICE_TOKEN_VARS, IDENTITY_SECRET_VAR]);
  for (const v of read) assert.ok(have.has(v) || refused.has(v), `${v} is read by the hosted-video worker but is not in the closed list`);
  for (const v of refused) assert.ok(!have.has(v), `${v} must stay out of the hosted-video worker's environment`);
});
