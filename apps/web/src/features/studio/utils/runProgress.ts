/**
 * @file runProgress.ts
 * @description What a running Studio step tells the person, from facts only (orazaka-web-client#85): the phase the
 * executor reported, step n of N, and a time left that rests on the median of runs that really finished — never a
 * guess. Pure functions; the clock is a parameter.
 */

import { differenceInMilliseconds, parseISO } from "date-fns";
import type { RunStep } from "@krizaka/orazaka-shared";
import { JOB_STATUS } from "@/core/constants/http.constants";
import type { Job, JobPhase } from "@/core/types/jobs.types";

// @fixed: the same floor as the server (RunStepView.MIN_SAMPLES) — fewer finished runs than this is an anecdote
export const MIN_ESTIMATE_SAMPLES = 3;

/** The phase to show: what the executor said, else "queued" until a job reports (it has been dispatched, not started). */
export function phaseOf(job: Pick<Job, "phase" | "status"> | undefined): JobPhase {
  if (job?.phase) return job.phase;
  if (job?.status === JOB_STATUS.PROCESSING) return "generating";
  return "queued";
}

export type Remaining =
  | { tag: "left"; ms: number; samples: number }
  | { tag: "overdue"; typicalMs: number; samples: number }
  | { tag: "none" };

/**
 * The time left of a running step: the measured median wall time of this step in this Studio minus what it has already
 * taken. Past the median it says so ("overdue") instead of inventing a new figure. No estimate (too few runs, no start
 * time) is "none" — the caller then says nothing about time but the time so far.
 */
export function remainingOf(step: Pick<RunStep, "startedAt" | "estimateMs" | "estimateSamples">, now: Date): Remaining {
  const samples = step.estimateSamples ?? 0;
  if (!step.startedAt || !step.estimateMs || step.estimateMs <= 0 || samples < MIN_ESTIMATE_SAMPLES)
    return { tag: "none" };
  const elapsed = Math.max(0, differenceInMilliseconds(now, parseISO(step.startedAt)));
  const left = step.estimateMs - elapsed;
  return left > 0 ? { tag: "left", ms: left, samples } : { tag: "overdue", typicalMs: step.estimateMs, samples };
}

/** "42 s", "3 min 05 s" — a duration in words a person reads. */
export function formatSpan(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  if (total < 60) return `${total} s`;
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return seconds === 0 ? `${minutes} min` : `${minutes} min ${String(seconds).padStart(2, "0")} s`;
}

/** Milliseconds a step has been running; null before it was dispatched. */
export function elapsedOf(step: Pick<RunStep, "startedAt">, now: Date): number | null {
  return step.startedAt ? Math.max(0, differenceInMilliseconds(now, parseISO(step.startedAt))) : null;
}
