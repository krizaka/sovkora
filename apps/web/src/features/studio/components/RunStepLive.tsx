"use client";

import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import type { Job } from "@/core/types/jobs.types";
import type { RunStep } from "@krizaka/orazaka-shared";
import { formatEta } from "@/core/jobs/jobInsights";
import { elapsedOf, formatSpan, phaseOf, remainingOf } from "@/features/studio/utils/runProgress";

interface RunStepLiveProps {
  step: RunStep;
  /** The step's job as the live stream sees it, when it does. */
  job?: Job;
  now: Date;
}

/**
 * What the running step is doing right now, in words: the phase the executor reported (waiting for a machine, loading
 * the model, generating step n of N, encoding, saving), the time it has taken, and the time left only when it rests on
 * runs that really finished — otherwise it says why there is none. Nothing here is invented.
 */
export function RunStepLive({ step, job, now }: Readonly<RunStepLiveProps>) {
  const { t } = useTranslation();
  const w = t.runProgress;
  const phase = phaseOf(job);
  const elapsed = elapsedOf(step, now);
  const remaining = remainingOf(step, now);
  const generating = phase === "generating" && job?.step && job.steps;
  return (
    <ul aria-live="polite" className="mt-2 flex flex-col gap-0.5 text-xs text-fg-secondary">
      <li className="font-medium text-fg">
        {w.phases[phase]}
        {generating ? ` · ${fill(w.stepOf, { step: job.step ?? 0, steps: job.steps ?? 0 })}` : ""}
      </li>
      {elapsed !== null && <li>{fill(w.elapsed, { time: formatSpan(elapsed) })}</li>}
      {remaining.tag === "left" && (
        <li>
          {formatEta(remaining.ms, t.insights.queue)}
          <span className="text-fg-muted"> · {fill(w.basis, { n: remaining.samples })}</span>
        </li>
      )}
      {remaining.tag === "overdue" && (
        <li className="text-fg-warning">{fill(w.overdue, { time: formatSpan(remaining.typicalMs) })}</li>
      )}
      {remaining.tag === "none" && <li className="text-fg-muted">{w.noEstimate}</li>}
    </ul>
  );
}
