/**
 * @file jobs.types.ts
 * @description TypeScript interface matching the backend JobDto record structure.
 */

export type JobStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "CANCELLED" | "DELETED";

/** What the executor said it is doing now (live stream only). */
export type JobPhase = "queued" | "loading" | "generating" | "encoding" | "uploading";

export interface Job {
  id: string;
  userId: string;
  featureKey: string;
  status: JobStatus;
  payload: Record<string, unknown>;
  result: Record<string, unknown>;
  errorMessage?: string;
  progress?: number;
  /** From the live stream: the phase, and step n of N while generating. */
  phase?: JobPhase;
  step?: number;
  steps?: number;
  createdAt: string; // ISO-8601 string
  updatedAt: string; // ISO-8601 string
}
