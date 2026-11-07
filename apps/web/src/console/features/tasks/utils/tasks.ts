import type { JobRecord, JobStatus } from "@krizaka/orazaka-shared";
import type { AuditRow } from "@/console/features/accounts/utils/audit";

/** One thing an operator has to look at. */
export interface OperatorTask {
  key: string;
  kind: "action" | "job";
  at: string;
  title: string;
  /** Why it is a task, in the platform's own words (the refusal code, the job's error). */
  why: string;
  /** The request to follow in the Audit log, when there is one. */
  requestId: string;
  source: string;
}

const FAILED: JobStatus = "FAILED";
const DAY = 24 * 3600 * 1000;
const ACTION_WINDOW = 7 * DAY;
const JOB_WINDOW = DAY;

/**
 * The operator's queue, derived from what the services already emit (ADR-085) and never stored: a refused or failed operator
 * action of the last 7 days with no later success on the same action and target, and a failed job of the last day. A task
 * closes by itself when its follow-up arrives. Newest first.
 */
export function openTasks(audit: readonly AuditRow[], jobs: readonly JobRecord[], now: number): OperatorTask[] {
  const tasks: OperatorTask[] = [];
  for (const line of audit) {
    if (line.outcome === "ok" || now - Date.parse(line.at) > ACTION_WINDOW) continue;
    const mended = audit.some(
      (other) =>
        other.outcome === "ok" &&
        other.source === line.source &&
        other.action === line.action &&
        other.targetId === line.targetId &&
        other.at > line.at,
    );
    if (mended) continue;
    tasks.push({
      key: line.key,
      kind: "action",
      at: line.at,
      title: line.action,
      why: line.detail ? `${line.outcome}: ${line.detail}` : line.outcome,
      requestId: line.requestId,
      source: line.source,
    });
  }
  for (const job of jobs) {
    if (job.status !== FAILED || now - Date.parse(job.updatedAt) > JOB_WINDOW) continue;
    const error =
      typeof (job.result as Record<string, unknown> | null)?.error === "string"
        ? String((job.result as Record<string, unknown>).error)
        : "failed";
    tasks.push({
      key: `job:${job.id}`,
      kind: "job",
      at: job.updatedAt,
      title: job.featureKey,
      why: error,
      requestId: "",
      source: "jobs",
    });
  }
  return tasks.sort((a, b) => (a.at < b.at ? 1 : a.at > b.at ? -1 : 0));
}
