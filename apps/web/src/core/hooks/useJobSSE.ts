/* eslint-disable */
"use client";

import { useEffect, useRef, useCallback } from "react";
import { JOB_STATUS } from "@/core/constants/http.constants";
import type { Job, JobPhase } from "@/core/types/jobs.types";

/** What the executor said it is doing, beside the percentage. */
export interface JobProgressDetail {
  phase?: JobPhase;
  step?: number;
  steps?: number;
}

interface JobSSECallbacks {
  onJobUpdate: (job: Job) => void;
  onJobProgress: (jobId: string, progress: number, detail?: JobProgressDetail) => void;
  /** A job changed state: started, completed or failed — the caller tells the person (in their language). */
  onJobEvent: (job: Job, event: "started" | "completed" | "failed" | "cancelled") => void;
  getJobs: () => Job[];
}

/**
 * Custom hook that establishes and manages the SSE connection for job status streaming.
 * Handles reconnection with exponential backoff and automatic cleanup.
 *
 * @param isAuthenticated Whether the user is authenticated.
 * @param hasActiveJobs Whether there are active jobs to monitor.
 * @param callbacks Event handlers for job updates, progress, and state changes.
 */
export function useJobSSE(isAuthenticated: boolean, hasActiveJobs: boolean, callbacks: JobSSECallbacks) {
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const reconnectDelayRef = useRef<number>(1000);
  const eventSourceRef = useRef<EventSource | null>(null);
  const callbacksRef = useRef(callbacks);

  useEffect(() => {
    callbacksRef.current = callbacks;
  }, [callbacks]);

  const handleJobStatus = useCallback((event: MessageEvent) => {
    try {
      const updatedJob: Job = JSON.parse(event.data);
      callbacksRef.current.onJobUpdate(updatedJob);

      if (updatedJob.status === "PROCESSING") {
        callbacksRef.current.onJobEvent(updatedJob, "started");
      } else if (updatedJob.status === "COMPLETED") {
        callbacksRef.current.onJobProgress(updatedJob.id, 100);
        callbacksRef.current.onJobEvent(updatedJob, "completed");
      } else if (updatedJob.status === "FAILED") {
        callbacksRef.current.onJobProgress(updatedJob.id, 0);
        callbacksRef.current.onJobEvent(updatedJob, "failed");
      } else if (updatedJob.status === JOB_STATUS.CANCELLED) {
        callbacksRef.current.onJobProgress(updatedJob.id, 0);
        callbacksRef.current.onJobEvent(updatedJob, "cancelled");
      }

      if (["COMPLETED", "FAILED", "CANCELLED", "DELETED"].includes(updatedJob.status)) {
        const otherActive = callbacksRef.current
          .getJobs()
          .filter((j) => j.id !== updatedJob.id && (j.status === "PENDING" || j.status === "PROCESSING"));
        if (otherActive.length === 0) {
          console.log("[useJobSSE] All active tasks completed. Closing SSE connection.");
          if (eventSourceRef.current) {
            eventSourceRef.current.close();
            eventSourceRef.current = null;
          }
        }
      }
    } catch (err) {
      console.error("Failed to parse job SSE event data", err);
    }
  }, []);

  const handleJobProgress = useCallback((event: MessageEvent) => {
    try {
      const progressPayload: { jobId: string; progress: number } & JobProgressDetail = JSON.parse(event.data);
      callbacksRef.current.onJobProgress(progressPayload.jobId, progressPayload.progress, {
        phase: progressPayload.phase,
        step: progressPayload.step,
        steps: progressPayload.steps,
      });
    } catch (err) {
      console.error("Failed to parse job progress SSE event data", err);
    }
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !hasActiveJobs) {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      return;
    }

    function onReconnect() {
      connectSSE();
    }

    function connectSSE() {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }

      const es = new EventSource("/api/v1/jobs/stream");
      eventSourceRef.current = es;

      es.onopen = () => {
        console.log("[useJobSSE] SSE Connection established");
        reconnectDelayRef.current = 1000;
      };

      es.addEventListener("job-status", handleJobStatus);
      es.addEventListener("job-progress", handleJobProgress);

      es.onerror = (err) => {
        console.error("SSE stream connection error, reconnecting...", err);
        es.close();
        eventSourceRef.current = null;

        const nextDelay = Math.min(reconnectDelayRef.current * 2, 30000);
        reconnectDelayRef.current = nextDelay;

        reconnectTimeoutRef.current = setTimeout(onReconnect, reconnectDelayRef.current);
      };
    }

    connectSSE();

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [isAuthenticated, hasActiveJobs, handleJobStatus, handleJobProgress]);
}
