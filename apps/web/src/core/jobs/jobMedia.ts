/**
 * @file jobMedia.ts
 * @description What a job produced, read once for every surface that shows it (the jobs gallery, the notification
 * bell): its kind, its media URL, the prompt it was given, its name in the reader's language.
 */

import { MODEL_CATEGORY } from "@/core/constants/capability.constants";
import type { TranslationDictionary } from "@/core/context/translations.types";
import type { Job } from "@/core/types/jobs.types";

export type JobKind = "image" | "video" | "audio" | "text";

/** The kind of work, from the capability key (`orazaka.core.media.image`, `…speech…`). */
export function jobKind(job: Pick<Job, "featureKey">): JobKind {
  const key = job.featureKey.toLowerCase();
  if (key.includes(MODEL_CATEGORY.VIDEO)) return "video";
  if (key.includes(MODEL_CATEGORY.IMAGE)) return "image";
  if (key.includes(MODEL_CATEGORY.SPEECH) || key.includes("audio") || key.includes("tts")) return "audio";
  return "text";
}

/** The URL of the media a completed job published (`result.url`), or null for a text result or an unfinished job. */
export function jobMediaUrl(job: Pick<Job, "result" | "status">): string | null {
  if (job.status !== "COMPLETED") return null;
  const url = job.result?.url;
  return typeof url === "string" && url !== "" ? url : null;
}

/** The prompt the job was given, when it carries one. */
export function jobPrompt(job: Pick<Job, "payload">): string | null {
  const prompt = job.payload?.prompt ?? job.payload?.text ?? job.payload?.input;
  return typeof prompt === "string" && prompt.trim() !== "" ? prompt.trim() : null;
}

/** The job's name in the reader's language: "Image generation", "Video generation"… */
export function jobLabel(t: TranslationDictionary, job: Pick<Job, "featureKey">): string {
  const kind = jobKind(job);
  if (kind === "video")
    return job.featureKey.includes("analysis") ? t.notifications.videoAnalysis : t.notifications.videoGen;
  if (kind === "image") return t.notifications.imageGen;
  if (kind === "audio") return t.notifications.speechGen;
  return t.notifications.textGen;
}

/** The short alt of a job's result picture: "Result of <studio>" (the Studio it came from, else the kind). Never the prompt; at most 120 characters. */
export function jobResultAlt(t: TranslationDictionary, job: Pick<Job, "featureKey" | "payload">): string {
  const studio = job.payload?.studio ?? job.payload?.studioLabel;
  const name = typeof studio === "string" && studio.trim() !== "" ? studio.trim() : jobLabel(t, job);
  return t.showcase.jobs.resultOf.replace("{studio}", name).slice(0, 120);
}
