/**
 * @file jobInsights.ts
 * @description What Eric's own work says about itself — read from the jobs and Studio runs the platform already
 * returns to him, never invented. Pure functions (dates through date-fns), so the dashboard, the credits page and the
 * job queue draw the same figures.
 */

import { differenceInMilliseconds, formatISO, parseISO, startOfDay, subDays } from "date-fns";
import type { Run } from "@krizaka/orazaka-shared";
import { jobKind, type JobKind } from "@/core/jobs/jobMedia";
import type { Job } from "@/core/types/jobs.types";

/** One day of work: how many jobs finished, failed or are still on their way. */
export interface DayActivity {
  /** ISO date (`2026-10-11`): the charts write it in the reader's language. */
  day: string;
  completed: number;
  failed: number;
  running: number;
}

const dayKey = (date: Date) => formatISO(startOfDay(date), { representation: "date" });

/** The last `days` days (today included), oldest first, each with its jobs by outcome — empty days included. */
export function activityByDay(jobs: readonly Job[], days: number, now: Date = new Date()): DayActivity[] {
  const rows = Array.from({ length: days }, (_, i) => ({
    day: dayKey(subDays(now, days - 1 - i)),
    completed: 0,
    failed: 0,
    running: 0,
  }));
  const byDay = new Map(rows.map((row) => [row.day, row]));
  for (const job of jobs) {
    const row = byDay.get(dayKey(parseISO(job.createdAt)));
    if (!row) continue;
    if (job.status === "COMPLETED") row.completed += 1;
    else if (job.status === "FAILED") row.failed += 1;
    else row.running += 1;
  }
  return rows;
}

/** How many jobs of each kind (image, video, audio, text), largest first, kinds with none left out. */
export function jobsByKind(jobs: readonly Job[]): { kind: JobKind; count: number }[] {
  const counts = new Map<JobKind, number>();
  for (const job of jobs) counts.set(jobKind(job), (counts.get(jobKind(job)) ?? 0) + 1);
  return [...counts.entries()].map(([kind, count]) => ({ kind, count })).sort((a, b) => b.count - a.count);
}

/** Milliseconds a finished job took, from its creation to its last update; null while it runs or when it failed. */
export function jobDuration(job: Job): number | null {
  if (job.status !== "COMPLETED") return null;
  const ms = differenceInMilliseconds(parseISO(job.updatedAt), parseISO(job.createdAt));
  return ms >= 0 ? ms : null;
}

const median = (values: readonly number[]): number | null => {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
};

/** The median time to a result, in seconds, per kind of work — what "how long will it take" rests on. */
export function timeToResultByKind(jobs: readonly Job[]): { kind: JobKind; seconds: number; samples: number }[] {
  const durations = new Map<JobKind, number[]>();
  for (const job of jobs) {
    const ms = jobDuration(job);
    if (ms === null) continue;
    const list = durations.get(jobKind(job)) ?? [];
    list.push(ms);
    durations.set(jobKind(job), list);
  }
  return [...durations.entries()]
    .map(([kind, list]) => ({ kind, seconds: Math.round((median(list) ?? 0) / 100) / 10, samples: list.length }))
    .sort((a, b) => b.seconds - a.seconds);
}

/**
 * Milliseconds left for a running job, from the median duration of the same work on this machine and the time it has
 * already run (or its reported progress); null when nothing comparable has finished yet — an ETA we cannot back is
 * not shown.
 */
export function remainingFor(job: Job, history: readonly Job[], now: Date = new Date()): number | null {
  const typical = median(
    history
      .filter((other) => other.featureKey === job.featureKey)
      .map(jobDuration)
      .filter((ms): ms is number => ms !== null),
  );
  if (typical === null) return null;
  const elapsed = Math.max(0, differenceInMilliseconds(now, parseISO(job.createdAt)));
  const progress =
    typeof job.progress === "number" && job.progress > 0 && job.progress < 100 ? job.progress / 100 : null;
  const left = progress !== null ? (elapsed / progress) * (1 - progress) : typical - elapsed;
  return Math.max(1000, Math.round(left));
}

/** How many runs each Studio had, most used first. */
export function runsByStudio(runs: readonly Run[]): { studioKey: string; runs: number; failed: number }[] {
  const counts = new Map<string, { runs: number; failed: number }>();
  for (const run of runs) {
    const entry = counts.get(run.studioKey) ?? { runs: 0, failed: 0 };
    entry.runs += 1;
    if (run.status === "FAILED") entry.failed += 1;
    counts.set(run.studioKey, entry);
  }
  return [...counts.entries()].map(([studioKey, c]) => ({ studioKey, ...c })).sort((a, b) => b.runs - a.runs);
}

/** A time left in words (“About 40 s left”, “About 2 min left”), rounded the way a person says it. */
export function formatEta(ms: number, words: { etaSeconds: string; etaMinutes: string }): string {
  const seconds = Math.max(1, Math.round(ms / 1000));
  if (seconds < 60)
    return words.etaSeconds.replace("{s}", String(seconds < 10 ? seconds : Math.round(seconds / 5) * 5));
  return words.etaMinutes.replace("{m}", String(Math.round(seconds / 60)));
}
