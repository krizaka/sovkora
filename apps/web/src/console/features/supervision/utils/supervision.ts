/**
 * @file supervision.ts
 * @description The platform's figures, derived from what the billing ledger and the job service return — pure
 * functions (dates through date-fns), tested on their own.
 */

import { differenceInMilliseconds, parseISO, subDays } from "date-fns";
import type { ActorConsumption, CapabilityUsage, JobRecord } from "@krizaka/orazaka-shared";

/** Totals over every capability: requests served, credits charged, refusal share of the attempts. */
export function usageTotals(rows: readonly CapabilityUsage[]) {
  const requests = rows.reduce((sum, r) => sum + r.events, 0);
  const refusals = rows.reduce((sum, r) => sum + r.refusals, 0);
  const credits = rows.reduce((sum, r) => sum + r.creditsCharged, 0);
  const attempts = requests + refusals;
  return { requests, refusals, credits, refusalRate: attempts === 0 ? 0 : refusals / attempts };
}

/** One row per capability (models merged), the label written for a person: `IMAGE` → `Image`. */
export function byCapability(rows: readonly CapabilityUsage[]) {
  const merged = new Map<string, { capability: string; events: number; refusals: number; credits: number }>();
  for (const row of rows) {
    const label = row.capability.charAt(0) + row.capability.slice(1).toLowerCase();
    const entry = merged.get(label) ?? { capability: label, events: 0, refusals: 0, credits: 0 };
    entry.events += row.events;
    entry.refusals += row.refusals;
    entry.credits += row.creditsCharged;
    merged.set(label, entry);
  }
  return [...merged.values()].sort((a, b) => b.credits - a.credits);
}

/** The heaviest consumers, an actor written short (its id's first block — the console holds no names). */
export function consumers(rows: readonly ActorConsumption[]) {
  return rows.map((row) => ({
    actor: `${row.actorId.split("-")[0]}…`,
    credits: row.creditsCharged,
    events: row.events,
  }));
}

/** A UTC day: the server renders the first paint, the browser hydrates it, and the two must name the same days. */
const dayKey = (date: Date) => date.toISOString().slice(0, 10);

/** The jobs of each of the last `days` days by outcome, oldest first, empty days included. */
export function jobsPerDay(jobs: readonly JobRecord[], days: number, now: Date = new Date()) {
  const rows = Array.from({ length: days }, (_, i) => ({
    day: dayKey(subDays(now, days - 1 - i)),
    completed: 0,
    failed: 0,
    running: 0,
  }));
  const index = new Map(rows.map((row) => [row.day, row]));
  for (const job of jobs) {
    const row = index.get(dayKey(parseISO(job.createdAt)));
    if (!row) continue;
    if (job.status === "COMPLETED") row.completed += 1;
    else if (job.status === "FAILED") row.failed += 1;
    else row.running += 1;
  }
  return rows;
}

/** The capability of a job, from its key's last segment (`orazaka.core.media.image` → `image`). */
const capabilityOf = (featureKey: string) => featureKey.split(".").pop() ?? featureKey;

/** The median seconds from request to result of the completed jobs, per capability, slowest first. */
export function latencyByCapability(jobs: readonly JobRecord[]) {
  const samples = new Map<string, number[]>();
  for (const job of jobs) {
    if (job.status !== "COMPLETED") continue;
    const ms = differenceInMilliseconds(parseISO(job.updatedAt), parseISO(job.createdAt));
    if (ms < 0) continue;
    const key = capabilityOf(job.featureKey);
    samples.set(key, [...(samples.get(key) ?? []), ms]);
  }
  return [...samples.entries()]
    .map(([capability, list]) => {
      const sorted = [...list].sort((a, b) => a - b);
      const mid = Math.floor(sorted.length / 2);
      const median = sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
      return { capability, seconds: Math.round(median / 100) / 10, samples: list.length };
    })
    .sort((a, b) => b.seconds - a.seconds);
}
