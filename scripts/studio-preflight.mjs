#!/usr/bin/env node
/**
 * The FREE preflight of a paid GPU run (orazaka#508): before a pod is rented, ask the dev Studio the exact question the batch will ask
 * (same Studio, same model, same size) and stop if it would answer a 4xx for a reason a booted pod cannot fix.
 *
 *   ORAZAKA_QA_EMAIL=… ORAZAKA_QA_PASSWORD=… node scripts/studio-preflight.mjs --model flux-1-schnell --size 1024x1024
 *
 * A pod that is not up yet makes the Studio answer « <model> is not running (its engine could not be reached) »: that answer PROVES the
 * model is in the catalogue, in the closed set, and the size fits it, so it passes. Any other 4xx (« is not a image model », « is not
 * supported by », a missing field, a rejected login) fails: renting the GPU would only bill minutes before the same refusal.
 * A run the Studio accepts is cancelled at once (nothing is rendered, nothing is held).
 *
 * Second free check, with --worker-ref <commit>: the worker commit the pod will run must list the model in its models.yaml. The Studio's
 * « not running » passes whatever the worker is, so a pin that predates the model (41d64f0 and flux-1-schnell) would rent a GPU that never
 * serves it: the ref is read from GitHub and refused before any pod.
 */
import { execFileSync } from "node:child_process";
import { edgeBase, edgeUrl, idSegment, oneLine } from "./lib/edge-url.mjs";
import { toolPath } from "./lib/tool-path.mjs";

/** @returns {{ok: boolean, reason: string}} the verdict on one Studio answer. */
export function judge(status, body) {
  const message = typeof body === "string" ? body : (body?.message ?? JSON.stringify(body ?? ""));
  if (status >= 200 && status < 300) return { ok: true, reason: "the Studio accepts the run" };
  if (status === 400 && /is not running \(its engine could not be reached\)/.test(message)) return { ok: true, reason: "the model is in the catalogue; only its engine is down (a pod fixes that)" };
  return { ok: false, reason: oneLine(`the Studio would refuse (${status}): ${message}`).slice(0, 400) };
}

/**
 * The engine itself (guard (a), krizaka-internal#138): `up` when the Studio accepts the run, `down` while it cannot reach the engine,
 * `refused` for anything a pod cannot fix. `--require-engine` exits 0 on `up` only (3 on `down`): the batch polls it before its first prompt.
 */
export function engineState(status, body) {
  const verdict = judge(status, body);
  if (!verdict.ok) return "refused";
  return status >= 200 && status < 300 ? "up" : "down";
}

/** True when the worker's models.yaml declares the model as a catalogue entry (two-space indent: `  flux-1-schnell:`). */
export function workerListsModel(yaml, model) {
  const escaped = model.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`^ {2}["']?${escaped}["']?:\\s*(#.*)?$`, "m").test(yaml);
}

/** models.yaml of the worker at one commit, read with the app credential (never printed). */
function workerModelsAt(ref) {
  const credential = process.env.GH_TOKEN ?? execFileSync(toolPath("python3"), [`${process.env.HOME}/.krizaka-worktrees/tools/gh-app-token.py`, "agents"], { encoding: "utf8" }).trim();
  return execFileSync(toolPath("gh"), ["api", `repos/krizaka/sovkora/contents/workers/media/models.yaml?ref=${ref}`, "-H", "Accept: application/vnd.github.raw"], { encoding: "utf8", env: { ...process.env, GH_TOKEN: credential } });
}

async function call(method, path, body, token) {
  const res = await fetch(edgeUrl(edgeBase(), path), {
    signal: AbortSignal.timeout(60_000),
    method,
    headers: { Accept: "application/json", ...(body ? { "Content-Type": "application/json" } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let parsed = text;
  try { parsed = JSON.parse(text); } catch { /* keep the text */ }
  return { status: res.status, body: parsed };
}

const ENGINE_EXIT = { up: 0, down: 3 };

/** Refuses (exit 1) a worker ref that does not list the model: the pod would never serve it. */
function checkWorkerRef(workerRef, model) {
  let yaml;
  try { yaml = workerModelsAt(workerRef); } catch { console.log(`preflight: REFUSED — worker ref ${workerRef.slice(0, 12)} cannot be read from GitHub`); process.exit(1); }
  if (!workerListsModel(yaml, model)) { console.log(`preflight: REFUSED — worker ref ${workerRef.slice(0, 12)} does not list ${model} in models.yaml (it would never serve it)`); process.exit(1); }
  console.log(`preflight: worker ref ${workerRef.slice(0, 12)} lists ${model}`);
}

async function main() {
  const arg = (name, fallback) => { const i = process.argv.indexOf(`--${name}`); return i > 0 ? process.argv[i + 1] : fallback; };
  const workerRef = arg("worker-ref");
  if (workerRef) checkWorkerRef(workerRef, arg("model"));
  const { ORAZAKA_QA_EMAIL: email, ORAZAKA_QA_PASSWORD: password } = process.env;
  if (!email || !password) throw new Error("ORAZAKA_QA_EMAIL / ORAZAKA_QA_PASSWORD are not set");
  const login = await call("POST", "/api/v1/auth/login", { email, password });
  if (login.status !== 200 || !login.body?.token) throw new Error(`login answered ${login.status}`);
  const token = login.body.token;
  const answer = await call("POST", "/api/v1/studios/image-generation/runs", { inputs: { prompt: "preflight", size: arg("size", "1024x1024"), model: arg("model") } }, token);
  if (answer.status >= 200 && answer.status < 300 && answer.body?.id) await call("POST", `/api/v1/studios/runs/${idSegment(answer.body.id)}/cancel`, undefined, token).catch(() => {});
  if (process.argv.includes("--require-engine")) {
    const state = engineState(answer.status, answer.body);
    console.log(`preflight: engine ${state}`);
    process.exit(ENGINE_EXIT[state] ?? 1);
  }
  const verdict = judge(answer.status, answer.body);
  console.log(`preflight: ${verdict.ok ? "ok" : "REFUSED"} — ${verdict.reason}`);
  process.exit(verdict.ok ? 0 : 1);
}

if (import.meta.url === `file://${process.argv[1]}`) main().catch((e) => { console.error(`preflight: ${e.message}`); process.exit(1); });
