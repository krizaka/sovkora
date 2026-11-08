"use client";

import { useEffect } from "react";

/** How often to re-sync the job list against the DB while jobs are in flight. */
const JOB_RECONCILE_POLL_MS = 10000;

/**
 * Reconciles the job list against the database (the source of truth) so a missed
 * SSE delta never leaves a job stuck in PENDING/PROCESSING. The live SSE stream is
 * best-effort — if the connection is down when a job completes, the terminal event
 * is lost and the client would otherwise show "in progress" forever.
 *
 * Two triggers cover that gap:
 *  - tab focus / visibility: refetch when the user returns to the tab;
 *  - polling: while any job is active, re-sync on a fixed interval.
 *
 * Pairs with the router-side disk reconciler that self-heals the DB itself, so a
 * refetch always converges on the true terminal status.
 */
export function useJobReconciliation(
  isAuthenticated: boolean,
  hasActiveJobs: boolean,
  refetch: () => void | Promise<void>,
) {
  useEffect(() => {
    if (!isAuthenticated) return;
    const onVisible = () => {
      if (document.visibilityState === "visible") {
        void refetch();
      }
    };
    window.addEventListener("focus", onVisible);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.removeEventListener("focus", onVisible);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [isAuthenticated, refetch]);

  useEffect(() => {
    if (!isAuthenticated || !hasActiveJobs) return;
    const intervalId = setInterval(() => {
      void refetch();
    }, JOB_RECONCILE_POLL_MS);
    return () => clearInterval(intervalId);
  }, [isAuthenticated, hasActiveJobs, refetch]);
}
