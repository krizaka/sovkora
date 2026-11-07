"use client";

import { useCallback, useEffect, useState } from "react";
import { BillingApi } from "@/services/billing.api";
import type { CostEstimate } from "@krizaka/orazaka-shared";

export interface CostEstimateState {
  estimate: CostEstimate | null;
  isLoading: boolean;
}

/**
 * What an action will cost, fetched before the user confirms it.
 *
 * The estimate comes from the pricebook rather than being computed here on
 * purpose: a client-side guess would be a second implementation of pricing, and
 * the first time it drifted the user would be quoted one number and charged
 * another.
 *
 * @param capability - the capability about to run, or `null` to skip the lookup
 * @param modelName - the resolved model, when the user has chosen one
 */
export function useCostEstimate(capability: string | null, modelName?: string | null): CostEstimateState {
  const [estimate, setEstimate] = useState<CostEstimate | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const load = useCallback(async () => {
    if (!capability) {
      setEstimate(null);
      return;
    }
    setIsLoading(true);
    try {
      setEstimate(await BillingApi.estimate(capability, modelName));
    } catch {
      // No estimate is better than a wrong one: the confirm step falls back to
      // showing no price rather than a number it cannot stand behind.
      setEstimate(null);
    } finally {
      setIsLoading(false);
    }
  }, [capability, modelName]);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  return { estimate, isLoading };
}
