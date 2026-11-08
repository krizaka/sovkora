"use client";

import { useQuery } from "@tanstack/react-query";
import type { EntitlementSnapshot, Plan } from "@krizaka/orazaka-shared";
import { BillingApi } from "@/services/billing.api";

/** The plan behind the balance: its name and what it grants each period (null until billing answers). */
export function usePlan(enabled = true) {
  const snapshot = useQuery<EntitlementSnapshot | null>({
    queryKey: ["entitlements"],
    queryFn: () => BillingApi.fetchEntitlements(),
    enabled,
  });
  const plans = useQuery<Plan[]>({ queryKey: ["plans"], queryFn: () => BillingApi.fetchPlans(), enabled });
  const plan = plans.data?.find((p) => p.planKey === snapshot.data?.planKey) ?? null;
  return { plan, isLoading: snapshot.isLoading || plans.isLoading };
}
