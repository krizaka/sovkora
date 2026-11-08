import { z } from "zod";

/**
 * Stateless adapter for the engine's runtime settings (studio admin API), through the BFF (`/api/v1/**`, AGENTS.md §8).
 * Responses are parsed: a screen that misreads a type would offer the wrong editor for a knob that runs the platform.
 */
const Typed = z.union([z.number(), z.boolean(), z.array(z.string())]);

export const RuntimeSettingSchema = z.object({
  key: z.string(),
  value: Typed,
  type: z.enum(["int", "boolean", "list"]),
  description: z.string().default(""),
  default: Typed,
  bounds: z
    .object({ minimum: z.number().nullish(), maximum: z.number().nullish(), maxItems: z.number().nullish() })
    .nullish()
    .transform((b) => b ?? {}),
});

export const RuntimeAuditSchema = z.object({
  at: z.string(),
  actorId: z.string().nullish(),
  key: z.string(),
  before: z.string().nullish(),
  after: z.string().nullish(),
});

export type SettingValue = z.infer<typeof Typed>;
export type RuntimeSetting = z.infer<typeof RuntimeSettingSchema>;
export type RuntimeAuditEntry = z.infer<typeof RuntimeAuditSchema>;

/** A refusal of the server (400 `invalid_request`): its `message` is shown next to the field. */
export class RuntimeConfigRefusal extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const BASE = "/api/v1/studios/admin/runtime-config";

async function call(path: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(path, { ...init, headers: { "Content-Type": "application/json", ...init?.headers } });
  if (!response.ok) {
    const detail = (await response.json().catch(() => null)) as { message?: unknown } | null;
    throw new RuntimeConfigRefusal(
      response.status,
      typeof detail?.message === "string" ? detail.message : `${response.status}`,
    );
  }
  return response.json();
}

export const RuntimeConfigApi = {
  /** Every setting with its current value, default and bounds. */
  list: async (): Promise<RuntimeSetting[]> => z.array(RuntimeSettingSchema).parse(await call(BASE)),

  /** Sets one value; it applies on the next read, with no restart. */
  update: async (key: string, value: SettingValue): Promise<RuntimeSetting> =>
    RuntimeSettingSchema.parse(
      await call(`${BASE}/${encodeURIComponent(key)}`, { method: "PUT", body: JSON.stringify({ value }) }),
    ),

  /** The change history of one key, newest first (actor ids only). */
  audit: async (key: string, limit = 50): Promise<RuntimeAuditEntry[]> =>
    z.array(RuntimeAuditSchema).parse(await call(`${BASE}/audit?key=${encodeURIComponent(key)}&limit=${limit}`)),
} as const;
