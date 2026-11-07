"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { StudioApi } from "@/services/studio.api";
import { useJobStream } from "@/core/context/JobStreamContext";
import { isTerminalRun, type Run } from "@krizaka/orazaka-shared";

export interface StudioRunState {
  run: Run | null;
  isLoading: boolean;
  hasError: boolean;
  reload: () => void;
}

/** How often a live run is re-read as a safety net behind the job stream. */
const LIVE_POLL_MS = 4000;

/**
 * One run, kept live.
 *
 * Progress rides the **existing** job stream rather than a second SSE channel
 * (ADR-034 §12.3): every step is an ordinary job, so the shared `JobStreamContext`
 * already receives its events. This hook watches that list and re-reads the run
 * when one of its steps moves — the run endpoint stays the authority on DAG state,
 * because the job stream knows about jobs and nothing about graphs.
 *
 * The slow poll behind it is the same safety net the jobs feature keeps for missed
 * events: a dropped event must delay the timeline, never strand it.
 *
 * @param runId - the run to follow, or null to follow nothing
 */
export function useStudioRun(runId: string | null): StudioRunState {
  const { jobs } = useJobStream();
  const [run, setRun] = useState<Run | null>(null);
  const [isLoading, setIsLoading] = useState(runId !== null);
  const [hasError, setHasError] = useState(false);

  const load = useCallback(async () => {
    if (!runId) {
      return;
    }
    try {
      setRun(await StudioApi.fetchRun(runId));
      setHasError(false);
    } catch {
      setHasError(true);
    } finally {
      setIsLoading(false);
    }
  }, [runId]);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  /** A fingerprint of this run's jobs as the shared stream currently sees them. */
  const streamFingerprint = useMemo(() => {
    if (!run) {
      return "";
    }
    const mine = new Set(run.steps.map((step) => step.jobId).filter(Boolean));
    return jobs
      .filter((job) => mine.has(job.id))
      .map((job) => `${job.id}:${job.status}`)
      .sort((a, b) => a.localeCompare(b))
      .join("|");
  }, [jobs, run]);

  useEffect(() => {
    if (!streamFingerprint) {
      return;
    }
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [streamFingerprint, load]);

  const isLive = run !== null && !isTerminalRun(run.status);

  useEffect(() => {
    if (!isLive) {
      return;
    }
    const timer = setInterval(() => void load(), LIVE_POLL_MS);
    return () => clearInterval(timer);
  }, [isLive, load]);

  return { run, isLoading, hasError, reload: () => void load() };
}
