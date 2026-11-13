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
  assert.match(fn, /exec .*-m app\.consumer/);
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
