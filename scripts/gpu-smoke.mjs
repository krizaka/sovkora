#!/usr/bin/env node
/**
 * The sovereign smoke (docs/DEPLOY_DEV.md § GPU): proves OUR runtime generates — Ollama, sd-server, the media worker —
 * through Orazaka's own routing on dev.sovkora.com, as the seeded test user. Never a third-party model API.
 *
 *   node scripts/gpu-smoke.mjs [--host droplet|mac] [--out <file>] [--only chat,image,video]
 *
 * It does not start or stop anything: `scripts/gpu-dev.sh smoke` starts the `small` GPU, runs this, and destroys the
 * droplet whatever happens (trap); `scripts/gpu-dev.sh smoke mac` runs it against the Mac's engines on the relay.
 *
 * Each check runs a Studio and asserts: the run SUCCEEDED, its output is non-empty, the latency, and — where the step
 * reports it — the model that answered, from our side (the job's own metadata, never a claim of the client).
 * Checks this environment cannot route are reported SKIPPED with the reason, never passed.
 *
 * Credentials: ORAZAKA_QA_EMAIL / ORAZAKA_QA_PASSWORD, else the seeded user in the QA store (secret.sh @qa
 * ORAZAKA_USER_EMAIL / ORAZAKA_USER_PASSWORD, written by `dev-deploy.mjs seed`).
 * Nothing secret is printed or written to the report.
 */
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";

const BASE = process.env.ORAZAKA_EDGE_URL ?? "https://dev.sovkora.com/edge";
const arg = (name, fallback) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : fallback;
};

/** The checks, each one Studio run. `skip` names why an environment cannot route it — reported, never passed. */
export const CHECKS = [
  {
    name: "chat",
    engine: "ollama",
    studio: "lead-research",
    // Sovereign or nothing: a chat answered by a cloud API is not our runtime, whatever it says.
    requireProvider: "ollama",
    install: { tone: "direct", signature: "Orazaka smoke" },
    inputs: {
      prospects: ["Boulangerie Le Fournil, Montréal — 3 boutiques"],
      offer: "Une IA privée qui rédige devis et relances sans que les données quittent l'entreprise.",
      icp: "PME québécoises de 5 à 50 employés",
    },
    timeoutS: 300,
  },
  {
    name: "image",
    engine: "sd-server",
    studio: "image-generation",
    inputs: { prompt: "a ceramic espresso cup on a walnut table, morning light, editorial photograph", size: "768x1024", seed: "7" },
    timeoutS: 600,
  },
  {
    name: "video",
    engine: "worker-media",
    studio: "video-generation",
    inputs: { prompt: "steam rising from a cup of coffee, slow push-in, warm light", durationSeconds: 1, aspect: "9:16", model: "wan2.1-t2v-1.3b", seed: "7" },
    timeoutS: 1500,
  },
  { name: "tts", engine: "localai", skip: "speech synthesis runs on LocalAI (piper), which no dev host serves yet" },
  { name: "transcription", engine: "localai", skip: "transcription runs on LocalAI (whisper), which no dev host serves yet" },
  { name: "rag", engine: "knowledge", skip: "the knowledge service is not deployed in dev (docs/DEPLOY_DEV.md)" },
];

/** One key of the QA store, through secret.sh (the value stays in this process, never printed); "" when absent. */
function qaSecret(key) {
  const r = spawnSync(join(homedir(), ".krizaka-worktrees/tools/secret.sh"), ["@qa", key], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
  return r.status === 0 ? r.stdout : "";
}

export function credentials(env = process.env, read = qaSecret) {
  if (env.ORAZAKA_QA_EMAIL && env.ORAZAKA_QA_PASSWORD) return { email: env.ORAZAKA_QA_EMAIL, password: env.ORAZAKA_QA_PASSWORD };
  const email = read("ORAZAKA_USER_EMAIL");
  const password = read("ORAZAKA_USER_PASSWORD");
  if (!email || !password) throw new Error("no QA credentials (ORAZAKA_QA_EMAIL/PASSWORD, or ORAZAKA_USER_* in the QA store: dev-deploy.mjs seed)");
  return { email, password };
}

/** What a finished run proves: SUCCEEDED, a non-empty output, and the model our job reported, when it did. */
/** What our own jobs reported, read from each step's job (never from the client): models, providers and the facts. */
export function jobFacts(jobs) {
  const models = new Set();
  const facts = [];
  for (const job of jobs) {
    const result = job?.result ?? {};
    const metrics = result.metrics ?? {};
    const metadata = result.metadata ?? {};
    for (const m of [result.model, metrics.model, metadata.model]) if (typeof m === "string" && m) models.add(m);
    if (typeof metadata.provider === "string") models.add(`provider:${metadata.provider}`);
    const f = {};
    for (const k of ["seed", "width", "height", "frames", "fps", "durationSeconds", "aspect", "gpu", "generationMs", "steps"]) {
      const v = metrics[k] ?? result[k];
      if (v !== undefined && v !== null) f[k] = v;
    }
    if (Object.keys(f).length) facts.push({ jobId: job.id, ...f });
  }
  return { models: [...models], facts };
}

export function judge(check, run, ms, reported = { models: [], facts: [] }) {
  const outputs = run.outputs ?? [];
  const nonEmpty = outputs.some((o) => {
    const v = o.value ?? o.url ?? o.content;
    return typeof v === "string" ? v.trim().length > 0 : v != null;
  });
  const models = new Set(reported.models);
  for (const o of outputs) {
    const text = typeof o.value === "string" ? o.value : JSON.stringify(o.value ?? "");
    for (const m of text.matchAll(/"?model"?\s*[:=]\s*"?([A-Za-z0-9._:/-]+)/g)) models.add(m[1]);
    for (const m of text.matchAll(/provider:\s*([a-z-]+)/g)) models.add(`provider:${m[1]}`);
  }
  const providers = [...models].filter((m) => m.startsWith("provider:")).map((m) => m.slice(9));
  const foreign = check.requireProvider && !providers.includes(check.requireProvider);
  const ok = run.status === "SUCCEEDED" && nonEmpty && !foreign;
  return {
    name: check.name,
    engine: check.engine,
    studio: check.studio,
    status: ok ? "PASSED" : "FAILED",
    ms,
    runId: run.id,
    models: [...models],
    facts: reported.facts,
    detail: ok
      ? `${outputs.length} output(s)`
      : foreign && run.status === "SUCCEEDED"
        ? `answered by ${providers.join(", ") || "an undeclared provider"}, not ${check.requireProvider}`
        : run.errorMessage ?? `status ${run.status}, empty output`,
  };
}

let token;
async function call(method, path, body, raw = false) {
  const res = await fetch(BASE + path, {
    method,
    headers: { "Content-Type": "application/json", Accept: "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status}`);
  return raw ? res : res.json();
}

async function runCheck(check) {
  if (check.skip) return { name: check.name, engine: check.engine, status: "SKIPPED", detail: check.skip };
  const started = Date.now();
  try {
    if (check.install) await call("POST", `/api/v1/studios/${check.studio}/installations`, { config: check.install }).catch(() => {});
    const accepted = await call("POST", `/api/v1/studios/${check.studio}/runs`, { inputs: check.inputs });
    let run = accepted;
    while (["PENDING", "RUNNING", "QUEUED", "AWAITING_APPROVAL"].includes(run.status) && Date.now() - started < check.timeoutS * 1000) {
      await new Promise((r) => setTimeout(r, 10_000));
      run = await call("GET", `/api/v1/studios/runs/${accepted.id}`);
    }
    const elapsed = Date.now() - started;
    const jobs = [];
    for (const step of run.steps ?? []) {
      if (step.jobId) jobs.push(await call("GET", `/api/v1/jobs/${step.jobId}`).catch(() => null));
    }
    return judge(check, run, elapsed, jobFacts(jobs.filter(Boolean)));
  } catch (e) {
    return { name: check.name, engine: check.engine, studio: check.studio, status: "FAILED", ms: Date.now() - started, detail: e.message };
  }
}

async function main() {
  const host = arg("host", "droplet");
  const only = arg("only")?.split(",");
  const { email, password } = credentials();
  token = (await call("POST", "/api/v1/auth/login", { email, password })).token;
  const report = { suite: "orazaka-sovereign-smoke", base: BASE, host, startedAt: new Date().toISOString(), checks: [] };
  for (const check of CHECKS.filter((c) => !only || only.includes(c.name))) {
    const result = await runCheck(check);
    console.log(`smoke: ${result.name.padEnd(13)} ${result.status.padEnd(7)} ${result.ms ? `${(result.ms / 1000).toFixed(0)} s` : ""} ${result.detail}`);
    report.checks.push(result);
  }
  report.finishedAt = new Date().toISOString();
  report.passed = report.checks.every((c) => c.status !== "FAILED") && report.checks.some((c) => c.status === "PASSED");
  const out = arg("out", join(homedir(), "Documents/krizaka/orazaka-smoke/last.json"));
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, JSON.stringify(report, null, 2) + "\n");
  console.log(`smoke: ${report.passed ? "PASSED" : "FAILED"} — report ${out}`);
  process.exit(report.passed ? 0 : 1);
}

if (import.meta.url === `file://${process.argv[1]}`) main().catch((e) => {
  console.error(`smoke: ${e.message}`);
  process.exit(1);
});
