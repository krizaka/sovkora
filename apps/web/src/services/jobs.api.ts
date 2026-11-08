import { restRequest } from "@/services/rest-client";
import type { Job } from "@/core/types/jobs.types";

// ── Types ────────────────────────────────────────────────────────────────────

export interface JobsPage {
  content: Job[];
  totalPages: number;
  totalElements: number;
}

/**
 * Stateless adapter exposing job-related network operations.
 */
export const JobsApi = {
  /**
   * Fetches a paginated list of async jobs from the BFF REST proxy.
   *
   * @param page - Zero-indexed page number.
   * @param size - Number of items per page.
   * @returns The paginated job response with content and metadata.
   */
  fetchPage: async (page: number, size: number): Promise<JobsPage> => {
    // No Authorization header here: the BFF proxy injects the session token server-side, so
    // the browser only ever sends its cookie (AGENTS.md §8 — BFF mandatory). Going through
    // `restRequest` also means a 401 tears down a stale session instead of looping forever.
    const data = await restRequest<Partial<JobsPage>>(`/api/v1/jobs?page=${page}&size=${size}`);

    return {
      content: data?.content ?? [],
      totalPages: data?.totalPages ?? 0,
      totalElements: data?.totalElements ?? 0,
    };
  },

  /**
   * Deletes a finished job's result: the files leave every store and the row becomes a tombstone (orazaka-web-client#86).
   * Owner only; a job still running is refused (cancel it first).
   *
   * @param id - The job.
   */
  deleteJob: async (id: string): Promise<void> => {
    await restRequest<unknown>(`/api/v1/jobs/${encodeURIComponent(id)}`, { method: "DELETE" });
  },
} as const;
