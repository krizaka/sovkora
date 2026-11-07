"use client";

import { useEffect } from "react";
import { Progress } from "@krizaka/ui/progress";
import { Stepper, type Step, type StepStatus } from "@krizaka/ui/stepper";
import type { RunStep, RunStepStatus } from "@krizaka/orazaka-shared";
import { useJobStream } from "@/core/context/JobStreamContext";
import { useTranslation } from "@/core/context/LocaleContext";
import { useNow } from "@/core/hooks/useNow";
import { formatEta, remainingFor } from "@/core/jobs/jobInsights";
import { RunStepLive } from "@/features/studio/components/RunStepLive";
import { remainingOf } from "@/features/studio/utils/runProgress";

interface RunTimelineProps {
  steps: RunStep[];
}

/** How each state of a run's step reads on the Stepper. A skipped step is behind the run, like a done one. */
const STEP_STATUS: Record<RunStepStatus, StepStatus> = {
  PENDING: "upcoming",
  RUNNING: "current",
  SUCCEEDED: "done",
  SKIPPED: "done",
  FAILED: "error",
  CANCELLED: "upcoming",
};

/**
 * The per-step live timeline, on the Krizaka Stepper: done steps are checked and linked, the running one carries its
 * job's progress (the worker's percentage over SSE) and the time left — the median the same work took on this
 * machine, never a guess — and a failed one says why.
 *
 * Fan-out instances are shown individually rather than collapsed: when three of five
 * photos succeeded and two failed, "describe: partial" tells the user nothing they
 * can act on, while five rows tell them exactly which images to replace.
 */
export function RunTimeline({ steps }: Readonly<RunTimelineProps>) {
  const { t } = useTranslation();
  const { jobs, jobProgress, refreshJobs } = useJobStream();
  const running = steps.some((step) => step.status === "RUNNING");
  const now = useNow(running);
  // A step's job the live stream does not know yet (the run was started elsewhere — a composer, the API): load the
  // jobs once, so the stream follows it (its progress, its ETA, its toasts).
  const unknownJob = steps.some(
    (step) => step.status === "RUNNING" && step.jobId && !jobs.some((j) => j.id === step.jobId),
  );
  useEffect(() => {
    if (unknownJob) void refreshJobs();
  }, [unknownJob, refreshJobs]);

  if (steps.length === 0) {
    return null;
  }

  const items: Step[] = steps.map((step) => {
    const status = STEP_STATUS[step.status];
    const job = step.jobId ? jobs.find((j) => j.id === step.jobId) : undefined;
    const percent = step.jobId ? jobProgress[step.jobId] : undefined;
    const left = status === "current" && job ? remainingFor(job, jobs, now) : null;
    // The server's measured estimate (RunStepLive) wins; the stream's own median is the fallback, never both.
    const measured = remainingOf(step, now).tag !== "none";
    const eta = left === null || measured ? undefined : formatEta(left, t.insights.queue);
    return {
      title: step.ordinal > 0 ? `${step.stepId} #${step.ordinal + 1}` : step.stepId,
      status,
      value: typeof percent === "number" && percent > 0 ? percent / 100 : undefined,
      description: step.status === "CANCELLED" ? t.studio.runCancelled : eta,
      content:
        status === "current" ? (
          <>
            <Progress
              size="sm"
              label={step.stepId}
              value={typeof percent === "number" && percent > 0 ? percent : undefined}
              eta={eta}
              className="w-72 max-w-full"
            />
            <RunStepLive step={step} job={job} now={now} />
          </>
        ) : status === "error" && step.error ? (
          <p role="alert" className="text-xs text-fg-danger">
            {step.error}
          </p>
        ) : undefined,
    };
  });

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-xs font-semibold text-fg-muted">{t.studio.runSteps}</h2>
      <Stepper
        steps={items}
        labels={t.insights.queue.steps}
        className="rounded-xl border border-border-default bg-surface-1 p-5"
      />
    </section>
  );
}
