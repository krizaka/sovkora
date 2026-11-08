import {
  ActorConsumptionSchema,
  CatalogModelSchema,
  CapabilityUsageSchema,
  MarginPreviewSchema,
  PackSchema,
  PlanSchema,
  PricebookRateSchema,
  SubscriptionSchema,
  WalletSchema,
  type ActorConsumption,
  type CapabilityUsage,
  type CatalogModel,
  type CreditBucket,
  type MarginPreview,
  type Pack,
  type Plan,
  type PricebookRate,
  type Subscription,
  type Wallet,
} from "@krizaka/orazaka-shared";

/**
 * Stateless adapter for the billing admin surface.
 *
 * Everything goes through the BFF proxy at `/api/v1/**`, never to :8095 — the
 * browser has no route to the billing service and should not acquire one
 * (AGENTS.md §8).
 *
 * Responses are parsed rather than cast. This console publishes prices and moves
 * balances; a silently-wrong shape here is a wrong number in front of someone
 * about to act on it.
 */

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    throw new Error(`${response.status} ${response.statusText}: ${detail}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** One whitelisted runtime setting, as the server declares it. */
export interface ConfigKeySpec {
  key: string;
  valueType: string;
  allowedValues: string[];
  defaultValue: string;
}

export const BillingAdminApi = {
  // ── Plans & packs ────────────────────────────────────────────────────────

  listPlans: async (includeInactive = true): Promise<Plan[]> => {
    const data = await request<unknown[]>(`/api/v1/billing/plans?includeInactive=${includeInactive}`);
    return data.map((plan) => PlanSchema.parse(plan));
  },

  savePlan: async (plan: Plan): Promise<Plan> =>
    PlanSchema.parse(
      await request<unknown>(`/api/v1/billing/plans/${plan.planKey}`, {
        method: "PUT",
        body: JSON.stringify(plan),
      }),
    ),

  retirePlan: (planKey: string): Promise<void> =>
    request<void>(`/api/v1/billing/plans/${planKey}`, { method: "DELETE" }),

  listPacks: async (includeInactive = true): Promise<Pack[]> => {
    const data = await request<unknown[]>(`/api/v1/billing/packs?includeInactive=${includeInactive}`);
    return data.map((pack) => PackSchema.parse(pack));
  },

  savePack: async (pack: Pack): Promise<Pack> =>
    PackSchema.parse(
      await request<unknown>(`/api/v1/billing/packs/${pack.packKey}`, {
        method: "PUT",
        body: JSON.stringify(pack),
      }),
    ),

  withdrawPack: (packKey: string): Promise<void> =>
    request<void>(`/api/v1/billing/packs/${packKey}`, { method: "DELETE" }),

  // ── Pricebook ────────────────────────────────────────────────────────────

  listRates: async (): Promise<PricebookRate[]> => {
    const data = await request<unknown[]>("/api/v1/billing/pricebook");
    return data.map((rate) => PricebookRateSchema.parse(rate));
  },

  /**
   * Replays a proposed rate against the last 30 days.
   *
   * Separate from publishing on purpose: a preview folded into the publish
   * response would arrive after the decision it exists to inform (design §12).
   */
  previewRate: async (rate: Omit<PricebookRate, "version">): Promise<MarginPreview> =>
    MarginPreviewSchema.parse(
      await request<unknown>("/api/v1/billing/pricebook/preview", {
        method: "POST",
        body: JSON.stringify(rate),
      }),
    ),

  publishRate: async (rate: Omit<PricebookRate, "version">): Promise<PricebookRate> =>
    PricebookRateSchema.parse(
      await request<unknown>("/api/v1/billing/pricebook", {
        method: "POST",
        body: JSON.stringify(rate),
      }),
    ),

  // ── Wallets & adjustments ────────────────────────────────────────────────

  fetchWallet: async (actorId: string): Promise<Wallet> =>
    WalletSchema.parse(await request<unknown>(`/api/v1/billing/wallets/${actorId}`)),

  /**
   * Moves credits by hand. Signed, so a claw-back is the same call as a grant.
   *
   * The reason is mandatory server-side and required here too, rather than being
   * left to a server error: an admin should learn the rule from the form, not
   * from a rejected submission.
   */
  adjust: async (actorId: string, bucket: CreditBucket, amount: number, reason: string): Promise<Wallet> => {
    if (!reason.trim()) {
      throw new Error("A reason is required for every manual adjustment");
    }
    return WalletSchema.parse(
      await request<unknown>(`/api/v1/billing/wallets/${actorId}/adjustments`, {
        method: "POST",
        body: JSON.stringify({ bucket, amount, reason }),
      }),
    );
  },

  adjustedToday: (): Promise<number> => request<number>("/api/v1/billing/wallets/adjustments/today"),

  // ── Subscriptions ────────────────────────────────────────────────────────

  fetchSubscription: async (actorId: string): Promise<Subscription | null> => {
    try {
      return SubscriptionSchema.parse(await request<unknown>(`/api/v1/billing/subscriptions/${actorId}`));
    } catch {
      return null;
    }
  },

  changePlan: async (actorId: string, planKey: string, status = "ACTIVE"): Promise<Subscription> =>
    SubscriptionSchema.parse(
      await request<unknown>(`/api/v1/billing/subscriptions/${actorId}`, {
        method: "POST",
        body: JSON.stringify({ planKey, status }),
      }),
    ),

  cancel: (actorId: string, immediately: boolean): Promise<Subscription> =>
    request(`/api/v1/billing/subscriptions/${actorId}?immediately=${immediately}`, {
      method: "DELETE",
    }),

  // ── Usage analytics ──────────────────────────────────────────────────────

  usageByCapability: async (days = 30): Promise<CapabilityUsage[]> => {
    const data = await request<unknown[]>(`/api/v1/billing/usage/capabilities?days=${days}`);
    return data.map((row) => CapabilityUsageSchema.parse(row));
  },

  topConsumers: async (days = 30, limit = 20): Promise<ActorConsumption[]> => {
    const data = await request<unknown[]>(`/api/v1/billing/usage/top-consumers?days=${days}&limit=${limit}`);
    return data.map((row) => ActorConsumptionSchema.parse(row));
  },

  // ── Model catalogue ──────────────────────────────────────────────────────

  /**
   * Every AI model the platform can run.
   *
   * Served by the model catalogue, not by billing: the two are separate bounded
   * contexts with separate databases, and billing has no business holding a copy
   * of the model list. The console joins them, which is what a BFF-backed console
   * is for.
   */
  listModels: async (): Promise<CatalogModel[]> => {
    const data = await request<unknown[]>("/api/v1/models/catalog");
    return data.map((model) => CatalogModelSchema.parse(model));
  },

  // ── Runtime settings ─────────────────────────────────────────────────────

  /**
   * The declared key vocabulary — type, permitted domain and default per key.
   *
   * The console renders its inputs from this rather than hardcoding the keys, so
   * a new setting appears without a front-end change and a value outside its
   * domain is refused by the same whitelist the server enforces (design §12.2).
   */
  fetchSettings: (): Promise<Record<string, ConfigKeySpec>> =>
    request<Record<string, ConfigKeySpec>>("/api/v1/billing/configuration"),

  updateSetting: (key: string, value: string): Promise<void> =>
    request<void>("/api/v1/billing/configuration", {
      method: "PATCH",
      body: JSON.stringify({ key, value }),
    }),
} as const;
