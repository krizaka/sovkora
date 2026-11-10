#!/usr/bin/env node
/**
 * MANUAL TOOL (ADR-026), run by a person before a recording. Owner: sovkora lane. Listed in README.md « Manual tools ».
 * Puts a little real work on the shelf of a dev persona, so the jobs list of a recording is not empty.
 *
 *   node scripts/dev-persona-runs.mjs [mira|eric]
 *
 * Signs in as the persona (credentials read from @dev through secret.sh, never printed), sets her preferences and starts
 * a few Studio runs through the same endpoint the run form uses, one after the other. Replayable: a Studio she already
 * ran with success is not run again. The content is fictional (a Montréal ceramic lamp studio).
 */
import { execFileSync } from "node:child_process";
import { homedir } from "node:os";
import { join } from "node:path";

const EDGE = process.env.ORAZAKA_EDGE_URL ?? "https://dev.sovkora.com/edge";
export const PERSONA_RUNS = [
  { studioKey: "image-generation", inputs: { prompt: "Studio product photo of a hand-glazed ceramic table lamp, warm amber glow, dark slate plinth, deep ink-blue background, soft rim light", model: "stable-diffusion-3.5-large" } },
  { studioKey: "speech-synthesis", inputs: { text: "Meet Ember, our new table lamp. Shaped by hand in Montréal, glazed in amber, and lit to make an evening feel like home." } },
  { studioKey: "image-generation", inputs: { prompt: "Overhead photo of three hand-glazed ceramic cups in sage, amber and slate, on a walnut table, soft morning light", model: "stable-diffusion-3.5-large" } },
];

export const secretKeys = (name) => {
  const k = { mira: "MIRA", eric: "PERSONA" }[name];
  if (!k) throw new Error(`unknown persona: ${name}`);
  return [`APP_QA_ORAZAKA_${k}_EMAIL`, `APP_QA_ORAZAKA_${k}_PASSWORD`];
};
const secret = (key) => execFileSync(join(homedir(), ".krizaka-worktrees/tools/secret.sh"), ["@dev", key], { encoding: "utf8" }).trim();

async function call(method, path, token, body) {
  const res = await fetch(`${EDGE}${path}`, {
    method,
    headers: { ...(body ? { "Content-Type": "application/json" } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path}: HTTP ${res.status} ${text.slice(0, 200)}`);
  return text ? JSON.parse(text) : null;
}

async function main(name = "mira") {
  const [ek, pk] = secretKeys(name);
  const { token } = await call("POST", "/api/v1/auth/login", undefined, { email: secret(ek), password: secret(pk) });
  await call("PUT", "/api/v1/profile/preferences", token, { language: "en", "image-aspect-ratio": "4:3" }).catch(() => {});
  const history = await call("GET", "/api/v1/studios/runs?limit=200", token);
  const done = new Set(history.filter((r) => r.status === "SUCCEEDED").map((r) => JSON.stringify(r.inputs?.prompt ?? r.studioKey)));
  for (const run of PERSONA_RUNS) {
    if (done.has(JSON.stringify(run.inputs.prompt ?? run.studioKey))) {
      console.log(`already done: ${run.studioKey}`);
      continue;
    }
    const started = await call("POST", `/api/v1/studios/${run.studioKey}/runs`, token, { inputs: run.inputs });
    const id = started.id ?? started.runId;
    for (let i = 0; i < 60; i++) {
      await new Promise((r) => setTimeout(r, 5000));
      const r = await call("GET", `/api/v1/studios/runs/${id}`, token);
      if (["SUCCEEDED", "FAILED", "CANCELLED"].includes(r.status)) {
        console.log(`${run.studioKey}: ${r.status}`);
        break;
      }
    }
  }
}
if (import.meta.url === `file://${process.argv[1]}`) main(process.argv[2]).catch((e) => { console.error(e.message); process.exit(1); });
