import { formatISO, parseISO, startOfDay, subDays } from "date-fns";
import type { JobRecord } from "@krizaka/orazaka-shared";

/** What a job made. The screen words it (`jobs.kind.image` → "Images" / "Images"): a key, never a sentence. */
export type JobKind = "image" | "video" | "voice" | "text";

/** The kind of work, from the capability key (`orazaka.core.media.image` → `image`). */
export function jobKind(featureKey: string): JobKind {
  const key = featureKey.toLowerCase();
  if (key.includes("video")) return "video";
  if (key.includes("image")) return "image";
  if (key.includes("speech") || key.includes("audio") || key.includes("sound")) return "voice";
  return "text";
}

const dayKey = (date: Date) => formatISO(startOfDay(date), { representation: "date" });

/** The jobs of each of the last `days` days by outcome, oldest first, empty days included. */
export function jobsPerDay(jobs: readonly JobRecord[], days: number, now: Date = new Date()) {
  const rows = Array.from({ length: days }, (_, i) => ({
    day: dayKey(subDays(now, days - 1 - i)),
    done: 0,
    failed: 0,
  }));
  const index = new Map(rows.map((row) => [row.day, row]));
  for (const job of jobs) {
    const row = index.get(dayKey(parseISO(job.createdAt)));
    if (!row) continue;
    if (job.status === "FAILED") row.failed += 1;
    else row.done += 1;
  }
  return rows;
}

/** How many jobs of each kind, largest first. */
export function jobsByKind(jobs: readonly JobRecord[]) {
  const counts = new Map<string, number>();
  for (const job of jobs) counts.set(jobKind(job.featureKey), (counts.get(jobKind(job.featureKey)) ?? 0) + 1);
  return [...counts.entries()].map(([kind, count]) => ({ kind, count })).sort((a, b) => b.count - a.count);
}
