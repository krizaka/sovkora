import { JobPageSchema, type JobPage } from "@krizaka/orazaka-shared";
import { z } from "zod";
import {
  AdminAuditEntrySchema,
  AdminUserPageSchema,
  AdminUserSchema,
  CapabilitySchema,
  HealthSchema,
  InfrastructureSchema,
  ModelEntrySchema,
  type AdminAuditEntry,
  type AdminUser,
  type AdminUserPage,
  type Capability,
  type Health,
  type Infrastructure,
  type ModelEntry,
} from "./platform.schemas";
import type { BillingAuditLine } from "@/console/features/accounts/utils/audit";

/**
 * Stateless adapter for the job service's operator surface, through the BFF (`/api/v1/**`, AGENTS.md §8). Responses
 * are parsed (`@krizaka/orazaka-shared`): a supervision screen that misreads a shape shows a wrong health.
 */
const SERVICE_AUDIT = {
  billing: "/api/v1/billing/admin/audit",
  studio: "/api/v1/studios/admin/audit",
  engine: "/api/v1/models/admin/audit",
} as const;

async function read(path: string): Promise<unknown> {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.json();
}

/** A refusal of the rules carries a stable code the console words (`SELF`, `LAST_ADMIN`, `CONFIRM_MISMATCH`). */
export class AdminRefusal extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
  ) {
    super(code ?? String(status));
  }
}

async function write(path: string, method: "PUT" | "POST" | "DELETE", body?: unknown): Promise<unknown> {
  const response = await fetch(path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const detail = (await response.json().catch(() => ({}))) as { code?: unknown };
    throw new AdminRefusal(response.status, typeof detail.code === "string" ? detail.code : null);
  }
  return response.status === 204 ? null : response.json();
}

export const PlatformAdminApi = {
  /** The latest jobs of every workspace (an administrator's token lists them all), newest first. */
  fetchJobs: async (size = 200): Promise<JobPage> =>
    JobPageSchema.parse(await read(`/api/v1/jobs?page=0&size=${size}`)),

  /** How many job streams (SSE) are open right now: people watching their work. */
  activeConnections: async (): Promise<number> => {
    const value = Number(await read("/api/v1/jobs/active-connections"));
    return Number.isFinite(value) ? value : 0;
  },

  /** Readiness of the conversation service's dependencies (database, pgvector, Ollama, default provider). */
  health: async (): Promise<Health> => HealthSchema.parse(await read("/api/v1/status/health")),

  /** Whether the local video and image engines answer. */
  infrastructure: async (): Promise<Infrastructure> =>
    InfrastructureSchema.parse(await read("/api/v1/status/infrastructure")),

  /** The model registry (catalogue with host compatibility). */
  models: async (): Promise<ModelEntry[]> => z.array(ModelEntrySchema).parse(await read("/api/v1/models/catalog")),

  /** The providers the registry knows. */
  providers: async (): Promise<string[]> => z.array(z.string()).parse(await read("/api/v1/models/providers")),

  /** Every capability, enabled or not (administrator only). */
  capabilities: async (): Promise<Capability[]> => z.array(CapabilitySchema).parse(await read("/api/v1/features/all")),

  /** Enables or disables one capability: a disabled one is not dispatched. */
  setCapability: async (featureKey: string, isEnabled: boolean): Promise<Capability> =>
    CapabilitySchema.parse(await write(`/api/v1/features/${encodeURIComponent(featureKey)}`, "PUT", { isEnabled })),

  /** A page of accounts matching `query` (the identity service's administration). */
  users: async (query = "", page = 0, size = 25): Promise<AdminUserPage> =>
    AdminUserPageSchema.parse(
      await read(`/api/v1/admin/users?q=${encodeURIComponent(query)}&page=${page}&size=${size}`),
    ),

  suspendUser: async (id: string): Promise<AdminUser> =>
    AdminUserSchema.parse(await write(`/api/v1/admin/users/${encodeURIComponent(id)}/suspend`, "POST")),
  restoreUser: async (id: string): Promise<AdminUser> =>
    AdminUserSchema.parse(await write(`/api/v1/admin/users/${encodeURIComponent(id)}/restore`, "POST")),
  setAdmin: async (id: string, admin: boolean): Promise<AdminUser> =>
    AdminUserSchema.parse(await write(`/api/v1/admin/users/${encodeURIComponent(id)}/admin`, "PUT", { admin })),
  eraseUser: async (id: string, confirmEmail: string): Promise<void> => {
    await write(`/api/v1/admin/users/${encodeURIComponent(id)}`, "DELETE", { confirmEmail });
  },

  /** A service's operator audit (every operator write it saw), newest first: billing, the Studio service, the engine's registry. */
  serviceAudit: async (service: "billing" | "studio" | "engine", limit = 200): Promise<BillingAuditLine[]> =>
    z
      .array(
        z.object({
          id: z.number(),
          at: z.string(),
          actor: z.string(),
          action: z.string(),
          targetId: z
            .string()
            .nullish()
            .transform((v) => v ?? null),
          requestId: z
            .string()
            .nullish()
            .transform((v) => v ?? null),
          outcome: z.enum(["ok", "refused", "failed"]).catch("ok"),
        }),
      )
      .parse(await read(`${SERVICE_AUDIT[service]}?limit=${limit}`)),
  /** The administration's audit trail, newest first. */
  audit: async (limit = 200): Promise<AdminAuditEntry[]> =>
    z.array(AdminAuditEntrySchema).parse(await read(`/api/v1/admin/users/audit?limit=${limit}`)),
} as const;
