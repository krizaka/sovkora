"use client";

import { useCallback, useMemo, useState } from "react";
import { toast, undoToast } from "@krizaka/ui/toast";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { jobLabel } from "@/core/jobs/jobMedia";
import { JobsApi } from "@/services/jobs.api";
import type { Job } from "@/core/types/jobs.types";

const RUNNING = ["PENDING", "PROCESSING"];

export interface JobDeletion {
  /** The job the person is being asked about, or null. */
  asking: Job | null;
  /** Ids hidden from every list while their deletion waits out the undo window. */
  hidden: ReadonlySet<string>;
  /** The trash icon of a row. A running job is refused with a word, not asked about. */
  request: (job: Job) => void;
  /** The dialog's Cancel. */
  dismiss: () => void;
  /** The dialog's Delete: the row leaves the list at once, the files only when the undo window ends. */
  confirm: () => void;
}

/**
 * Deleting a result (orazaka-web-client#86): a confirm dialog, then an optimistic removal with a kit undo toast (6 s).
 * Only when the toast goes without an undo does the DELETE reach the server — so an undo is free and a failure puts the
 * row back with the reason. Closing the page inside the window deletes nothing: nothing is ever lost by accident.
 *
 * @param onDone - Runs after the server deleted (refresh the lists).
 */
export function useJobDeletion(onDone: () => void): JobDeletion {
  const { t } = useTranslation();
  const [asking, setAsking] = useState<Job | null>(null);
  const [hidden, setHidden] = useState<ReadonlySet<string>>(new Set());

  const unhide = useCallback((id: string) => {
    setHidden((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  }, []);

  const request = useCallback(
    (job: Job) => {
      if (RUNNING.includes(job.status)) {
        toast.warning(t.runProgress.deleteDialog.running);
        return;
      }
      setAsking(job);
    },
    [t],
  );

  const confirm = useCallback(() => {
    const job = asking;
    if (!job) return;
    setAsking(null);
    const d = t.runProgress.deleteDialog;
    const name = jobLabel(t, job);
    setHidden((prev) => new Set(prev).add(job.id));
    undoToast(fill(d.done, { name }), {
      undoLabel: d.undo,
      onUndo: () => unhide(job.id),
      onCommit: () => {
        JobsApi.deleteJob(job.id)
          .then(onDone)
          .catch((error: unknown) => {
            unhide(job.id);
            toast.error(fill(d.failed, { name, reason: error instanceof Error ? error.message : "" }));
          });
      },
    });
  }, [asking, t, unhide, onDone]);

  const dismiss = useCallback(() => setAsking(null), []);

  return useMemo(() => ({ asking, hidden, request, dismiss, confirm }), [asking, hidden, request, dismiss, confirm]);
}
