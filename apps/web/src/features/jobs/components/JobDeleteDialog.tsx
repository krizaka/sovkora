"use client";

import { AlertDialog } from "@krizaka/ui/dialog";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { jobLabel } from "@/core/jobs/jobMedia";
import type { Job } from "@/core/types/jobs.types";

interface JobDeleteDialogProps {
  job: Job | null;
  onConfirm: () => void;
  onDismiss: () => void;
}

/** "Delete {name}?" — the kit's AlertDialog, in the reader's language. Cancel takes the focus; the toast that follows holds the undo. */
export function JobDeleteDialog({ job, onConfirm, onDismiss }: Readonly<JobDeleteDialogProps>) {
  const { t } = useTranslation();
  const d = t.runProgress.deleteDialog;
  return (
    <AlertDialog
      open={job !== null}
      onOpenChange={(open) => {
        if (!open) onDismiss();
      }}
      title={job ? fill(d.title, { name: jobLabel(t, job) }) : ""}
      description={d.description}
      confirmLabel={d.confirm}
      cancelLabel={d.cancel}
      tone="danger"
      onConfirm={onConfirm}
    />
  );
}
