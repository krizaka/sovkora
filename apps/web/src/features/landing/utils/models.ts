/**
 * @file models.ts
 * @description The models Sovkora supports, as the signed-in screens name them. Data is `models.generated.json`
 * (scripts/sync-models.mjs, from the registry Sovkora's routing reads): nothing about a model is typed here. This file
 * only types it, filters it and picks the few the home page shows.
 */
import data from "./models.generated.json";

export type Capability =
  | "chat"
  | "reasoning"
  | "vision"
  | "code"
  | "embeddings"
  | "image"
  | "video"
  | "speech-to-text"
  | "text-to-speech"
  | "music"
  | "video-to-audio";
export type RunsOn = "local" | "provider";
export type ModelStatus = "default" | "available" | "planned";

export interface ModelEntry {
  id: string;
  name: string;
  vendor: string;
  family: string;
  capabilities: Capability[];
  runsOn: RunsOn;
  runtime: string;
  openWeights: boolean;
  licence: { name: string; url: string | null };
  /** Can a firm that sells use it: yes · conditions (a community licence, share-alike, use restrictions) · no (non-commercial). */
  commercialUse: "yes" | "conditions" | "no";
  contextWindow: number | null;
  status: ModelStatus;
  lastVerified: string | null;
}

/** The registry, in its generated order: local first, defaults first. */
export const MODELS = data.models as ModelEntry[];

/** The capabilities that appear in the registry, in the order the filters show them. */
const CAPABILITY_ORDER: Capability[] = [
  "chat",
  "reasoning",
  "vision",
  "code",
  "embeddings",
  "image",
  "video",
  "speech-to-text",
  "text-to-speech",
  "music",
  "video-to-audio",
];
export const capabilitiesOf = (models: readonly ModelEntry[]): Capability[] =>
  CAPABILITY_ORDER.filter((c) => models.some((m) => m.capabilities.includes(c)));

export interface Filters {
  capabilities: readonly Capability[];
  runsOn: RunsOn | null;
}

export function filterModels(models: readonly ModelEntry[], { capabilities, runsOn }: Filters): ModelEntry[] {
  return models.filter(
    (m) =>
      (capabilities.length === 0 || capabilities.some((c) => m.capabilities.includes(c))) &&
      (runsOn === null || m.runsOn === runsOn),
  );
}

/** The address' filters: `?cap=chat,code&where=local`. Unknown values are ignored, never trusted. */
export function readFilters(params: URLSearchParams, models: readonly ModelEntry[] = MODELS): Filters {
  const known = capabilitiesOf(models);
  const caps = (params.get("cap") ?? "").split(",").filter((c): c is Capability => known.includes(c as Capability));
  const where = params.get("where");
  return { capabilities: [...new Set(caps)], runsOn: where === "local" || where === "provider" ? where : null };
}

export function filtersHref(filters: Filters): string {
  const q = new URLSearchParams();
  if (filters.capabilities.length) q.set("cap", filters.capabilities.join(","));
  if (filters.runsOn) q.set("where", filters.runsOn);
  const s = q.toString();
  return s ? `/models?${s}` : "/models";
}

/** How many tokens, grouped for reading: 32768 → "32,768" (the locale's own separator). */
export const formatCount = (n: number, locale: string) =>
  new Intl.NumberFormat(locale === "fr" ? "fr-CA" : "en-CA").format(n);

/** The licences that let anyone use the weights for anything (a score, never a claim about law). */
const PERMISSIVE = new Set(["Apache-2.0", "MIT"]);

/**
 * The few models the home page shows: for each capability in turn, the best local model — the default first, then a
 * permissive licence, then the plainest family (a base model before its fine-tunes) — up to `count`. Deterministic,
 * and read from the data: no model is named here.
 */
export function featured(models: readonly ModelEntry[] = MODELS, count = 6): ModelEntry[] {
  const score = (m: ModelEntry) => (m.status === "default" ? 2 : 0) + (PERMISSIVE.has(m.licence.name) ? 1 : 0);
  const best = (a: ModelEntry, b: ModelEntry) =>
    score(b) - score(a) ||
    a.family.length - b.family.length ||
    (b.contextWindow ?? 0) - (a.contextWindow ?? 0) ||
    a.id.localeCompare(b.id);
  const picked: ModelEntry[] = [];
  for (const cap of ["chat", "vision", "code", "image", "video", "speech-to-text", "text-to-speech"] as Capability[]) {
    const candidate = models
      .filter((m) => m.runsOn === "local" && m.status !== "planned" && m.capabilities[0] === cap && !picked.includes(m))
      .sort(best)[0];
    if (candidate) picked.push(candidate);
    if (picked.length === count) break;
  }
  return picked;
}

/** The models served by a provider: the home page says how many, never lists them as if they were the product. */
export const providerCount = (models: readonly ModelEntry[] = MODELS) =>
  models.filter((m) => m.runsOn === "provider").length;
