import { restRequest } from "@/services/rest-client";
import {
  AutomationRunSchema,
  AutomationSchema,
  TriggerSchema,
  type Automation,
  type AutomationRun,
  type Trigger,
} from "@krizaka/orazaka-shared";

const BASE = "/api/v1/automations";

/** What the create form sends. */
export interface NewAutomation {
  name: string;
  studioKey: string;
  inputs: Record<string, unknown>;
}

/** A trigger to add: a signed webhook, or a daily slot in a time zone. */
export type NewTrigger =
  { kind: "webhook" } | { kind: "schedule"; time: string; zone: string; days: string[]; catchUp: "once" | "skip" };

const path = (...parts: string[]) => [BASE, ...parts.map(encodeURIComponent)].join("/");

/**
 * Stateless adapter for the automation API (ADR-084): automations, their triggers and their runs.
 *
 * Every response is parsed against the shared contract, never cast. No Authorization header: the BFF proxy injects the
 * session token server-side (AGENTS.md §8). A webhook trigger's `secret` is in the answer of `addTrigger` and
 * `rotateTrigger` only — shown once by the caller, never stored by this adapter.
 */
export const AutomationApi = {
  /** The caller's automations, oldest first. */
  list: async (): Promise<Automation[]> =>
    ((await restRequest<unknown[]>(BASE)) ?? []).map((row) => AutomationSchema.parse(row)),

  create: async (draft: NewAutomation): Promise<Automation> =>
    AutomationSchema.parse(await restRequest<unknown>(BASE, { method: "POST", body: draft })),

  /** Changes a name, the fixed inputs or the enabled state; a missing field is left as it is. */
  update: async (
    id: string,
    patch: Partial<Pick<NewAutomation, "name" | "inputs">> & { enabled?: boolean },
  ): Promise<Automation> =>
    AutomationSchema.parse(await restRequest<unknown>(path(id), { method: "PATCH", body: patch })),

  remove: async (id: string): Promise<void> => {
    await restRequest<unknown>(path(id), { method: "DELETE" });
  },

  triggers: async (id: string): Promise<Trigger[]> =>
    ((await restRequest<unknown[]>(path(id, "triggers"))) ?? []).map((row) => TriggerSchema.parse(row)),

  addTrigger: async (id: string, trigger: NewTrigger): Promise<Trigger> =>
    TriggerSchema.parse(await restRequest<unknown>(path(id, "triggers"), { method: "POST", body: trigger })),

  setTriggerEnabled: async (id: string, triggerId: string, enabled: boolean): Promise<Trigger> =>
    TriggerSchema.parse(
      await restRequest<unknown>(path(id, "triggers", triggerId), { method: "PATCH", body: { enabled } }),
    ),

  /** A new secret, in the answer only; the previous one keeps verifying for a grace period. */
  rotateTrigger: async (id: string, triggerId: string): Promise<Trigger> =>
    TriggerSchema.parse(await restRequest<unknown>(path(id, "triggers", triggerId, "rotate"), { method: "POST" })),

  removeTrigger: async (id: string, triggerId: string): Promise<void> => {
    await restRequest<unknown>(path(id, "triggers", triggerId), { method: "DELETE" });
  },

  /** The runs of one automation, newest first. */
  runs: async (id: string, limit = 50): Promise<AutomationRun[]> =>
    ((await restRequest<unknown[]>(`${path(id, "runs")}?limit=${limit}`)) ?? []).map((row) =>
      AutomationRunSchema.parse(row),
    ),
} as const;
