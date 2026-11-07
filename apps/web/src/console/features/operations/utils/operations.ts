import type { JobRecord, JobStatus } from "@krizaka/orazaka-shared";
import type { Health } from "@/console/services/platform.schemas";

export type QueueDepths = Record<JobStatus, number>;

/** Every state the queue shows, in reading order. A `Record<JobStatus, …>` forces a new contract state to be added here. */
export const QUEUE_STATES = [
  "PENDING",
  "PROCESSING",
  "COMPLETED",
  "FAILED",
  "CANCELLED",
  "DELETED",
] as const satisfies readonly JobStatus[];

/** How many jobs wait, run, ended and failed: the queue's depth as an operator reads it. */
export function queueDepths(jobs: readonly JobRecord[]): QueueDepths {
  const depths: QueueDepths = { PENDING: 0, PROCESSING: 0, COMPLETED: 0, FAILED: 0, CANCELLED: 0, DELETED: 0 };
  for (const job of jobs) depths[job.status] += 1;
  return depths;
}

/** The latest failures with their message, newest first. */
export function recentFailures(jobs: readonly JobRecord[], limit = 20): JobRecord[] {
  return jobs
    .filter((job) => job.status === "FAILED")
    .sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt))
    .slice(0, limit);
}

/** Seconds from request to the last update; null while the job has not ended or a date is unreadable. */
export function jobSeconds(job: Pick<JobRecord, "status" | "createdAt" | "updatedAt">): number | null {
  if (job.status !== "COMPLETED" && job.status !== "FAILED") return null;
  const seconds = (Date.parse(job.updatedAt) - Date.parse(job.createdAt)) / 1000;
  return Number.isFinite(seconds) && seconds >= 0 ? Math.round(seconds) : null;
}

export type CheckTone = "success" | "danger" | "warning";
export interface DependencyCheck {
  id: "database" | "vector" | "ollama" | "provider";
  ok: boolean;
  tone: CheckTone;
  detail: string;
}

/**
 * The dependencies of the conversation service, one line each. The database check is implied by the vector one (the
 * service reads the extension through its connection); the default provider is a warning, not a failure, when it is not
 * the sovereign one.
 */
export function dependencyChecks(health: Health): DependencyCheck[] {
  return [
    {
      id: "database",
      ok: health.pgVectorConnected,
      tone: health.pgVectorConnected ? "success" : "danger",
      detail: health.pgVectorConnected ? "connected" : "unreachable",
    },
    {
      id: "vector",
      ok: health.pgVectorExtensionInstalled,
      tone: health.pgVectorExtensionInstalled ? "success" : "danger",
      detail: health.pgVectorExtensionInstalled ? "pgvector installed" : "pgvector missing",
    },
    {
      id: "ollama",
      ok: health.ollamaConnected,
      tone: health.ollamaConnected ? "success" : "danger",
      detail: health.ollamaConnected ? `${health.ollamaModels.length} models installed` : "unreachable",
    },
    {
      id: "provider",
      ok: health.defaultProvider.toLowerCase() === "ollama",
      tone: health.defaultProvider.toLowerCase() === "ollama" ? "success" : "warning",
      detail: health.defaultProvider,
    },
  ];
}
