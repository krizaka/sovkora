import type { Job } from "@/core/types/jobs.types";

export interface JobModalProps {
  job: Job | null;
  modalType: "payload" | "result" | null;
  onClose: () => void;
  copiedId: string | null;
  onCopy: (text: string, id: string) => void;
}
