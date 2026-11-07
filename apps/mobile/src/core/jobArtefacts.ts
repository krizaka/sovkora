import type { ArtefactType, JobRecord, RunArtefact } from "@krizaka/orazaka-shared";
import { jobKind } from "./jobStats";

const BY_FORMAT: Record<string, ArtefactType> = {
  png: "IMAGE",
  jpg: "IMAGE",
  jpeg: "IMAGE",
  webp: "IMAGE",
  gif: "IMAGE",
  mp4: "VIDEO",
  webm: "VIDEO",
  mov: "VIDEO",
  mp3: "AUDIO",
  wav: "AUDIO",
  m4a: "AUDIO",
  ogg: "AUDIO",
};
const BY_KIND: Record<ReturnType<typeof jobKind>, ArtefactType> = {
  image: "IMAGE",
  video: "VIDEO",
  voice: "AUDIO",
  text: "TEXT",
};
/** The facts a result carries beside its media, in the order the card reads them (ArtefactCard words these keys). */
const FACTS = ["model", "seed", "generationMs", "durationMs"] as const;

/**
 * What a finished job made, as the artefacts the run screen already shows (ArtefactCard): its media (the `url` the job
 * service published, typed by its `format`, else by the job's kind), its text (`content`, `text`), then its facts. A job
 * that made nothing readable gives an empty list — never a guess.
 */
export function jobArtefacts(job: JobRecord): RunArtefact[] {
  const result = job.result;
  const out: RunArtefact[] = [];
  const url = typeof result.url === "string" ? result.url : null;
  if (url) {
    const format =
      typeof result.format === "string"
        ? result.format.toLowerCase()
        : (/\.([a-z0-9]+)(?:\?|$)/i.exec(url)?.[1] ?? "").toLowerCase();
    out.push({
      key: "result",
      label: "result",
      type: BY_FORMAT[format] ?? BY_KIND[jobKind(job.featureKey)],
      value: url,
    });
  }
  const text = [result.content, result.text].find((v): v is string => typeof v === "string") ?? null;
  if (text?.trim()) out.push({ key: "content", label: "content", type: "TEXT", value: text });
  for (const fact of FACTS) {
    const value = result[fact];
    if (typeof value === "string" || typeof value === "number")
      out.push({ key: fact, label: fact, type: "TEXT", value: String(value) });
  }
  return out;
}
