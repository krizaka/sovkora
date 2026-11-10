#!/usr/bin/env node
/**
 * Image → video through Orazaka's Studios (docs/DEPLOY_DEV.md § GPU): each clip of a plan is one run of the
 * `video-generation` Studio on dev.sovkora.com, routed to the GPU worker. It never talks to a model directly.
 *
 *   ORAZAKA_QA_EMAIL=… ORAZAKA_QA_PASSWORD=… node scripts/gpu-i2v-run.mjs --plan plan.json --out <dir> [--images <dir>] [--only id,id] [--rate 0.76]
 *
 * A plan is `{ "model": "wan2.2-ti2v-5b", "clips": [{ "id", "image": "<file>", "aspect": "16:9"|"9:16", "prompt", "seed", "seconds" }] }`
 * (image paths are relative to --images, default the plan's folder). Per clip: the photo is uploaded (`/api/v1/media/upload`), the Studio is run
 * with its asset id, the run is followed to the end, the clip is downloaded, and the FACTS the run itself reported
 * (model, size, frames, fps, seed, duration — from the job's metrics, never from this script's wish) are written
 * to `<out>/<id>.json` beside `<id>.mp4`. A clip is never marked good here: a person looks at it (warps are rejected).
 * Cost = wall seconds of the run × the GPU's hourly rate / 3600. Credentials are read from the environment, never printed.
 */
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { basename, dirname, join, resolve } from "node:path";
import { toolPath } from "./lib/tool-path.mjs";

const BASE = process.env.ORAZAKA_EDGE_URL ?? "https://dev.sovkora.com/edge";
const arg = (name, fallback) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : fallback;
};

let token;
/** One call, retried on a network failure (the edge answers slowly now and then): a clip must not be lost to a socket. */
async function call(method, path, body, { form, raw } = {}) {
  let last;
  for (let attempt = 1; attempt <= 6; attempt++) {
    try {
      const res = await fetch(BASE + path, {
        signal: AbortSignal.timeout(raw ? 300_000 : 60_000), // a hung socket is a failed attempt, retried
        method,
        headers: {
          Accept: "application/json",
          ...(form || body === undefined ? {} : { "Content-Type": "application/json" }),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
      });
      if (!res.ok) {
        const refused = new Error(`${method} ${path} → ${res.status}`);
        refused.final = res.status < 500; // a 4xx is an answer; a 5xx or a dropped socket is retried
        throw refused;
      }
      return raw ? res : res.json();
    } catch (e) {
      last = e;
      if (e.final) break;
      await new Promise((r) => setTimeout(r, 3000 * attempt));
    }
  }
  throw last;
}

/** The facts a finished run's jobs reported (their own metrics), merged: what the clip is, as the platform measured it. */
export function factsOf(jobs) {
  const facts = {};
  for (const job of jobs) {
    const result = job?.result ?? {};
    const metrics = result.metrics ?? {};
    for (const k of ["model", "seed", "width", "height", "frames", "fps", "durationSeconds", "aspect", "steps", "generationMs", "gpu", "provider", "gpuSeconds", "inference_time_sec", "peak_memory_rss_mb", "gpu_allocated_mb", "imageKept", "upscaleModel", "upscaleFrom", "upscaleTo", "upscaleSeconds"]) {
      const v = metrics[k] ?? result[k];
      if (v !== undefined && v !== null) facts[k] = v;
    }
  }
  return facts;
}

/** The Studio inputs of one clip: the plan's model unless the clip names its own (LTX or Wan HQ per shot), and the optional `upscale`. */
export function clipInputs(plan, clip, assetId) {
  return {
    prompt: clip.prompt,
    durationSeconds: clip.seconds ?? 5,
    aspect: clip.aspect,
    seed: String(clip.seed),
    model: clip.model ?? plan.model,
    ...(assetId ? { image: assetId } : {}),
    ...(clip.upscale ? { upscale: clip.upscale } : {}),
  };
}

/** Seconds a run took by the platform's own clock (its start and finish), not by when this script noticed. */
export const runSeconds = (run) => (run?.startedAt && run?.finishedAt ? Math.round((Date.parse(run.finishedAt) - Date.parse(run.startedAt)) / 1000) : undefined);

export const gpuCostUsd = (seconds, ratePerHour) => Math.round((seconds / 3600) * ratePerHour * 10000) / 10000;

/** The last frame of a clip, as the PNG the next run starts from. */
export function lastFrame(video, png) {
  if (!existsSync(video)) throw new Error(`${video} is not there: the clip to continue from has not been made`);
  execFileSync(toolPath("ffmpeg"), ["-v", "error", "-y", "-sseof", "-0.2", "-i", video, "-update", "1", "-frames:v", "1", png]);
  return png;
}

async function runClip(plan, clip, imagesDir, out, rate) {
  const ticket = join(out, `${clip.id}.run`); // a run in flight survives this process: its id and start are kept here
  let started = Date.now();
  let runId;
  if (existsSync(ticket)) {
    ({ runId, started } = JSON.parse(readFileSync(ticket, "utf8")));
    console.log(`i2v: ${clip.id} resumes run ${runId}`);
  } else {
    // `continueFrom`: the photo is the LAST frame of an earlier clip of this batch (the second half of a 10 s clip, two linked runs).
    // No `image` at all: a text-to-video clip (Wan 2.2 A14B at 720p) — nothing is uploaded.
    let assetId;
    if (clip.continueFrom || clip.image) {
      const imagePath = clip.continueFrom ? lastFrame(join(out, `${clip.continueFrom}.mp4`), join(out, `${clip.id}.start.png`)) : resolve(imagesDir, clip.image);
      const bytes = readFileSync(imagePath);
      const form = new FormData();
      form.append("file", new Blob([bytes], { type: "image/png" }), basename(imagePath));
      const uploaded = await call("POST", "/api/v1/media/upload", undefined, { form });
      assetId = uploaded.assetId ?? uploaded.id;
      if (!assetId) throw new Error("the upload answered no asset id");
    }
    const inputs = clipInputs(plan, clip, assetId);
    runId = (await call("POST", "/api/v1/studios/video-generation/runs", { inputs })).id;
    writeFileSync(ticket, JSON.stringify({ runId, started }));
  }
  const accepted = { id: runId };
  let run = { status: "RUNNING" };
  const limit = (Number(arg("timeout-min", "60")) || 60) * 60_000;
  while (["PENDING", "RUNNING", "QUEUED", "AWAITING_APPROVAL"].includes(run.status) && Date.now() - started < limit) {
    await new Promise((r) => setTimeout(r, 15_000));
    try {
      run = await call("GET", `/api/v1/studios/runs/${runId}`);
    } catch (e) {
      console.log(`i2v: ${clip.id} poll failed (${e.message}); still waiting`); // a flaky network is not a failed clip
    }
  }
  const seconds = Math.round((Date.now() - started) / 1000);
  const jobs = [];
  for (const step of run.steps ?? []) if (step.jobId) jobs.push(await call("GET", `/api/v1/jobs/${step.jobId}`).catch(() => null));
  const record = {
    id: clip.id,
    model: clip.model ?? plan.model,
    sourceImage: clip.continueFrom ? `last frame of ${clip.continueFrom}` : (clip.image || null),
    aspect: clip.aspect,
    prompt: clip.prompt,
    requestedSeed: String(clip.seed),
    requestedSeconds: clip.seconds ?? 5,
    runId: accepted.id,
    status: run.status,
    pollSeconds: seconds, // when this script saw it finish: includes any network gap, so it is not the run's time
    runSeconds: runSeconds(run) ?? seconds, // the platform's own clock: start to finish of the run
    gpuRateUsdPerHour: rate,
    gpuCostUsd: gpuCostUsd(runSeconds(run) ?? seconds, rate),
    reported: factsOf(jobs.filter(Boolean)),
    error: run.status === "SUCCEEDED" ? undefined : (run.errorMessage ?? `status ${run.status}`),
  };
  if (run.status === "SUCCEEDED") {
    const output = (run.outputs ?? []).find((o) => typeof (o.value ?? o.url) === "string");
    const url = output?.value ?? output?.url;
    if (!url) throw new Error("the run succeeded without an output url");
    const file = await call("GET", url.startsWith("/") ? url : `/${url}`, undefined, { raw: true });
    const data = Buffer.from(await file.arrayBuffer());
    if (data.length < 10_000) throw new Error(`the clip is ${data.length} bytes: not a video`);
    writeFileSync(join(out, `${clip.id}.mp4`), data);
    record.bytes = data.length;
  }
  writeFileSync(join(out, `${clip.id}.json`), JSON.stringify(record, null, 2) + "\n");
  rmSync(ticket, { force: true });
  return record;
}

async function main() {
  const planFile = resolve(arg("plan"));
  const out = resolve(arg("out"));
  const rate = Number(arg("rate", "0.76"));
  const only = arg("only")?.split(",");
  const plan = JSON.parse(readFileSync(planFile, "utf8"));
  const email = process.env.ORAZAKA_QA_EMAIL;
  const password = process.env.ORAZAKA_QA_PASSWORD;
  if (!email || !password) throw new Error("ORAZAKA_QA_EMAIL / ORAZAKA_QA_PASSWORD are not set (read them with secret.sh)");
  mkdirSync(out, { recursive: true });
  token = (await call("POST", "/api/v1/auth/login", { email, password })).token;
  let spent = 0;
  for (const clip of plan.clips.filter((c) => !only || only.includes(c.id))) {
    if (existsSync(join(out, `${clip.id}.json`)) && !process.argv.includes("--redo")) {
      console.log(`i2v: ${clip.id} already recorded (use --redo)`);
      continue;
    }
    try {
      let r;
      // The platform keeps an upload on the core's own disk, which a redeploy of the app wipes: a run whose photo vanished
      // before the worker fetched it is made again from a fresh upload (never counted twice: its record is replaced).
      for (let attempt = 1; attempt <= 3; attempt++) {
        r = await runClip(plan, clip, resolve(arg("images", dirname(planFile))), out, rate);
        if (r.status === "SUCCEEDED" || !/starting image/.test(r.error ?? "")) break;
        console.log(`i2v: ${clip.id} lost its photo to a redeploy (attempt ${attempt}); uploading it again`);
        spent += r.gpuCostUsd;
        rmSync(join(out, `${clip.id}.json`), { force: true });
      }
      spent += r.gpuCostUsd;
      const got = r.reported;
      console.log(`i2v: ${clip.id.padEnd(22)} ${r.status.padEnd(9)} ${String(r.runSeconds).padStart(5)} s  ${got.width ?? "?"}x${got.height ?? "?"}  ${got.frames ?? "?"} fr  $${r.gpuCostUsd}  ${r.error ?? ""}`);
    } catch (e) {
      console.log(`i2v: ${clip.id.padEnd(22)} ERROR ${e.message}`);
    }
  }
  console.log(`i2v: batch GPU cost ≈ $${spent.toFixed(2)}`);
}

if (import.meta.url === `file://${process.argv[1]}`) main().catch((e) => {
  console.error(`i2v: ${e.message}`);
  process.exit(1);
});
