#!/usr/bin/env node
/**
 * Writes `src/features/landing/utils/film.records.json` — the run records of the home film « Montrer son travail », read from the
 * per-shot records the render left (one JSON per kept shot: run id, model, GPU, GPU seconds, GPU cost).
 *
 *   node scripts/film-records.mjs <dir of the shot records> [--write]
 *
 * The page reads this file at build and shows only what it holds: the figures are summed there, never typed. The shots
 * kept in the film are listed below by the name of their record; a record that is not SUCCEEDED stops the script.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

/** The seven takes of the film, in cut order (krizaka-internal#138). */
export const KEPT = ["s1-03-lock", "s2-01-a14b", "s3-01-a14b", "s4-01-a14b", "s5-01-a14br", "s6-02-a14b", "s7-01-a14b"];

/** The models of the film, by what each did (names as the Studios and the worker report them). */
export const MODELS = [
  { role: "keyframes", name: "FLUX.2 [klein] 4B" },
  { role: "shots", name: "Wan 2.2 I2V A14B" },
  { role: "music", name: "ACE-Step 1.5 XL turbo" },
];

/** The one status of a run that left an output. */
const FINISHED = new Set(["SUCCEEDED"]);

export function shotOf(record) {
  if (!FINISHED.has(record.status)) {
    throw new Error(`${record.id}: run ${record.runId} is ${record.status}`);
  }
  return {
    id: record.id,
    runId: record.runId,
    model: record.reported.model,
    gpu: record.reported.gpu,
    gpuSeconds: record.reported.gpuSeconds,
    gpuCostUsd: record.gpuCostUsd,
  };
}

export function filmRecords(dir) {
  const shots = KEPT.map((id) => shotOf(JSON.parse(fs.readFileSync(path.join(dir, `${id}.json`), "utf8"))));
  return { film: "montrer-son-travail", models: MODELS, shots };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const dir = process.argv[2];
  if (!dir) {
    throw new Error("usage: node scripts/film-records.mjs <dir of the shot records> [--write]");
  }
  const out = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)),
    "../src/features/landing/utils/film.records.json",
  );
  const text = `${JSON.stringify(filmRecords(dir), null, 2)}\n`;
  if (process.argv.includes("--write")) {
    fs.writeFileSync(out, text);
  } else {
    process.stdout.write(text);
  }
}
