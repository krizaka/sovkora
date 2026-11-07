import {
  BlueprintVersionSchema,
  StudioDetailSchema,
  StudioSummarySchema,
  type BlueprintVersion,
  type StudioDetail,
  type StudioSummary,
} from "@krizaka/orazaka-shared";

/**
 * Stateless adapter for the Studio authoring surface (ADR-034 §12.4).
 *
 * Everything goes through the BFF proxy at `/api/v1/**`, never to :8096 — the
 * browser has no route to the studio service and should not acquire one
 * (AGENTS.md §8).
 *
 * This console is what makes "a new profession ships with zero code" true: an
 * admin writes a blueprint here and publishes it. Responses are parsed rather
 * than cast, because a silently-wrong shape is a broken Studio in front of every
 * user who installs it.
 */

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    // The server's message is the product here: it names the offending step of an
    // invalid graph, and flattening it to "400" would throw away the only thing
    // that tells the author what to fix.
    throw new Error(detail || `${response.status} ${response.statusText}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export interface StudioUsageRow {
  readonly studioKey: string;
  readonly label: string;
  readonly installations: number;
  readonly runs: number;
  readonly succeeded: number;
  readonly failed: number;
  readonly successRate: number;
  readonly estimatedCredits: number;
  readonly installedButUnused: boolean;
}

export interface BlueprintDraft {
  readonly definition: string;
  readonly inputSchema: string;
  readonly configSchema: string;
  readonly estimatedCredits: number;
  readonly changelog: string;
}

export const StudioAdminApi = {
  /** Every Studio in the catalogue. */
  listStudios: async (): Promise<StudioSummary[]> => {
    const data = await request<unknown[]>("/api/v1/studios");
    return (data ?? []).map((studio) => StudioSummarySchema.parse(studio));
  },

  /** One Studio, including the schemas its current version publishes. */
  studioDetail: async (studioKey: string): Promise<StudioDetail> => {
    const data = await request<unknown>(`/api/v1/studios/${encodeURIComponent(studioKey)}`);
    return StudioDetailSchema.parse(data);
  },

  /** Every version of a Studio, drafts included — the Builder's version list. */
  listVersions: async (studioKey: string): Promise<BlueprintVersion[]> => {
    const data = await request<unknown[]>(`/api/v1/studios/${encodeURIComponent(studioKey)}/blueprints`);
    return (data ?? []).map((version) => BlueprintVersionSchema.parse(version));
  },

  /**
   * Creates or replaces a draft version.
   *
   * The server validates the graph on write, so an invalid blueprint is refused
   * while the author is still looking at it rather than at publish time.
   */
  saveDraft: async (studioKey: string, version: string, draft: BlueprintDraft): Promise<void> => {
    await request<void>(`/api/v1/studios/${encodeURIComponent(studioKey)}/blueprints/${encodeURIComponent(version)}`, {
      method: "PUT",
      body: JSON.stringify(draft),
    });
  },

  /** Publishes a draft, minting it as the Studio's latest version. */
  publish: async (studioKey: string, version: string): Promise<void> => {
    await request<void>(
      `/api/v1/studios/${encodeURIComponent(studioKey)}/blueprints/${encodeURIComponent(version)}/publish`,
      { method: "POST" },
    );
  },

  /** Deprecates a version without deleting it — runs still reference what they ran. */
  deprecate: async (studioKey: string, version: string): Promise<void> => {
    await request<void>(`/api/v1/studios/${encodeURIComponent(studioKey)}/blueprints/${encodeURIComponent(version)}`, {
      method: "DELETE",
    });
  },

  /** How the marketplace is actually performing — installs, runs, success rate. */
  analytics: async (): Promise<StudioUsageRow[]> => {
    return request<StudioUsageRow[]>("/api/v1/studios/analytics");
  },

  /** The capability keys a blueprint step may name, for the feature-key picker. */
  featureKeys: async (): Promise<string[]> => {
    const data = await request<Array<{ id?: string; featureKey?: string }>>("/api/v1/features/all");
    return (data ?? []).map((feature) => feature.featureKey ?? feature.id ?? "").filter((key) => key !== "");
  },
} as const;
