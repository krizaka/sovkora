"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import { groupedToast, progressToast, toast } from "@krizaka/ui/toast";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { jobLabel } from "@/core/jobs/jobMedia";
import type { Job } from "@/core/types/jobs.types";

export interface JobToasts {
  /** A job started on the machine: a progress toast, updated in place as it advances (its id is the job's). */
  started: (job: Job) => void;
  /** The worker reported a percentage: the same toast grows. */
  progressed: (job: Job, percent: number) => void;
  /** It is done: its progress toast goes, and the results fold into one toast that counts ("3 results ready"). */
  completed: (job: Job) => void;
  /** It failed: its progress toast goes, an error says why. */
  failed: (job: Job) => void;
  /** Its owner stopped it: the progress toast goes and a quiet note says nothing was billed. */
  cancelled: (job: Job) => void;
}

/**
 * The notifications of the job queue, in the reader's language, on the platform's Toaster (@krizaka/ui/toast):
 * `progressToast` while a job runs, `groupedToast` when results land in a burst (a batch of images is one toast, not
 * a pile), `toast.error` when one fails. Nothing here knows the transport: the SSE hook calls these.
 */
export function useJobToasts(): JobToasts {
  const { t } = useTranslation();
  const router = useRouter();
  return useMemo<JobToasts>(() => {
    const q = t.insights.queue;
    const name = (job: Job) => jobLabel(t, job);
    return {
      started: (job) => {
        progressToast(fill(q.started, { name: name(job) }), {
          id: job.id,
          value: Math.max(0, (job.progress ?? 0) / 100),
        });
      },
      progressed: (job, percent) => {
        if (percent > 0 && percent < 100)
          progressToast(fill(q.started, { name: name(job) }), { id: job.id, value: percent / 100 });
      },
      completed: (job) => {
        toast.dismiss(job.id);
        groupedToast("jobs-ready", fill(q.ready, { name: name(job) }), {
          countLabel: (count) => fill(q.readyMany, { count }),
          action: { label: t.showcase.jobs.result, onClick: () => router.push(`/jobs?jobId=${job.id}`) },
        });
      },
      cancelled: (job) => {
        toast.dismiss(job.id);
        toast.info(fill(t.runProgress.cancelled, { name: name(job) }));
      },
      failed: (job) => {
        toast.dismiss(job.id);
        toast.error(fill(q.failed, { name: name(job), reason: job.errorMessage || q.unknownError }), {
          duration: 8000,
        });
      },
    };
  }, [t, router]);
}
