#!/usr/bin/env node
/**
 * MANUAL TOOL (ADR-026), run by a person. Owner: sovkora lane. Listed in README.md « Manual tools ».
 * The core journey of Orazaka on the real dev backend (Dev preview `journey.core` + `journey.ai-via-orazaka`, orazaka#178):
 * a demo persona signs in, asks the chat, makes an image through the `image-generation` Studio, has it read by
 * `image-analysis`, and turns the idea into a short clip with `video-generation` — every step through the public edge
 * (https://dev.sovkora.com/edge, the API the web and the phone use), no SQL, no direct model call. Each step records the
 * edge request id and, for a Studio, the run id, so the engine's own trace (GET /studios/runs/{id}, the jobs of its steps)
 * can be followed from the report.
 *
 *   ORAZAKA_QA_EMAIL=… ORAZAKA_QA_PASSWORD=… node scripts/journey-dev.mjs --out <dir> [--only chat,image,...] [--video-model <key>]
 *
 * Writes `<out>/journey-<date>.json` (the report), `.md` (the table a PR quotes) and the clip `.mp4`. Credentials are read
 * from the environment (the persona's, via secret.sh), never printed; the report names the persona by first name only.
 * Cost: one hosted image (about 0.08 USD) and one hosted 5 s clip (about 0.60 USD).
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { edgeBase, edgeUrl, idSegment } from "./lib/edge-url.mjs";
import { chatText, edgeRequestId, isProven, readableReport, runFacts } from "./lib/journey.mjs";

const EDGE = edgeBase();
const BASE = EDGE.href;
const arg = (name, fallback) => {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : fallback;
};
const ONLY = arg("only")?.split(",");
const PERSONA = process.env.ORAZAKA_PERSONA_NAME ?? "Eric";
const ACTIVE = new Set(["PENDING", "RUNNING", "QUEUED", "AWAITING_APPROVAL"]);
const say = (line) => process.stdout.write(`${line}\n`);

let token;
async function call(method, path, body, { raw, stream, form } = {}) {
  const res = await fetch(edgeUrl(EDGE, path), {
    signal: AbortSignal.timeout(stream ? 240_000 : 90_000),
    method,
    headers: {
      Accept: stream ? "text/event-stream" : "application/json",
      ...(body === undefined || form ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
  });
  if (!res.ok) throw Object.assign(new Error(`${method} ${path} → ${res.status}`), { requestId: edgeRequestId(res.headers) });
  const requestId = edgeRequestId(res.headers);
  if (raw) return { res, requestId };
  return { data: stream ? await res.text() : await res.json(), requestId };
}

/** A Studio run, followed to its end; the run is the engine's trace (its steps carry the job ids). */
async function studio(key, inputs, { timeoutMs = 900_000 } = {}) {
  const started = await call("POST", `/api/v1/studios/${idSegment(key)}/runs`, { inputs });
  const startedAt = Date.now();
  let run = started.data;
  while (ACTIVE.has(run.status) && Date.now() - startedAt < timeoutMs) {
    await new Promise((r) => setTimeout(r, 5000));
    run = (await call("GET", `/api/v1/studios/runs/${idSegment(started.data.id)}`).catch(() => ({ data: run }))).data;
  }
  return { run, requestId: started.requestId };
}

/** The run's output of a type (IMAGE, VIDEO): its path to fetch, and the asset id inside it. */
function outputOf(run, type) {
  const o = (run.outputs ?? []).find((x) => x?.type === type && typeof x.value === "string");
  return o ? { path: o.value, assetId: /\/assets\/([0-9a-f-]{36})/.exec(o.value)?.[1] ?? null } : null;
}

const steps = [];
const state = {};
async function step(name, fn) {
  if (ONLY && name !== "sign-in" && !ONLY.includes(name)) return;
  const t0 = Date.now();
  try {
    const { requestId, note, ...rest } = await fn();
    steps.push({ name, ok: true, ms: Date.now() - t0, requestId, note, ...rest });
    say(`ok   ${name.padEnd(9)} ${Date.now() - t0} ms  ${requestId ?? "—"}  ${note ?? ""}`);
  } catch (e) {
    steps.push({ name, ok: false, ms: Date.now() - t0, requestId: e.requestId ?? null, note: e.message });
    say(`FAIL ${name.padEnd(9)} ${e.message}`);
  }
}

async function main() {
  const { ORAZAKA_QA_EMAIL: email, ORAZAKA_QA_PASSWORD: password } = process.env;
  if (!email || !password) throw new Error("ORAZAKA_QA_EMAIL / ORAZAKA_QA_PASSWORD are not set (read them with secret.sh)");
  const out = resolve(arg("out", "."));
  mkdirSync(out, { recursive: true });

  await step("sign-in", async () => {
    const r = await call("POST", "/api/v1/auth/login", { email, password });
    token = r.data.token;
    if (!token) throw new Error("no token");
    return { requestId: r.requestId, note: `${PERSONA} is signed in` };
  });
  if (!token) return finish(out);

  await step("chat", async () => {
    const session = await call("POST", "/api/v1/chat/sessions", {});
    const r = await call("POST", `/api/v1/chat/stream/${idSegment(session.data.id)}`, { prompt: "En une phrase : pourquoi garder ses données chez soi quand on utilise l'IA ?" }, { stream: true });
    const text = chatText(r.data).trim();
    if (text.length < 20) throw new Error(`the chat answered ${text.length} characters`);
    return { requestId: r.requestId, note: `answer: « ${text.slice(0, 140)} »`, sessionId: session.data.id };
  });

  await step("image", async () => {
    const { run, requestId } = await studio("image-generation", {
      prompt: "A pink flamingo reading a map on a snowy Montréal rooftop at sunrise, soft light, photographic",
      size: "1024x1024", // in the Studio's enum AND in the hosted engine's (its portrait sizes differ)
      model: arg("image-model", "stable-diffusion-3.5-large"),
    });
    if (run.status !== "SUCCEEDED") throw Object.assign(new Error(`${run.studioKey} run ${run.id} ${run.status}: ${run.errorMessage ?? ""}`), { requestId });
    state.image = outputOf(run, "IMAGE");
    if (!state.image?.assetId) throw Object.assign(new Error(`run ${run.id} has no image asset`), { requestId });
    return { requestId, note: `image-generation run ${run.id}`, ...runFacts(run) };
  });

  await step("analysis", async () => {
    if (!state.image?.assetId) throw new Error("no image to analyse (run the image step)");
    const { run, requestId } = await studio("image-analysis", { assetId: state.image.assetId, prompt: "Describe this image in one sentence." });
    if (run.status !== "SUCCEEDED") throw Object.assign(new Error(`image-analysis run ${run.id} ${run.status}: ${run.errorMessage ?? ""}`), { requestId });
    const said = (run.outputs ?? []).find((x) => x?.type === "TEXT" || x?.type === "MARKDOWN")?.value ?? JSON.stringify(run.outputs ?? []);
    return { requestId, note: `image-analysis run ${run.id}: « ${String(said).slice(0, 140)} »`, ...runFacts(run) };
  });

  await step("video", async () => {
    // The hosted text-to-video model (16:9): the 9:16 models run on the GPU host, which is up only for a batch.
    const inputs = { prompt: "A pink flamingo walks across a snowy Montréal rooftop at sunrise, the camera slowly rising", aspect: arg("aspect", "16:9"), durationSeconds: 5, model: arg("video-model", "wan2-2-t2v-a14b") };
    const { run, requestId } = await studio("video-generation", inputs);
    if (run.status !== "SUCCEEDED") throw Object.assign(new Error(`video-generation run ${run.id} ${run.status}: ${run.errorMessage ?? ""}`), { requestId });
    const clip = outputOf(run, "VIDEO");
    if (!clip) throw Object.assign(new Error(`run ${run.id} has no video output`), { requestId });
    const { res } = await call("GET", clip.path, undefined, { raw: true });
    const data = Buffer.from(await res.arrayBuffer());
    if (data.length < 50_000) throw new Error(`${data.length} bytes is not a clip`);
    const file = join(out, `journey-${new Date().toISOString().slice(0, 10)}.mp4`);
    writeFileSync(file, data);
    return { requestId, note: `video-generation run ${run.id}, ${inputs.aspect} ${inputs.model}, ${(data.length / 1e6).toFixed(1)} MB`, clip: file, ...runFacts(run) };
  });

  finish(out);
}

function finish(out) {
  const report = { persona: PERSONA, base: BASE, date: new Date().toISOString(), proven: isProven({ steps }), steps };
  const stem = join(out, `journey-${report.date.slice(0, 10)}`);
  writeFileSync(`${stem}.json`, JSON.stringify(report, null, 2) + "\n");
  writeFileSync(`${stem}.md`, readableReport(report) + "\n");
  say(`\n${report.proven ? "PROVEN" : "NOT PROVEN"} — ${steps.filter((s) => s.ok).length}/${steps.length} steps · ${stem}.json`);
  process.exit(report.proven ? 0 : 1);
}

main().catch((e) => {
  process.stderr.write(`journey: ${e.message}\n`);
  process.exit(1);
});
