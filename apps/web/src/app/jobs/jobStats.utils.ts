import { differenceInMilliseconds, parseISO } from "date-fns";
import type { Job } from "@/core/types/jobs.types";
import { JOB_STATUS } from "@/core/constants/http.constants";

export interface JobStats {
  total: number;
  completed: number;
  failed: number;
  avgMs: number;
  avgDuration: string;
  maxDuration: string;
  totalMs: number;
  totalDuration: string;
  uniqueModels: number;
  uniqueFeatures: number;
  topModel: string | null;
  topFeature: string | null;
}

/**
 * Computes aggregated KPI statistics from a list of jobs.
 * Includes completion rates, duration analysis, and model/feature popularity.
 */
export function computeJobStats(jobs: Job[]): JobStats {
  const completed = jobs.filter((j) => j.status === JOB_STATUS.COMPLETED).length;
  const failed = jobs.filter((j) => j.status === JOB_STATUS.FAILED).length;

  const durations: number[] = [];
  jobs.forEach((j) => {
    if (j.status === JOB_STATUS.COMPLETED && j.createdAt && j.updatedAt) {
      try {
        const diff = differenceInMilliseconds(parseISO(j.updatedAt), parseISO(j.createdAt));
        if (diff >= 0) durations.push(diff);
      } catch {
        /* ignore */
      }
    }
    const rd = j.result?.durationMs;
    if (typeof rd === "number" && rd > 0 && durations.length === 0) {
      durations.push(rd);
    }
  });

  const totalMs = durations.reduce((a, b) => a + b, 0);
  const avgMs = durations.length > 0 ? totalMs / durations.length : 0;
  const maxMs = durations.length > 0 ? Math.max(...durations) : 0;

  const modelCounts = new Map<string, number>();
  jobs.forEach((j) => {
    const model = (j.payload?.model as string) || null;
    if (model) modelCounts.set(model, (modelCounts.get(model) || 0) + 1);
  });

  const featureCounts = new Map<string, number>();
  jobs.forEach((j) => {
    const shortKey = j.featureKey.split(".").pop() || j.featureKey;
    featureCounts.set(shortKey, (featureCounts.get(shortKey) || 0) + 1);
  });

  let topModel: string | null = null;
  let topModelCount = 0;
  modelCounts.forEach((count, model) => {
    if (count > topModelCount) {
      topModel = model;
      topModelCount = count;
    }
  });

  let topFeature: string | null = null;
  let topFeatureCount = 0;
  featureCounts.forEach((count, feature) => {
    if (count > topFeatureCount) {
      topFeature = feature;
      topFeatureCount = count;
    }
  });

  return {
    total: jobs.length,
    completed,
    failed,
    avgMs: Math.round(avgMs),
    avgDuration: formatMs(avgMs),
    maxDuration: formatMs(maxMs),
    totalMs: Math.round(totalMs),
    totalDuration: formatMs(totalMs),
    uniqueModels: modelCounts.size,
    uniqueFeatures: featureCounts.size,
    topModel: topModel ? `⚡ ${topModel}` : null,
    topFeature: topFeature ? `⚡ ${topFeature}` : null,
  };
}

/** Formats a duration in milliseconds into a human-readable string. */
export function formatMs(ms: number): string {
  if (ms === 0) return "—";
  if (ms < 1000) return `${Math.round(ms)}ms`;
  const secs = Math.floor(ms / 1000);
  if (secs < 60) return `${secs}.${Math.round((ms % 1000) / 100)}s`;
  const mins = Math.floor(secs / 60);
  const remSecs = secs % 60;
  if (mins < 60) return `${mins}m ${remSecs}s`;
  const hrs = Math.floor(mins / 60);
  const remMins = mins % 60;
  return `${hrs}h ${remMins}m`;
}
