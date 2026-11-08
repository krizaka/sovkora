"use client";

import { useQuery } from "@tanstack/react-query";
import type { Run } from "@krizaka/orazaka-shared";
import { JobsApi, type JobsPage } from "@/services/jobs.api";
import { StudioApi } from "@/services/studio.api";

/** How far back the charts read: the last hundred jobs cover weeks of a person's work, in one request. */
const HISTORY = 100;

/**
 * The work the charts are drawn from — the signed-in person's own jobs and Studio runs, cached by TanStack Query so
 * the dashboard, the credits page and the job queue share one fetch.
 */
export function useInsights(enabled = true) {
  const jobs = useQuery<JobsPage>({
    queryKey: ["jobs", 0, HISTORY],
    queryFn: () => JobsApi.fetchPage(0, HISTORY),
    enabled,
  });
  const runs = useQuery<Run[]>({ queryKey: ["studio-runs"], queryFn: () => StudioApi.fetchRuns(), enabled });
  return {
    jobs: jobs.data?.content ?? [],
    runs: runs.data ?? [],
    isLoading: jobs.isLoading || runs.isLoading,
    hasError: Boolean(jobs.error && runs.error),
  };
}
