import { Icon } from "@krizaka/orazaka-design-system";
import { JobStatus } from "@/core/types/jobs.types";
import { JOB_STATUS } from "@/core/constants/http.constants";

/** Pill badge for a job's status (icon + label, theme-token colored). */
export function JobStatusBadge({ status }: Readonly<{ status: JobStatus }>) {
  switch (status) {
    case JOB_STATUS.PENDING:
      return (
        <span className="inline-flex items-center px-2.5 py-1 rounded-sm text-[11px] font-semibold bg-surface-2 text-fg-secondary border border-border-subtle">
          <Icon name="timer" className="w-3.5 h-3.5 mr-1 text-fg-muted" />
          PENDING
        </span>
      );
    case JOB_STATUS.PROCESSING:
      return (
        <span className="inline-flex items-center px-2.5 py-1 rounded-sm text-[11px] font-semibold bg-accent/10 text-accent border border-accent/15 animate-pulse">
          <Icon name="loader" className="w-3.5 h-3.5 mr-1 text-accent animate-spin" />
          PROCESSING
        </span>
      );
    case JOB_STATUS.COMPLETED:
      return (
        <span className="inline-flex items-center px-2.5 py-1 rounded-sm text-[11px] font-semibold bg-success/10 text-success border border-success/15">
          <Icon name="checkCircle" className="w-3.5 h-3.5 mr-1 text-success" />
          COMPLETED
        </span>
      );
    case JOB_STATUS.FAILED:
      return (
        <span className="inline-flex items-center px-2.5 py-1 rounded-sm text-[11px] font-semibold bg-danger/10 text-danger border border-danger/15">
          <Icon name="warning" className="w-3.5 h-3.5 mr-1 text-danger" />
          FAILED
        </span>
      );
    default:
      return null;
  }
}
