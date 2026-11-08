"use client";

import { CheckIcon, CopyIcon, DownloadIcon, EyeIcon, TrashIcon } from "@krizaka/icons";
import { IconButton } from "@krizaka/ui/button";
import { JOB_STATUS } from "@/core/constants/http.constants";
import { jobMediaUrl } from "@/core/jobs/jobMedia";
import type { Job } from "@/core/types/jobs.types";
import type { JobsTableProps } from "./jobsTableShape";

export type ActionLabels = {
  open: string;
  payload: string;
  download: string;
  copied: string;
  copyId: string;
  delete: string;
};

export function JobActionsCell({
  job,
  labels,
  copiedId,
  onOpenModal,
  onCopy,
  onDelete,
}: Readonly<{
  job: Job;
  labels: ActionLabels;
  copiedId: string | null;
  onOpenModal: JobsTableProps["onOpenModal"];
  onCopy: JobsTableProps["onCopy"];
  onDelete: JobsTableProps["onDelete"];
}>) {
  const url = jobMediaUrl(job);
  return (
    <span className="inline-flex items-center gap-0.5">
      <IconButton
        variant="ghost"
        size="sm"
        label={job.status === JOB_STATUS.COMPLETED ? labels.open : labels.payload}
        onClick={() => onOpenModal(job, job.status === JOB_STATUS.COMPLETED ? "result" : "payload")}
      >
        <EyeIcon size={15} />
      </IconButton>
      {url && (
        <IconButton variant="ghost" size="sm" label={labels.download} asChild>
          <a href={url} download>
            <DownloadIcon size={15} />
          </a>
        </IconButton>
      )}
      <IconButton
        variant="ghost"
        size="sm"
        label={copiedId === job.id ? labels.copied : labels.copyId}
        onClick={() => onCopy(job.id, job.id)}
      >
        {copiedId === job.id ? <CheckIcon size={15} /> : <CopyIcon size={15} />}
      </IconButton>
      {onDelete && (
        <IconButton variant="ghost" size="sm" label={labels.delete} onClick={() => onDelete(job)}>
          <TrashIcon size={15} />
        </IconButton>
      )}
    </span>
  );
}
