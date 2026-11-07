/**
 * @file film-records.ts
 * @description The run facts under the home film « Montrer son travail », read from the records the render left
 * (`src/features/landing/utils/film.records.json`, written by scripts/film-records.mjs). Every figure is summed here from the
 * records; a record that is missing or malformed contributes nothing, so the page shows only what is verified.
 */

export interface FilmShot {
  id: string;
  runId: string;
  model: string;
  gpu: string;
  gpuSeconds: number;
  gpuCostUsd: number;
}

export interface FilmFacts {
  /** The models of the film by what each did, as the Studios report them. */
  models: readonly { role: "keyframes" | "shots" | "music"; name: string }[];
  /** The GPU cards the kept shots ran on. */
  gpus: readonly string[];
  /** Video runs kept in the film, one per shot. */
  runs: number;
  /** What those runs cost on the GPU, in US dollars (the sum of their records). */
  gpuCostUsd: number | null;
  /** The run ids, in cut order: the link to the evidence. */
  runIds: readonly string[];
  /** The run id of one shot by its record name, for the output that shows it. */
  runIdOf: Readonly<Record<string, string>>;
}

export const NO_FILM_FACTS: FilmFacts = { models: [], gpus: [], runs: 0, gpuCostUsd: null, runIds: [], runIdOf: {} };

const ROLES = ["keyframes", "shots", "music"] as const;
const isShot = (v: unknown): v is FilmShot => {
  const s = v as Partial<FilmShot> | null;
  return (
    typeof s?.runId === "string" &&
    typeof s.gpu === "string" &&
    typeof s.gpuCostUsd === "number" &&
    Number.isFinite(s.gpuCostUsd)
  );
};

export function filmFacts(raw: unknown): FilmFacts {
  const data = raw as { models?: unknown; shots?: unknown } | null;
  const shots = Array.isArray(data?.shots) ? data.shots.filter(isShot) : [];
  const models = (Array.isArray(data?.models) ? data.models : []).filter(
    (m): m is FilmFacts["models"][number] =>
      typeof m?.name === "string" && (ROLES as readonly string[]).includes(m?.role),
  );
  if (!shots.length) {
    return { ...NO_FILM_FACTS, models };
  }
  return {
    models,
    gpus: [...new Set(shots.map((s) => s.gpu))],
    runs: shots.length,
    gpuCostUsd: Math.round(shots.reduce((sum, s) => sum + s.gpuCostUsd, 0) * 100) / 100,
    runIds: shots.map((s) => s.runId),
    runIdOf: Object.fromEntries(shots.map((s) => [s.id, s.runId])),
  };
}
