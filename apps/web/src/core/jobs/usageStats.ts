/**
 * @file usageStats.ts
 * @description The figures of the Credits & usage page and of the dashboard head, and the export of the same data —
 * read from the jobs and Studio runs the platform already returns, never estimated. Pure (dates through date-fns, the
 * clock injected): the KPI row, the charts and the downloaded file all draw from the same rows.
 */

import { addDays, formatISO, parseISO, startOfDay, subDays } from "date-fns";
import type { Run } from "@krizaka/orazaka-shared";
import { jobDuration, activityByDay, type DayActivity } from "@/core/jobs/jobInsights";
import { jobKind } from "@/core/jobs/jobMedia";
import type { Job, JobStatus } from "@/core/types/jobs.types";

const COMPLETED: JobStatus = "COMPLETED";
const FAILED: JobStatus = "FAILED";

/** The periods the page offers, in days. */
export const PERIODS = [7, 30, 90] as const;
export type Period = (typeof PERIODS)[number];
/** The same periods as the values of the `?period=` parameter (the URL is the state). */
export const PERIOD_KEYS = ["7", "30", "90"] as const satisfies readonly `${Period}`[];
export const DEFAULT_PERIOD_KEY = "30" as const;

/** What a period holds. */
export interface Totals {
  jobs: number;
  completed: number;
  failed: number;
  /** Completed / finished (completed + failed), 0–1; null while nothing has finished. */
  successRate: number | null;
  /** Median seconds from request to result; null when no job finished. */
  medianSeconds: number | null;
  runs: number;
}

export interface UsageSummary {
  current: Totals;
  /** The period right before, the same length: what the trends compare with. */
  previous: Totals;
  /** One row per day of the current period, oldest first, empty days included. */
  perDay: DayActivity[];
}

/** The `days` ending today (`offset` 0) or the `days` before them (`offset` 1): [start, end) at local midnight. */
function windowOf(days: number, offset: number, now: Date): [Date, Date] {
  const end = startOfDay(addDays(subDays(now, days * offset), 1));
  return [startOfDay(subDays(end, days)), end];
}

const within = (iso: string, [start, end]: [Date, Date]) => {
  const at = parseISO(iso);
  return at >= start && at < end;
};

const median = (values: readonly number[]): number | null => {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
};

function totalsOf(jobs: readonly Job[], runs: readonly Run[], range: [Date, Date]): Totals {
  const inRange = jobs.filter((job) => within(job.createdAt, range));
  const completed = inRange.filter((job) => job.status === COMPLETED).length;
  const failed = inRange.filter((job) => job.status === FAILED).length;
  const seconds = inRange
    .map(jobDuration)
    .filter((ms): ms is number => ms !== null)
    .map((ms) => ms / 1000);
  const mid = median(seconds);
  return {
    jobs: inRange.length,
    completed,
    failed,
    successRate: completed + failed === 0 ? null : completed / (completed + failed),
    medianSeconds: mid === null ? null : Math.round(mid * 10) / 10,
    runs: runs.filter((run) => within(run.startedAt, range)).length,
  };
}

/** The figures of the last `days` days and of the `days` before, and the days themselves. */
export function usageSummary(jobs: readonly Job[], runs: readonly Run[], days: number, now: Date): UsageSummary {
  return {
    current: totalsOf(jobs, runs, windowOf(days, 0, now)),
    previous: totalsOf(jobs, runs, windowOf(days, 1, now)),
    perDay: activityByDay(jobs, days, now),
  };
}

/** The jobs and runs of the last `days` days, newest first. */
export function usageRows(jobs: readonly Job[], runs: readonly Run[], days: number, now: Date) {
  const range = windowOf(days, 0, now);
  return {
    jobs: jobs.filter((job) => within(job.createdAt, range)).sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    runs: runs.filter((run) => within(run.startedAt, range)).sort((a, b) => b.startedAt.localeCompare(a.startedAt)),
  };
}

/**
 * A CSV cell a spreadsheet will not run: a value that starts with `=`, `+`, `-`, `@`, a tab or a carriage return is
 * written with a leading apostrophe (OWASP's CSV-injection rule), then quoted when it holds a separator.
 */
export function csvCell(value: string | number | null | undefined): string {
  let text = value === null || value === undefined ? "" : String(value);
  if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
  return /[",\n\r;]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

const line = (cells: readonly (string | number | null | undefined)[]) => cells.map(csvCell).join(",");

/** The usage of a period as one CSV: a row per job or Studio run (BOM first, so Excel reads the accents). */
export function usageCsv(jobs: readonly Job[], runs: readonly Run[], days: number, now: Date): string {
  const rows = usageRows(jobs, runs, days, now);
  const out = [line(["type", "id", "started_at", "name", "kind", "status", "seconds"])];
  for (const job of rows.jobs) {
    const ms = jobDuration(job);
    out.push(
      line(["job", job.id, job.createdAt, job.featureKey, jobKind(job), job.status, ms === null ? "" : ms / 1000]),
    );
  }
  for (const run of rows.runs)
    out.push(line(["studio_run", run.id, run.startedAt, run.studioKey, "studio", run.status, ""]));
  return `﻿${out.join("\n")}\n`;
}

/** The same data as JSON, with the period's figures. */
export function usageJson(jobs: readonly Job[], runs: readonly Run[], days: number, now: Date): string {
  const rows = usageRows(jobs, runs, days, now);
  const { current } = usageSummary(jobs, runs, days, now);
  return JSON.stringify(
    {
      period: { days, to: formatISO(now, { representation: "date" }) },
      totals: current,
      jobs: rows.jobs.map((job) => ({
        id: job.id,
        startedAt: job.createdAt,
        name: job.featureKey,
        kind: jobKind(job),
        status: job.status,
        seconds: jobDuration(job) === null ? null : (jobDuration(job) as number) / 1000,
      })),
      runs: rows.runs.map((run) => ({
        id: run.id,
        startedAt: run.startedAt,
        studio: run.studioKey,
        status: run.status,
      })),
    },
    null,
    2,
  );
}

/** `+24 %`-style change of `now` against `before` (a ratio), or null when it cannot be said. */
export const changeOf = (now: number, before: number): number | null => (before === 0 ? null : now / before - 1);

/** A change written as the reader writes it (`+24 %`, `−8 %`), or null when it cannot be said. */
export function formatChange(ratio: number | null, locale: string): string | null {
  if (ratio === null) return null;
  return new Intl.NumberFormat(locale, {
    style: "percent",
    maximumFractionDigits: 0,
    signDisplay: "exceptZero",
  }).format(ratio);
}
