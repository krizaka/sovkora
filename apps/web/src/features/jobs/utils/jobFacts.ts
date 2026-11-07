/**
 * @file jobFacts.ts
 * @description What a job says about itself, read once for the Jobs table and its row detail: the model (always a
 * name), where it ran, the real wall time (queue + run, not the API call's milliseconds), and the facts the worker
 * reports, grouped (Output · Generation · Hardware · Timing) with human values — "167.7 s", "29.1 GB" — never the raw
 * keys (`gpu_allocated_mb`). Labels are ids here; the words come from `jobsTable` (en, fr), and a test holds that every
 * key the worker emits has one.
 */

import { JOB_STATUS } from "@/core/constants/http.constants";
import type { FactId } from "@/core/types/jobFacts.types";
import type { Job } from "@/core/types/jobs.types";

export type { FactId };

export type FactSection = "output" | "generation" | "hardware" | "timing" | "other";

export const FACT_SECTION: Record<FactId, FactSection> = {
  format: "output",
  resolution: "output",
  aspect: "output",
  frames: "output",
  fps: "output",
  clipLength: "output",
  images: "output",
  voice: "output",
  keyframes: "output",
  model: "generation",
  provider: "generation",
  seed: "generation",
  steps: "generation",
  gpu: "hardware",
  vram: "hardware",
  peakRam: "hardware",
  inference: "timing",
  gpuTime: "timing",
  apiCall: "timing",
  queued: "timing",
  total: "timing",
  credits: "generation",
};

/** Normalised worker/payload key (lower case, letters and digits only) → the fact it is. */
const KEY_TO_FACT: Record<string, FactId | "width" | "height"> = {
  format: "format",
  outputformat: "format",
  width: "width",
  height: "height",
  aspect: "aspect",
  aspectratio: "aspect",
  frames: "frames",
  numframes: "frames",
  framecount: "frames",
  fps: "fps",
  framerate: "fps",
  images: "images",
  imagecount: "images",
  numimages: "images",
  voice: "voice",
  keyframes: "keyframes",
  keyframecount: "keyframes",
  model: "model",
  modelname: "model",
  modelid: "model",
  provider: "provider",
  inference: "provider",
  seed: "seed",
  steps: "steps",
  numinferencesteps: "steps",
  inferencesteps: "steps",
  gpu: "gpu",
  gpuname: "gpu",
  gpuallocatedmb: "vram",
  vrammb: "vram",
  peakmemoryrssmb: "peakRam",
  peakrssmb: "peakRam",
  inferencetimesec: "inference",
  inferencetimes: "inference",
  gpuseconds: "gpuTime",
  gpusec: "gpuTime",
  gputimesec: "gpuTime",
  durationms: "apiCall",
  duration: "apiCall",
  durationsec: "apiCall",
  credits: "credits",
  creditscharged: "credits",
  cost: "credits",
};

/** Every key a worker is known to emit in a job's payload, metadata or metrics — each must have a label (tested). */
export const WORKER_KEYS = [
  "model",
  "provider",
  "inference",
  "format",
  "fps",
  "gpu",
  "seed",
  "steps",
  "width",
  "height",
  "aspect",
  "frames",
  "images",
  "gpu_seconds",
  "duration",
  "durationMs",
  "gpu_allocated_mb",
  "inference_time_sec",
  "peak_memory_rss_mb",
  "keyframeCount",
  "voice",
] as const;

/** `gpu_allocated_mb` → `gpuallocatedmb`. */
export const normaliseKey = (key: string) => key.toLowerCase().replace(/[^a-z0-9]/g, "");

/** The fact a key is, or null for a key with no label of its own. */
export function factOf(key: string): FactId | "width" | "height" | null {
  return KEY_TO_FACT[normaliseKey(key)] ?? null;
}

export interface JobFact {
  /** A labelled fact's id, or the humanised key of one the dictionary has no word for. */
  id: string;
  section: FactSection;
  /** Set for a fact without a label of its own: its key in words ("Peak queue"), never the raw key. */
  label?: string;
  value: string;
}

type Scalar = string | number | boolean;
const isScalar = (v: unknown): v is Scalar => typeof v === "string" || typeof v === "number" || typeof v === "boolean";

const asRecord = (v: unknown): Record<string, unknown> =>
  typeof v === "object" && v !== null && !Array.isArray(v) ? (v as Record<string, unknown>) : {};

/** Everything the job reports, flat, by normalised key — the worker's measures over the result's own fields over the request. */
function merged(job: Pick<Job, "payload" | "result">): Map<string, { key: string; value: Scalar }> {
  const out = new Map<string, { key: string; value: Scalar }>();
  const result = asRecord(job.result);
  for (const source of [asRecord(job.payload), result, asRecord(result.metadata), asRecord(result.metrics)]) {
    for (const [key, value] of Object.entries(source)) if (isScalar(value)) out.set(normaliseKey(key), { key, value });
  }
  return out;
}

const num = (v: Scalar | undefined): number | null => {
  const n = typeof v === "number" ? v : typeof v === "string" && v.trim() !== "" ? Number(v) : NaN;
  return Number.isFinite(n) ? n : null;
};

/** "850 ms" · "12.4 s" · "2 min 47 s" · "1 h 02 min". */
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${Math.max(0, Math.round(ms))} ms`;
  const s = ms / 1000;
  if (s < 100) return `${s.toFixed(1)} s`;
  const total = Math.round(s);
  if (total < 3600) return `${Math.floor(total / 60)} min ${String(total % 60).padStart(2, "0")} s`;
  return `${Math.floor(total / 3600)} h ${String(Math.floor((total % 3600) / 60)).padStart(2, "0")} min`;
}

/** A measured time in seconds, one decimal under ten minutes ("167.7 s"), as the worker reports it. */
const seconds = (s: number) => (s < 600 ? `${s.toFixed(1)} s` : formatDuration(s * 1000));
const memory = (mb: number) => (mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${Math.round(mb)} MB`);

/** The wall time of a job, queue and run: creation to last update. While it runs it is `now` (the caller's clock) when given, else unknown. */
export function wallTimeMs(job: Pick<Job, "status" | "createdAt" | "updatedAt">, now?: number): number | null {
  const start = Date.parse(job.createdAt);
  if (Number.isNaN(start)) return null;
  const running = job.status === JOB_STATUS.PENDING || job.status === JOB_STATUS.PROCESSING;
  const end = running ? (now ?? Number.NaN) : Date.parse(job.updatedAt);
  return Number.isNaN(end) ? null : Math.max(0, end - start);
}

const humanise = (key: string) => {
  const words = key
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/[_\-.]+/g, " ")
    .trim()
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
};

/** Keys that are the request or the result itself, not facts about the run. */
const NOT_FACTS = new Set([
  "prompt",
  "text",
  "input",
  "url",
  "assetid",
  "jobid",
  "negativeprompt",
  "studio",
  "studiokey",
  "success",
  "type",
]);

/** The facts of a job, in reading order, each in its section. */
export interface JobFactsOptions {
  now?: number;
  /** The registry's display names, by normalised model id or name (see `modelOf`). */
  modelNames?: ReadonlyMap<string, string>;
}

type Merged = ReturnType<typeof merged>;
type Pair = [FactId, string | null];

const asText = (value: Scalar | undefined): string | null => (value === undefined ? null : String(value));
const asNumberText = (n: number | null): string | null => (n === null ? null : String(n));

/** The facts about the media: format, resolution, aspect, frames, fps, clip length, images, voice, keyframes. */
function mediaFacts(get: (id: string) => Scalar | undefined): Pair[] {
  const format = get("format");
  const w = num(get("width"));
  const h = num(get("height"));
  const frames = num(get("frames"));
  const fps = num(get("fps"));
  return [
    ["format", typeof format === "string" ? format.toUpperCase() : null],
    ["resolution", w !== null && h !== null ? `${w}×${h}` : null],
    ["aspect", asText(get("aspect"))],
    ["frames", asNumberText(frames)],
    ["fps", fps === null ? null : `${fps} fps`],
    ["clipLength", frames !== null && fps ? `${(frames / fps).toFixed(1)} s` : null],
    ["images", asNumberText(num(get("images")))],
    ["voice", asText(get("voice"))],
    ["keyframes", asNumberText(num(get("keyframes")))],
  ];
}

/** The facts about who made it: model, provider, seed, steps, credits. */
function makerFacts(get: (id: string) => Scalar | undefined, model: string | null): Pair[] {
  const provider = get("provider");
  return [
    ["model", model],
    ["provider", provider === undefined ? null : providerName(String(provider))],
    ["seed", asText(get("seed"))],
    ["steps", asNumberText(num(get("steps")))],
    ["credits", asNumberText(num(get("credits")))],
  ];
}

/** The facts about the machine: gpu, vram, peak ram. */
function machineFacts(get: (id: string) => Scalar | undefined): Pair[] {
  const vram = num(get("vram"));
  const peak = num(get("peakRam"));
  return [
    ["gpu", asText(get("gpu"))],
    ["vram", vram === null ? null : memory(vram)],
    ["peakRam", peak === null ? null : memory(peak)],
  ];
}

/** The facts about time: queued, inference, gpu time, api call, total. */
function timingFacts(
  job: Pick<Job, "status" | "createdAt" | "updatedAt">,
  m: Merged,
  get: (id: string) => Scalar | undefined,
  now: number | undefined,
): Pair[] {
  const inference = num(get("inference"));
  const total = wallTimeMs(job, now);
  const finished = job.status === JOB_STATUS.COMPLETED || job.status === JOB_STATUS.FAILED;
  const waiting = finished && inference !== null && total !== null ? total - inference * 1000 : null;
  const gpuTime = num(get("gpuTime"));
  const apiMs = num(get("apiCall"));
  // `duration` is milliseconds in a result (`durationMs`) and seconds when the key says so.
  const apiKey = [...m].find(([k]) => KEY_TO_FACT[k] === "apiCall")?.[0] ?? "";
  return [
    ["queued", waiting !== null && waiting >= 1000 ? formatDuration(waiting) : null],
    ["inference", inference === null ? null : seconds(inference)],
    ["gpuTime", gpuTime === null ? null : seconds(gpuTime)],
    ["apiCall", apiMs === null ? null : formatDuration(apiKey.endsWith("sec") ? apiMs * 1000 : apiMs)],
    ["total", total === null ? null : formatDuration(total)],
  ];
}

/** What the dictionary has no word for: shown with its key in words, never raw. */
function otherFacts(m: Merged): JobFact[] {
  const facts: JobFact[] = [];
  for (const [k, { key, value }] of m) {
    if (KEY_TO_FACT[k] || NOT_FACTS.has(k)) continue;
    if (typeof value === "string" && value.length > 80) continue;
    facts.push({ id: key, section: "other", label: humanise(key), value: String(value) });
  }
  return facts;
}

export function jobFacts(
  job: Pick<Job, "payload" | "result" | "status" | "createdAt" | "updatedAt">,
  { now, modelNames }: JobFactsOptions = {},
): JobFact[] {
  const m = merged(job);
  const get = (id: string) => {
    for (const [k, v] of m) {
      if (KEY_TO_FACT[k] === id) {
        return v.value;
      }
    }
    return undefined;
  };
  const pairs: Pair[] = [
    ...mediaFacts(get),
    ...makerFacts(get, modelOf(job, modelNames)),
    ...machineFacts(get),
    ...timingFacts(job, m, get, now),
  ];
  const facts: JobFact[] = [];
  for (const [id, value] of pairs) {
    if (value !== null && value !== "") {
      facts.push({ id, section: FACT_SECTION[id], value });
    }
  }
  return [...facts, ...otherFacts(m)];
}

const PROVIDERS: Record<string, string> = {
  runpod: "RunPod",
  digitalocean: "DigitalOcean",
  ollama: "Ollama",
  openai: "OpenAI",
  anthropic: "Anthropic",
  mistral: "Mistral",
  comfyui: "ComfyUI",
  mlx: "Mac · Metal",
  metal: "Mac · Metal",
  mps: "Mac · Metal",
  local: "Local",
};

/** "runpod" → "RunPod"; an unknown provider keeps its own spelling. */
export const providerName = (raw: string) => PROVIDERS[normaliseKey(raw)] ?? raw;

/** "NVIDIA H100 80GB HBM3" → "H100"; Apple silicon → "Mac"; anything else as reported. */
export function gpuName(raw: string): string {
  if (/apple|\bm[1-9]\b|metal|mps/i.test(raw)) return "Mac";
  const known = raw.match(/\b(B200|H200|H100|A100|L40S|L40|A10G|A10|A40|V100|T4|RTX ?\d{4}[A-Za-z]*)\b/i);
  return known
    ? known[1].toUpperCase().replace(/^RTX(\d)/, "RTX $1")
    : raw.replace(/^nvidia\s+(geforce\s+)?/i, "").trim();
}

/** Where a job ran: "H100 · RunPod", "Mac · Metal", "DigitalOcean". Null when the job does not say. */
export function whereRan(job: Pick<Job, "payload" | "result">): string | null {
  const m = merged(job);
  const find = (id: string) => [...m].find(([k]) => KEY_TO_FACT[k] === id)?.[1].value;
  const gpu = find("gpu");
  const provider = find("provider");
  const providerLabel = typeof provider === "string" && provider !== "" ? providerName(provider) : null;
  if (typeof gpu === "string" && gpu !== "") {
    const g = gpuName(gpu);
    if (g === "Mac") return "Mac · Metal";
    return providerLabel && providerLabel !== "Mac · Metal" ? `${g} · ${providerLabel}` : g;
  }
  return providerLabel;
}

/** The model of a job as a name ("Wan 2.2 TI2V 5B"): the registry's when `names` knows it, else what the job says. Null when it says nothing. */
export function modelOf(job: Pick<Job, "payload" | "result">, names?: ReadonlyMap<string, string>): string | null {
  const m = merged(job);
  const raw = [...m].find(([k]) => KEY_TO_FACT[k] === "model")?.[1].value;
  if (raw === undefined || String(raw).trim() === "") return null;
  return names?.get(normaliseKey(String(raw))) ?? String(raw);
}

/** The prompt a job was given, for its detail and its search. */
export function promptOf(job: Pick<Job, "payload">): string | null {
  const p = job.payload?.prompt ?? job.payload?.text ?? job.payload?.input;
  return typeof p === "string" && p.trim() !== "" ? p.trim() : null;
}

/** The Studio a run came from, when the request names it. */
export function studioOf(job: Pick<Job, "payload">): string | null {
  const s = job.payload?.studio ?? job.payload?.studioKey ?? job.payload?.studioLabel;
  return typeof s === "string" && s !== "" ? s : null;
}
