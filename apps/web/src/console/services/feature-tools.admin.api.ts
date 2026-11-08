import { z } from "zod";

/**
 * Stateless adapter for the per-feature engine settings of the conversation service (orazaka-ai-engine#169), through
 * the BFF (`/api/v1/**`, AGENTS.md §8): the most tool rounds a feature key may run in a turn, the most answers a structured-output call may ask for (#172), and the models each
 * feature routes to (read only). Responses are parsed: a screen that misreads a bound would show a wrong limit.
 */
export const ToolBoundSchema = z.object({
  featureKey: z.string(),
  maxToolRounds: z.number().int(),
  isDefault: z.boolean(),
});
export const ToolBoundsSchema = z.object({
  min: z.number().int(),
  max: z.number().int(),
  bounds: z.array(ToolBoundSchema),
});
export const StructuredBoundSchema = z.object({
  featureKey: z.string(),
  maxAttempts: z.number().int(),
  isDefault: z.boolean(),
});
export const StructuredBoundsSchema = z.object({
  min: z.number().int(),
  max: z.number().int(),
  bounds: z.array(StructuredBoundSchema),
});
export const CachePolicySchema = z.object({
  featureKey: z.string(),
  strategy: z.string(),
  isDefault: z.boolean(),
});
export const CachePoliciesSchema = z.object({
  strategies: z.array(z.string()),
  policies: z.array(CachePolicySchema),
});
export const FeatureRouteSchema = z.object({ featureKey: z.string(), models: z.array(z.string()) });

export type ToolBound = z.infer<typeof ToolBoundSchema>;
export type ToolBounds = z.infer<typeof ToolBoundsSchema>;
export type StructuredBound = z.infer<typeof StructuredBoundSchema>;
export type StructuredBounds = z.infer<typeof StructuredBoundsSchema>;
export type CachePolicy = z.infer<typeof CachePolicySchema>;
export type CachePolicies = z.infer<typeof CachePoliciesSchema>;
export type FeatureRoute = z.infer<typeof FeatureRouteSchema>;

/** A refusal of the server (400 `invalid-request`): its message is shown next to the field. */
export class FeatureToolsRefusal extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const BASE = "/api/v1/models/admin";

async function call(path: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(path, { ...init, headers: { "Content-Type": "application/json", ...init?.headers } });
  if (!response.ok) {
    const detail = (await response.json().catch(() => null)) as { detail?: unknown; message?: unknown } | null;
    const text = [detail?.detail, detail?.message].find((v): v is string => typeof v === "string");
    throw new FeatureToolsRefusal(response.status, text ?? `${response.status}`);
  }
  return response.status === 204 ? null : response.json();
}

export const FeatureToolsApi = {
  /** Every bound (the default row `*` first) and the range the server accepts. */
  bounds: async (): Promise<ToolBounds> => ToolBoundsSchema.parse(await call(`${BASE}/feature-tool-bounds`)),

  /** Sets the bound of a feature; it applies on the next turn, with no restart. */
  save: async (featureKey: string, maxToolRounds: number): Promise<ToolBound> =>
    ToolBoundSchema.parse(
      await call(`${BASE}/feature-tool-bounds/${encodeURIComponent(featureKey)}`, {
        method: "PUT",
        body: JSON.stringify({ maxToolRounds }),
      }),
    ),

  /** Removes a feature's own bound so it follows the default row. */
  remove: async (featureKey: string): Promise<void> => {
    await call(`${BASE}/feature-tool-bounds/${encodeURIComponent(featureKey)}`, { method: "DELETE" });
  },

  /** Every structured-output bound (the default row `*` first) and the range the server accepts. */
  structuredBounds: async (): Promise<StructuredBounds> =>
    StructuredBoundsSchema.parse(await call(`${BASE}/feature-structured-bounds`)),

  /** Sets the most answers a feature's structured calls may ask for; it applies on the next call. */
  saveStructured: async (featureKey: string, maxAttempts: number): Promise<StructuredBound> =>
    StructuredBoundSchema.parse(
      await call(`${BASE}/feature-structured-bounds/${encodeURIComponent(featureKey)}`, {
        method: "PUT",
        body: JSON.stringify({ maxAttempts }),
      }),
    ),

  /** Removes a feature's own structured bound so it follows the default row. */
  removeStructured: async (featureKey: string): Promise<void> => {
    await call(`${BASE}/feature-structured-bounds/${encodeURIComponent(featureKey)}`, { method: "DELETE" });
  },

  /** Every prompt-cache policy (the default row `*` first) and the strategies on offer (#173). */
  cachePolicies: async (): Promise<CachePolicies> =>
    CachePoliciesSchema.parse(await call(`${BASE}/feature-cache-policies`)),

  /** Sets what of a feature's prompt may be cached; it applies on the next turn. */
  saveCachePolicy: async (featureKey: string, strategy: string): Promise<CachePolicy> =>
    CachePolicySchema.parse(
      await call(`${BASE}/feature-cache-policies/${encodeURIComponent(featureKey)}`, {
        method: "PUT",
        body: JSON.stringify({ strategy }),
      }),
    ),

  /** Removes a feature's own policy so it follows the default row. */
  removeCachePolicy: async (featureKey: string): Promise<void> => {
    await call(`${BASE}/feature-cache-policies/${encodeURIComponent(featureKey)}`, { method: "DELETE" });
  },

  /** The feature routes: each feature key with its models, best first. */
  routes: async (): Promise<FeatureRoute[]> => z.array(FeatureRouteSchema).parse(await call(`${BASE}/feature-routes`)),
} as const;
