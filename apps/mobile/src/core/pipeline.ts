/**
 * @file pipeline.ts
 * @description The interceptor chain of one chat request, as the router reports it — never invented.
 *
 * The router streams `pipeline-ack` (the chain that will run: `coreInterceptorIds` then `dynamicInterceptorIds`) before
 * the model answers, then one `pipeline-step` per interceptor that finished. The phone shows the steps that really ran.
 */

/** The ids a `pipeline-ack` payload lists, in the order they run; `[]` for anything else. */
export function pipelineOfAck(payload: unknown): string[] {
  if (typeof payload !== "object" || payload === null) return [];
  const { coreInterceptorIds, dynamicInterceptorIds } = payload as Record<string, unknown>;
  const ids = (value: unknown): string[] =>
    Array.isArray(value) ? value.filter((v): v is string => typeof v === "string") : [];
  return [...ids(coreInterceptorIds), ...ids(dynamicInterceptorIds)];
}

/** The id a `pipeline-step` payload names, or null. */
export function stepOf(payload: unknown): string | null {
  if (typeof payload !== "object" || payload === null) return null;
  const id = (payload as Record<string, unknown>).interceptorId;
  return typeof id === "string" && id !== "" ? id : null;
}

/**
 * `SemanticRouterInterceptor` → `Semantic Router` — for an interceptor the catalogue has no word for (a new one the
 * platform adds): deterministic spacing of the id, nothing invented.
 */
export function humanizeInterceptor(id: string): string {
  const cleaned = id
    .replace(/Interceptor$/, "")
    .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
    .replace(/([A-Z])([A-Z][a-z])/g, "$1 $2")
    .trim();
  return cleaned === "" ? id : cleaned;
}
