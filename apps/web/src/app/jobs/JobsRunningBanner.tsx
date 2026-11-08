"use client";

import { Card } from "@krizaka/ui/card";
import { Mascot } from "@krizaka/ui/mascots";
import { Progress } from "@krizaka/ui/progress";
import { birdFor } from "@/components/mascot/birdFor";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { useNow } from "@/core/hooks/useNow";
import { formatEta, remainingFor } from "@/core/jobs/jobInsights";
import { jobLabel } from "@/core/jobs/jobMedia";
import type { Job } from "@/core/types/jobs.types";

interface JobsRunningBannerProps {
  /** The jobs still running (PENDING or PROCESSING). */
  jobs: readonly Job[];
  /** Finished jobs to measure from: the ETA is the median time the same work took on this machine. */
  history: readonly Job[];
}

/**
 * What runs on the machine right now: the bird of the first job thinks beside one progress bar per job — its name,
 * its percentage when the worker reports one, and the time left when the same work has finished here before (an ETA
 * nothing backs is not shown). A long wait is the moment a mascot is for; the banner disappears when nothing runs.
 */
export function JobsRunningBanner({ jobs, history }: Readonly<JobsRunningBannerProps>) {
  const { t } = useTranslation();
  const now = useNow(jobs.length > 0);
  if (jobs.length === 0) return null;

  return (
    <Card.Root className="border-accent/40">
      <Card.Body padding="lg" className="flex-row flex-wrap items-center gap-5">
        <Mascot bird={birdFor(jobs[0].featureKey)} mood="thinking" size={88} />
        <section aria-live="polite" className="flex min-w-0 flex-1 flex-col gap-2">
          <p className="text-base font-semibold text-fg">
            {fill(t.showcase.jobs.runningTitle, { count: jobs.length })}
          </p>
          <p className="text-[13px] text-fg-secondary">{t.showcase.jobs.runningBody}</p>
          <ul className="flex flex-col gap-3">
            {jobs.slice(0, 3).map((job) => {
              const left = remainingFor(job, history, now);
              return (
                <li key={job.id}>
                  <Progress
                    size="sm"
                    showLabel
                    label={jobLabel(t, job)}
                    value={typeof job.progress === "number" && job.progress > 0 ? job.progress : undefined}
                    eta={left === null ? undefined : formatEta(left, t.insights.queue)}
                  />
                </li>
              );
            })}
          </ul>
        </section>
      </Card.Body>
    </Card.Root>
  );
}
