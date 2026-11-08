#!/usr/bin/env node
/**
 * The unattended render batch (orazaka#508), driven by `scripts/gpu-dev.sh runpod batch`: N prompts → one image each, through the
 * `image-generation` Studio of dev.sovkora.com (scripts/showcase-image-run.mjs, never a model called directly) on the pod the shell
 * started → the results to Bunny under `site/renders/<batch>/`. The dollar cap is enforced HERE between every prompt and by the
 * shell around it (the pod is terminated in a trap whatever happens: this script never touches the pod).
 *
 *   node scripts/runpod-batch.mjs --prompts prompts.txt --out <dir> --rate <usd/h> --started <epoch s> --cap <usd> [--refs refs.json] [--model m] [--size WxH] [--batch name] [--no-upload]
 *
 * prompts.txt: one prompt per line (blank lines and `#` comments ignored). The Studio login and the Bunny keys are read from the
 * process settings by name (ORAZAKA_QA_*, MEDIA_STORAGE_*, MEDIA_CDN_HOSTNAME as scripts/pr-assets.mjs), never printed.
 */
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { bootBudgetMinutes, progressLine } from "./lib/boot-budget.mjs";

/**
 * One clip per prompt, ids `r01`, `r02`… (the plan format of gpu-i2v-run.mjs, only `scene` and `references` are read by the image step).
 * A line may start with `[owl, cahier]`: the names of reference pictures, resolved by `refs` (name → asset id of an earlier run's output)
 * so the same character comes back from shot to shot (krizaka-internal#158). An unknown name stops the batch before any pod time is spent.
 */
export function planFromPrompts(text, refs = {}) {
  const prompts = text.split("\n").map((line) => line.trim()).filter((line) => line && !line.startsWith("#"));
  const width = Math.max(2, String(prompts.length).length);
  return {
    clips: prompts.map((line, i) => {
      const id = `r${String(i + 1).padStart(width, "0")}`;
      const m = /^\[([^\]]*)\]\s*(.+)$/.exec(line);
      if (!m) return { id, scene: line };
      const references = m[1].split(",").map((n) => n.trim()).filter(Boolean).map((name) => {
        if (!refs[name]) throw new Error(`${id}: the reference "${name}" is not in --refs`);
        return refs[name];
      });
      return { id, scene: m[2], ...(references.length ? { references } : {}) };
    }),
  };
}

/** What the pod has cost by `nowMs` (dollars): hours × rate, from the epoch-seconds `startedS`. */
export const spentUsd = (rate, startedS, nowMs) => Math.round(((nowMs / 1000 - startedS) / 3600) * rate * 10000) / 10000;

/** True when one more prompt must NOT start: the cap is reached, or the next prompt (at `perPromptUsd`) would cross it. */
export const capReached = (spent, cap, perPromptUsd = 0) => spent + perPromptUsd >= cap;

/** The Studio's answer while the pod's engine is not reachable (guard (b) reads it in the image step's error). */
export const ENGINE_DOWN = /is not running \(its engine could not be reached\)/;

/** A run the Studio ended FAILED (the image step prints `image: <id>  FAILED <s> s <runId>`): guard (c) counts them per prompt. */
export const RUN_FAILED = /^image: \S+\s+FAILED\b/m;

/** True once the engine wait (guard (a)) has used its `minutes`, counted from the pod's start `startedS` (epoch seconds). */
export const engineWaitOver = (startedS, nowMs, minutes) => nowMs / 1000 - startedS >= minutes * 60;

/**
 * Guard (b): the streak of « engine could not be reached » after the engine was proved up. Three in a row, or five minutes since the
 * first of them, abort the batch (the shell's trap ends the pod). Any other outcome resets it.
 */
export function engineStreak(previous, unreachable, nowMs, { max = 3, minutes = 5 } = {}) {
  if (!unreachable) return { count: 0, since: undefined, abort: false };
  const count = (previous?.count ?? 0) + 1;
  const since = previous?.since ?? nowMs;
  return { count, since, abort: count >= max || nowMs - since >= minutes * 60_000 };
}

function readProgress(file) { try { return file ? readFileSync(file, "utf8") : undefined; } catch { return undefined; } }

/** Guard (a): polls the Studio (studio-preflight.mjs --require-engine) until the engine answers; throws when the wait or the cap runs out. */
async function waitForEngine({ started, rate, cap, minutes }) {
  for (let attempt = 1; ; attempt++) {
    const args = [join(here, "studio-preflight.mjs"), "--require-engine", ...(arg("model") ? ["--model", arg("model")] : []), ...(arg("size") ? ["--size", arg("size")] : [])];
    const probe = spawnSync(process.execPath, args, { env: process.env, encoding: "utf8", timeout: 90_000 });
    if (probe.status === 0) { console.log(`batch: engine reachable after ${Math.round(Date.now() / 1000 - started)} s`); return; }
    if (probe.status === 1) throw new Error(`the Studio refuses the run: ${(probe.stdout + probe.stderr).trim().slice(0, 300)}`);
    if (engineWaitOver(started, Date.now(), minutes)) throw new Error(`the engine was not reachable after ${minutes} min (${progressLine(readProgress(process.env.GPU_POD_PROGRESS_FILE))}) — ending the pod`);
    if (capReached(spentUsd(rate, started, Date.now()), cap)) throw new Error(`cap $${cap} reached while the engine was still down`);
    if (attempt % 4 === 1) console.log(`batch: engine not reachable yet (${Math.round(Date.now() / 1000 - started)} s)`);
    await new Promise((res) => setTimeout(res, 20_000));
  }
}

/** The Bunny storage URL and the CDN URL of one result (a batch name is a slug, never a path). */
export function bunnyTarget(batch, id, settings) {
  if (!/^[a-z0-9][a-z0-9-]{0,63}$/.test(batch)) throw new Error(`batch name "${batch}" must be a lowercase slug`);
  const path = `site/renders/${batch}/${id}.png`;
  return { path, put: `https://${settings.MEDIA_STORAGE_ENDPOINT ?? "storage.bunnycdn.com"}/${settings.MEDIA_STORAGE_ZONE}/${path}`, cdn: `https://${settings.MEDIA_CDN_HOSTNAME}/${path}` };
}

async function upload(file, target, settings) {
  const res = await fetch(target.put, { method: "PUT", headers: { AccessKey: settings.MEDIA_STORAGE_API_KEY, "Content-Type": "image/png" }, body: readFileSync(file) });
  if (!res.ok) throw new Error(`Bunny answered ${res.status} for ${target.path}`);
}

const here = dirname(fileURLToPath(import.meta.url));
const arg = (name, fallback) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : fallback;
};

async function main() {
  const refs = arg("refs") ? JSON.parse(readFileSync(resolve(arg("refs")), "utf8")) : {};
  const plan = planFromPrompts(readFileSync(resolve(arg("prompts")), "utf8"), refs);
  if (process.argv.includes("--validate-only")) { console.log(`batch: ${plan.clips.length} prompts, ${plan.clips.filter((c) => c.references).length} with references — valid`); return; }
  const out = resolve(arg("out"));
  const rate = Number(arg("rate")), started = Number(arg("started")), cap = Number(arg("cap"));
  if (![rate, started, cap].every(Number.isFinite) || cap <= 0) throw new Error("--rate, --started and --cap are required numbers");
  const batch = arg("batch", new Date().toISOString().slice(0, 10) + "-batch");
  const bootMinutes = Number(process.env.GPU_BATCH_BOOT_MINUTES ?? 20);
  // A stills pod listens in minutes (STILLS=1): the engine wait is short, and it is a hard stop, not a retry of real prompts.
  // With GPU_BATCH_DOWNLOAD_GB (the model's `downloadGb` in the catalogue) the wait is sized to the download, not to a flat limit.
  const engineMinutes = Number(process.env.GPU_BATCH_ENGINE_MINUTES ?? bootBudgetMinutes({ downloadGb: Number(process.env.GPU_BATCH_DOWNLOAD_GB ?? 0) }));
  mkdirSync(out, { recursive: true });
  writeFileSync(join(out, "plan.json"), JSON.stringify(plan));
  const results = [];
  const done = () => finishBatch({ results, plan, out, batch, rate, started, cap });

  await waitForEngine({ started, rate, cap, minutes: engineMinutes });
  const run = { rate, started, cap, bootMinutes, out, perPrompt: 0, streak: undefined, failed: undefined, rendered: 0 };
  for (const clip of plan.clips) {
    const png = await renderClip(clip, run);
    if (!png) return done();
    results.push({ id: clip.id, file: png });
    run.rendered = results.length;
  }
  return done();
}

/** Renders one prompt, retrying until it succeeds. Returns its file, or null when the cap stops the batch before it. */
async function renderClip(clip, run) {
  const png = join(run.out, `${clip.id}.image.png`);
  run.failed = undefined;
  // The pod boots for minutes before its worker answers: a prompt is retried until it succeeds; the cap or the boot window stops it.
  while (!existsSync(png)) {
    const spent = spentUsd(run.rate, run.started, Date.now());
    if (capReached(spent, run.cap, run.perPrompt)) {
      console.log(`batch: cap $${run.cap} reached at $${spent.toFixed(2)} — stopping before ${clip.id}`);
      return null;
    }
    if (run.rendered === 0 && Date.now() / 1000 - run.started > run.bootMinutes * 60) throw new Error(`no render after ${run.bootMinutes} min of boot — giving up`);
    const t0 = Date.now();
    renderAttempt({ clip, png, out: run.out, state: run });
    if (existsSync(png)) run.perPrompt = Math.max(run.perPrompt, spentUsd(run.rate, 0, Date.now()) - spentUsd(run.rate, 0, t0));
    else await new Promise((res) => setTimeout(res, 30_000));
  }
  return png;
}

/** Sends the results to Bunny (unless --no-upload) and writes results.tsv. */
async function finishBatch({ results, plan, out, batch, rate, started, cap }) {
  const lines = [];
  for (const r of results) {
    if (process.argv.includes("--no-upload")) { lines.push(`${r.id}\t${r.file}`); continue; }
    const target = bunnyTarget(batch, r.id, process.env);
    await upload(r.file, target, process.env);
    lines.push(`${r.id}\t${target.cdn}`);
  }
  writeFileSync(join(out, "results.tsv"), lines.join("\n") + "\n");
  console.log(lines.join("\n"));
  console.log(`batch: ${results.length}/${plan.clips.length} rendered, spent ≈ $${spentUsd(rate, started, Date.now()).toFixed(2)} of the $${cap} cap`);
}

/** One attempt at one prompt, then the guards (c) the same prompt failed three times in a row, (d) the engine stopped answering. */
function renderAttempt({ clip, png, out, state }) {
  const args = [join(here, "showcase-image-run.mjs"), "--plan", join(out, "plan.json"), "--out", out, "--only", clip.id, "--redo", ...(arg("model") ? ["--model", arg("model")] : []), ...(arg("size") ? ["--size", arg("size")] : [])];
  const run = spawnSync(process.execPath, args, { env: process.env, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
  if (run.stdout) process.stdout.write(run.stdout);
  if (run.stderr) process.stderr.write(run.stderr);
  const missing = !existsSync(png);
  state.failed = engineStreak(state.failed, missing && RUN_FAILED.test(run.stdout ?? ""), Date.now(), { minutes: Infinity });
  if (state.failed.abort) throw new Error(`${clip.id} failed ${state.failed.count} times in a row — read the run's error in the Studio; ending the pod`);
  state.streak = engineStreak(state.streak, missing && ENGINE_DOWN.test(run.stderr ?? ""), Date.now());
  if (state.streak.abort) throw new Error(`the engine stopped answering (${state.streak.count} × « could not be reached ») — ending the pod`);
}

if (import.meta.url === `file://${process.argv[1]}`) main().catch((e) => { console.error(`batch: ${e.message}`); process.exit(1); });
