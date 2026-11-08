"use client";

import { useCallback } from "react";
import { useQuery, useQueryClient } from "@krizaka/query";
import type { PricesView } from "@krizaka/ui/price-catalogue-editor";
import { PricesAdminApi } from "@/console/services/prices.admin.api";
import { useNow } from "@/core/hooks/useNow";
import { toHistory, toPricesView } from "@/console/features/billing/utils/prices";

const KEY = ["billing", "prices"] as const;

/** The catalogue in force with each key's scheduled versions, read once and re-read after a change is scheduled. */
export function usePrices() {
  const client = useQueryClient();
  const now = useNow(false);
  const query = useQuery<PricesView>({
    queryKey: KEY,
    queryFn: async () => {
      const current = await PricesAdminApi.current();
      const histories = Object.fromEntries(
        await Promise.all(current.map(async (p) => [p.key, await PricesAdminApi.history(p.key)] as const)),
      );
      return toPricesView(current, histories, now);
    },
    retry: false,
    gcTime: 0,
  });
  const loadHistory = useCallback(async (key: string) => toHistory(await PricesAdminApi.history(key)), []);
  const refresh = useCallback(() => client.invalidateQueries({ queryKey: KEY }), [client]);
  return {
    prices: query.data,
    isLoading: query.isPending,
    hasError: query.isError,
    reload: () => void query.refetch(),
    loadHistory,
    refresh,
  };
}
