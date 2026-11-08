import type { Job, JobStatus } from "@/core/types/jobs.types";

export const PAGE_SIZES = [10, 20, 50] as const;
export const TONE: Record<JobStatus, "neutral" | "accent" | "success" | "danger"> = {
  PENDING: "neutral",
  PROCESSING: "accent",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "neutral",
  DELETED: "neutral",
};

export interface JobsTableProps {
  jobs: Job[];
  loading: boolean;
  totalElements: number;
  /** From 0. */
  currentPage: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onOpenModal: (job: Job, type: "payload" | "result") => void;
  copiedId: string | null;
  onCopy: (text: string, id: string) => void;
  /** Deletes a task and its result (orazaka-web-client#86). The action shows only when the page provides it. */
  onDelete?: (job: Job) => void;
}
