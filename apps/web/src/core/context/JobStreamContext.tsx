"use client";

import React, { createContext, useCallback, useContext, useEffect, useMemo, useState, useRef } from "react";
import { useAuth } from "@/core/hooks/useAuth";
import type { Job } from "@/core/types/jobs.types";
import { JobsApi } from "@/services/jobs.api";
import { parseISO } from "date-fns";
import { useChatStreamClient } from "@/core/hooks/useChatStreamClient";
import { useJobSSE, type JobProgressDetail } from "@/core/hooks/useJobSSE";
import { useJobReconciliation } from "@/core/hooks/useJobReconciliation";
import { useJobToasts } from "@/core/hooks/useJobToasts";
import { JOB_STATUS } from "@/core/constants/http.constants";
import type { JobStreamContextType } from "@/core/context/jobStream.types";
import { usePlaygroundState } from "@/core/hooks/usePlaygroundState";

const JobStreamContext = createContext<JobStreamContextType | undefined>(undefined);

/**
 * Global React Context Provider establishing a persistent EventSource stream
 * pointing to the BFF proxy. Tracks and broadcasts all async task progress.
 */
export function JobStreamProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const { isAuthenticated, user } = useAuth();
  const [jobs, setJobs] = useState<Job[]>([]);
  const [jobProgress, setJobProgress] = useState<Record<string, number>>({});
  const jobsRef = useRef<Job[]>([]);
  useEffect(() => {
    jobsRef.current = jobs;
  }, [jobs]);

  // The open conversation belongs to who is signed in: another identity starts with none (derived, not reset by an effect).
  const identity = `${user?.id ?? ""}|${isAuthenticated}`;
  const [active, setActive] = useState<{ identity: string; id: string }>({ identity, id: "" });
  const activeConversationId = active.identity === identity ? active.id : "";
  const setActiveConversationId = useCallback((id: string) => setActive({ identity, id }), [identity]);

  const {
    playgroundInputs,
    setPlaygroundInput,
    playgroundResults,
    setPlaygroundResult,
    activeJobIdByNodeId,
    setActiveJobIdForNode,
  } = usePlaygroundState();
  const [videoAnalysisJobId, setVideoAnalysisJobId] = useState<string | null>(null);

  const [chatInput, setChatInput] = useState<string>("");
  const { isChatStreaming, chatPipeline, chatStageProgress, chatRouting, startChatStream, stopChatStream } =
    useChatStreamClient();

  const [videoAnalysisIsUploading, setVideoAnalysisIsUploading] = useState<boolean>(false);
  const [videoAnalysisError, setVideoAnalysisError] = useState<string | null>(null);

  const [ragQuery, setRagQuery] = useState<string>("");
  const [ragResult, setRagResult] = useState<string | null>(null);
  const [ragIsPending, setRagIsPending] = useState<boolean>(false);
  const [ragError, setRagError] = useState<string | null>(null);

  const activeJobsCount = jobs.filter(
    (j) => j.status === JOB_STATUS.PENDING || j.status === JOB_STATUS.PROCESSING,
  ).length;

  const lastJobs = [...jobs]
    .sort((a, b) => parseISO(b.createdAt).getTime() - parseISO(a.createdAt).getTime())
    .slice(0, 5);

  /** A job's progression, announced by the platform Toaster (mounted once in Providers), in the reader's language. */
  const jobToasts = useJobToasts();
  const handleJobEvent = React.useCallback(
    (job: Job, event: "started" | "completed" | "failed" | "cancelled") => jobToasts[event](job),
    [jobToasts],
  );

  const fetchJobs = React.useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      const pageData = await JobsApi.fetchPage(0, 20);
      setJobs(pageData.content);
      const initialProgress: Record<string, number> = {};
      pageData.content.forEach((job) => {
        if (job.progress !== undefined) {
          initialProgress[job.id] = job.progress;
        }
      });
      setJobProgress(initialProgress);
    } catch (err) {
      console.error("Failed to load jobs history", err);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (isAuthenticated) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void fetchJobs();
    } else {
      setJobs([]);
    }
  }, [isAuthenticated, fetchJobs]);

  const hasActiveJobs = activeJobsCount > 0;

  const handleJobUpdate = React.useCallback((updatedJob: Job) => {
    setJobs((prev) => {
      const index = prev.findIndex((j) => j.id === updatedJob.id);
      if (index !== -1) {
        const updated = [...prev];
        updated[index] = updatedJob;
        return updated;
      }
      return [updatedJob, ...prev];
    });
  }, []);

  const handleJobProgress = React.useCallback(
    (jobId: string, progress: number, detail?: JobProgressDetail) => {
      setJobs((prev) => {
        const index = prev.findIndex((j) => j.id === jobId);
        if (index !== -1) {
          const updated = [...prev];
          updated[index] = {
            ...updated[index],
            progress,
            ...(detail?.phase ? { phase: detail.phase } : {}),
            ...(detail?.steps ? { step: detail.step, steps: detail.steps } : {}),
          };
          return updated;
        }
        return prev;
      });
      setJobProgress((prev) => ({ ...prev, [jobId]: progress }));
      const job = jobsRef.current.find((j) => j.id === jobId);
      if (job) jobToasts.progressed(job, progress);
    },
    [jobToasts],
  );

  useJobSSE(isAuthenticated, hasActiveJobs, {
    onJobUpdate: handleJobUpdate,
    onJobProgress: handleJobProgress,
    onJobEvent: handleJobEvent,
    getJobs: () => jobsRef.current,
  });

  // Safety net for missed SSE events: re-sync the job list from the DB on tab
  // focus and while jobs are in flight, so completions are never lost.
  useJobReconciliation(isAuthenticated, hasActiveJobs, fetchJobs);

  const contextValue = useMemo<JobStreamContextType>(
    () => ({
      jobs,
      activeJobsCount,
      lastJobs,
      refreshJobs: fetchJobs,
      activeConversationId,
      setActiveConversationId,
      playgroundInputs,
      setPlaygroundInput,
      playgroundResults,
      setPlaygroundResult,
      activeJobIdByNodeId,
      setActiveJobIdForNode,
      videoAnalysisJobId,
      setVideoAnalysisJobId,
      chatInput,
      setChatInput,
      isChatStreaming,
      chatPipeline,
      chatStageProgress,
      chatRouting,
      startChatStream,
      stopChatStream,
      videoAnalysisIsUploading,
      setVideoAnalysisIsUploading,
      videoAnalysisError,
      setVideoAnalysisError,
      ragQuery,
      setRagQuery,
      ragResult,
      setRagResult,
      ragIsPending,
      setRagIsPending,
      ragError,
      setRagError,
      jobProgress,
    }),
    [
      jobs,
      activeJobsCount,
      lastJobs,
      fetchJobs,
      activeConversationId,
      setActiveConversationId,
      playgroundInputs,
      setPlaygroundInput,
      playgroundResults,
      setPlaygroundResult,
      activeJobIdByNodeId,
      setActiveJobIdForNode,
      videoAnalysisJobId,
      chatInput,
      isChatStreaming,
      chatPipeline,
      chatStageProgress,
      chatRouting,
      startChatStream,
      stopChatStream,
      videoAnalysisIsUploading,
      videoAnalysisError,
      ragQuery,
      ragResult,
      ragIsPending,
      ragError,
      jobProgress,
    ],
  );

  return <JobStreamContext.Provider value={contextValue}>{children}</JobStreamContext.Provider>;
}

export function useJobStream() {
  const context = useContext(JobStreamContext);
  if (context === undefined) {
    throw new Error("useJobStream must be used within a JobStreamProvider");
  }
  return context;
}
