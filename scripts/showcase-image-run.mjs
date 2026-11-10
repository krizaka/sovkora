#!/usr/bin/env node
/**
 * The first step of the home page's "Prompt → Image → Video": each clip of a plan gets its image from a run of the
 * `image-generation` Studio on dev.sovkora.com (never a model called directly). The facts the run reported are written to
 * `<out>/<id>.image.json` beside `<id>.image.png`; a person looks at the image before it goes on (nothing is marked good here).
 *
 *   ORAZAKA_QA_EMAIL=… ORAZAKA_QA_PASSWORD=… node scripts/showcase-image-run.mjs --plan plan.json --out <dir> [--only id,id] [--redo] [--model stable-diffusion-3.5-large] [--size 1024x1024]
 *
 * A plan is the one of gpu-i2v-run.mjs, each clip carrying `scene` (the still's prompt). Credentials come from the environment, never printed.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { runSeconds, factsOf } from "./gpu-i2v-run.mjs";

const BASE = process.env.ORAZAKA_EDGE_URL ?? "https://dev.sovkora.com/edge";
const arg = (name, fallback) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : fallback;
};
let token;
async function call(method, path, body, raw) {
  const res = await fetch(BASE + path, {
    signal: AbortSignal.timeout(300_000),
    method,
    headers: { Accept: "application/json", ...(body ? { "Content-Type": "application/json" } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  // The Studio's message is kept: the batch reads « engine could not be reached » from it (runpod-batch.mjs, ENGINE_DOWN).
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${(await res.text().catch(() => "")).slice(0, 300)}`);
  return raw ? res : res.json();
}

/** The inputs of one image run: the Studio's own fields, nothing the script invents. */
export const imageInputs = (clip, model, size) => ({
  prompt: clip.scene,
  size: clip.size ?? size,
  model: clip.imageModel ?? model,
  ...(clip.imageSeed ? { seed: String(clip.imageSeed) } : {}),
  // The same character in every shot (krizaka-internal#158): up to 4 assets (the outputs of earlier runs) the image follows.
  ...(clip.references?.length ? { references: clip.references } : {}),
});

async function main() {
  const plan = JSON.parse(readFileSync(resolve(arg("plan")), "utf8"));
  const out = resolve(arg("out"));
  const model = arg("model", "stable-diffusion-3.5-large");
  const size = arg("size", "1024x1024");
  const only = arg("only")?.split(",");
  const { ORAZAKA_QA_EMAIL: email, ORAZAKA_QA_PASSWORD: password } = process.env;
  if (!email || !password) throw new Error("ORAZAKA_QA_EMAIL / ORAZAKA_QA_PASSWORD are not set (read them with secret.sh)");
  mkdirSync(out, { recursive: true });
  token = (await call("POST", "/api/v1/auth/login", { email, password })).token;
  for (const clip of plan.clips.filter((c) => !only || only.includes(c.id))) {
    const record = join(out, `${clip.id}.image.json`);
    if (existsSync(record) && !process.argv.includes("--redo")) continue;
    const accepted = await call("POST", "/api/v1/studios/image-generation/runs", { inputs: imageInputs(clip, model, size) });
    let run = { status: "RUNNING" };
    const started = Date.now();
    while (["PENDING", "RUNNING", "QUEUED", "AWAITING_APPROVAL"].includes(run.status) && Date.now() - started < 600_000) {
      await new Promise((r) => setTimeout(r, 5000));
      run = await call("GET", `/api/v1/studios/runs/${accepted.id}`).catch(() => run);
    }
    const jobs = [];
    for (const step of run.steps ?? []) if (step.jobId) jobs.push(await call("GET", `/api/v1/jobs/${step.jobId}`).catch(() => null));
    const facts = { id: clip.id, runId: accepted.id, status: run.status, model: clip.imageModel ?? model, requestedSize: clip.size ?? size, prompt: clip.scene, runSeconds: runSeconds(run), reported: factsOf(jobs.filter(Boolean)) };
    if (run.status === "SUCCEEDED") {
      const o = (run.outputs ?? []).find((x) => typeof (x.value ?? x.url) === "string");
      const url = o?.value ?? o?.url;
      const data = Buffer.from(await (await call("GET", url.startsWith("/") ? url : `/${url}`, undefined, true)).arrayBuffer());
      if (data.length < 20_000) throw new Error(`${clip.id}: ${data.length} bytes is not an image`);
      writeFileSync(join(out, `${clip.id}.image.png`), data);
      facts.bytes = data.length;
    }
    writeFileSync(record, JSON.stringify(facts, null, 2) + "\n");
    console.log(`image: ${clip.id.padEnd(16)} ${facts.status} ${facts.runSeconds ?? "?"} s ${facts.bytes ?? ""} ${accepted.id}`);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) main().catch((e) => { console.error(`image: ${e.message}`); process.exit(1); });
