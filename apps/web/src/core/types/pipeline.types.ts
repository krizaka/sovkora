/**
 * @file pipeline.ts
 * @description Types + helpers for the real interceptor pipeline schema the
 * Router pushes as the SSE `pipeline-ack` event before the LLM starts (see
 * ChatStreamController.emitPipelineAck / AdvancedPipelineSchema). This is the
 * actual interceptor chain that processed the request — not a simulation.
 */

/** Mirror of the backend AdvancedPipelineSchema (Early-Ack SSE payload). */
export interface ChatPipelineSchema {
  readonly pipelineId: string;
  readonly coreInterceptorIds: readonly string[];
  readonly dynamicInterceptorIds: readonly string[];
  readonly estimatedLatencyMs: number;
}

/** A single, display-ready pipeline step. */
export interface PipelineStep {
  readonly id: string;
  readonly label: string;
  readonly phase: "core" | "dynamic";
}

/** Type guard: does an arbitrary SSE-parsed object look like a pipeline schema? */
export function isPipelineSchema(value: unknown): value is ChatPipelineSchema {
  return (
    typeof value === "object" &&
    value !== null &&
    Array.isArray((value as { coreInterceptorIds?: unknown }).coreInterceptorIds)
  );
}

/**
 * Turns an interceptor class id (e.g. "SemanticRouterInterceptor") into a
 * human-readable label ("Semantic Router"). Deterministic formatting only — no
 * invented names; the source ids come from the backend schema.
 */
export function humanizeInterceptor(id: string): string {
  const cleaned = id
    .replace(/Interceptor$/, "")
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/([A-Z]+)([A-Z][a-z])/g, "$1 $2")
    .trim();
  return cleaned || id;
}

/** Flattens a schema into ordered, display-ready steps (core first, then dynamic). */
export function toPipelineSteps(schema: ChatPipelineSchema): PipelineStep[] {
  const steps: PipelineStep[] = [];
  for (const id of schema.coreInterceptorIds) {
    steps.push({ id, label: humanizeInterceptor(id), phase: "core" });
  }
  for (const id of schema.dynamicInterceptorIds) {
    steps.push({ id, label: humanizeInterceptor(id), phase: "dynamic" });
  }
  return steps;
}
