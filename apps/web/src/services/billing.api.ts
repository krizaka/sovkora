import { restRequest } from "@/services/rest-client";
import {
  CostEstimateSchema,
  EntitlementSnapshotSchema,
  PackSubscriptionSchema,
  PlanSchema,
  WalletSchema,
  type CostEstimate,
  type EntitlementSnapshot,
  type PackSubscription,
  type Plan,
  type Wallet,
} from "@krizaka/orazaka-shared";

/**
 * Stateless adapter for the billing surface.
 *
 * Every response is parsed rather than cast. A balance the UI shows is a number
 * the user makes spending decisions against, so "the server said so" is not a
 * strong enough guarantee — a shape change should fail loudly here rather than
 * render as `NaN credits` three components away.
 *
 * No Authorization header: the BFF proxy injects the session token server-side
 * (AGENTS.md §8 — the browser never reaches :8095).
 */
export const BillingApi = {
  /**
   * The signed-in user's balances.
   *
   * @returns their wallet, or `null` when they have never been billed — which is
   *   not an error, just an actor with no ledger history yet.
   */
  fetchWallet: async (): Promise<Wallet | null> => {
    const data = await restRequest<unknown>("/api/v1/billing/wallets/me");
    if (!data) {
      return null;
    }
    return WalletSchema.parse(data);
  },

  /** What the signed-in user's plan permits, plus their available balance. */
  fetchEntitlements: async (): Promise<EntitlementSnapshot | null> => {
    const data = await restRequest<unknown>("/api/v1/billing/wallets/me/entitlements");
    return data ? EntitlementSnapshotSchema.parse(data) : null;
  },

  /**
   * What an action is expected to cost, before the user commits to it.
   *
   * @param capability - the capability about to run
   * @param modelName - the resolved model, when one is chosen
   */
  estimate: async (capability: string, modelName?: string | null): Promise<CostEstimate | null> => {
    const query = new URLSearchParams({ capability });
    if (modelName) {
      query.set("modelName", modelName);
    }
    const data = await restRequest<unknown>(`/api/v1/billing/pricebook/estimate?${query.toString()}`);
    return data ? CostEstimateSchema.parse(data) : null;
  },

  /** The public catalogue, for the upgrade path out of a paywall. */
  fetchPlans: async (): Promise<Plan[]> => {
    const data = await restRequest<unknown[]>("/api/v1/billing/plans");
    return (data ?? []).map((plan) => PlanSchema.parse(plan));
  },

  // No fetchPacks here any more. The marketplace's catalogue — names, shelves, icons,
  // bundles and prices — is served by StudioApi.fetchPacks since ADR-036; billing owns
  // only who *holds* a pack, which is what the calls below answer.

  /** The packs the signed-in user already holds. */
  fetchMyPacks: async (): Promise<PackSubscription[]> => {
    const data = await restRequest<unknown[]>("/api/v1/billing/pack-subscriptions/me");
    return (data ?? []).map((held) => PackSubscriptionSchema.parse(held));
  },

  /**
   * Adds a pack to the signed-in user.
   *
   * The actor is resolved server-side from the session, never sent from here — this
   * call carries only which pack, because "for whom" is not the browser's to assert.
   *
   * @param packKey - the pack to add
   */
  subscribeToPack: async (packKey: string): Promise<PackSubscription> =>
    PackSubscriptionSchema.parse(
      await restRequest<unknown>(`/api/v1/billing/pack-subscriptions/me/${encodeURIComponent(packKey)}`, {
        method: "POST",
      }),
    ),

  /**
   * Removes a pack from the signed-in user.
   *
   * @param packKey - the pack to remove
   */
  removePack: async (packKey: string): Promise<void> => {
    await restRequest<void>(`/api/v1/billing/pack-subscriptions/me/${encodeURIComponent(packKey)}`, {
      method: "DELETE",
    });
  },
} as const;
