/**
 * @file useJobs.ts
 * @description TanStack Query hook for paginated job listing.
 * Replaces inline `fetchPage()` in `jobs/page.tsx` and `JobStreamContext.tsx`.
 */

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { JobsApi } from "@/services/jobs.api";
import type { JobsPage } from "@/services/jobs.api";

const QUERY_KEY_PREFIX = "jobs" as const;

/**
 * Hook providing cached, reactive access to paginated job data.
 *
 * @param page - Zero-indexed page number.
 * @param size - Number of items per page.
 * @param enabled - Whether to enable the query (typically bound to auth state).
 * @returns An object with page data, loading state, error, and refresh trigger.
 */
export function useJobs(page: number, size: number, enabled = true) {
  const queryClient = useQueryClient();

  const query = useQuery<JobsPage>({
    queryKey: [QUERY_KEY_PREFIX, page, size],
    queryFn: () => JobsApi.fetchPage(page, size),
    enabled,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: [QUERY_KEY_PREFIX] });

  return {
    jobs: query.data?.content ?? [],
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
    isLoading: query.isLoading,
    error: query.error,
    refresh,
  };
}
