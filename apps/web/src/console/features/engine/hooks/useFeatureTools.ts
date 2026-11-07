"use client";

import { useCallback } from "react";
import { useQuery, useQueryClient } from "@krizaka/query";
import {
  FeatureToolsApi,
  FeatureToolsRefusal,
  type CachePolicies,
  type FeatureRoute,
  type StructuredBounds,
  type ToolBounds,
} from "@/console/services/feature-tools.admin.api";

const BOUNDS_KEY = ["engine", "feature-tool-bounds"] as const;
const STRUCTURED_KEY = ["engine", "feature-structured-bounds"] as const;
const CACHE_KEY = ["engine", "feature-cache-policies"] as const;
const ROUTES_KEY = ["engine", "feature-routes"] as const;

export interface FeatureTools {
  bounds: ToolBounds | undefined;
  /** The structured-output bounds; `undefined` while unread or when they could not be read. */
  structured: StructuredBounds | undefined;
  /** The prompt-cache policies; `undefined` while unread or when they could not be read. */
  cache: CachePolicies | undefined;
  routes: FeatureRoute[];
  isLoading: boolean;
  hasError: boolean;
  routesFailed: boolean;
  reload: () => void;
  /** Saves one bound; resolves to the server's refusal message, or `null` when it was accepted. */
  save: (featureKey: string, rounds: number) => Promise<string | null>;
  /** Removes a feature's own bound; same answer as `save`. */
  remove: (featureKey: string) => Promise<string | null>;
  /** Same two writes for the structured-output bound. */
  saveStructured: (featureKey: string, attempts: number) => Promise<string | null>;
  removeStructured: (featureKey: string) => Promise<string | null>;
  /** Same two writes for the prompt-cache policy. */
  saveCache: (featureKey: string, strategy: string) => Promise<string | null>;
  removeCache: (featureKey: string) => Promise<string | null>;
}

/** The refusal text of an admin write, `""` when the failure carried none (the screen then says its own words). */
const refusal = (error: unknown): string => (error instanceof FeatureToolsRefusal ? error.message : "");

/** The bounds and the routes, read once, and the writes that re-read the bounds so the list stays at the server's answer. */
export function useFeatureTools(): FeatureTools {
  const client = useQueryClient();
  const bounds = useQuery({ queryKey: BOUNDS_KEY, queryFn: () => FeatureToolsApi.bounds(), retry: false, gcTime: 0 });
  const routes = useQuery({ queryKey: ROUTES_KEY, queryFn: () => FeatureToolsApi.routes(), retry: false, gcTime: 0 });

  const structured = useQuery({
    queryKey: STRUCTURED_KEY,
    queryFn: () => FeatureToolsApi.structuredBounds(),
    retry: false,
    gcTime: 0,
  });

  const cache = useQuery({
    queryKey: CACHE_KEY,
    queryFn: () => FeatureToolsApi.cachePolicies(),
    retry: false,
    gcTime: 0,
  });

  const write = useCallback(
    async (queryKey: readonly string[], action: () => Promise<unknown>) => {
      try {
        await action();
        await client.invalidateQueries({ queryKey });
        return null;
      } catch (error) {
        return refusal(error);
      }
    },
    [client],
  );

  return {
    bounds: bounds.data,
    structured: structured.data,
    cache: cache.data,
    routes: routes.data ?? [],
    isLoading: bounds.isPending,
    hasError: bounds.isError,
    routesFailed: routes.isError,
    reload: () => {
      void bounds.refetch();
      void structured.refetch();
      void cache.refetch();
      void routes.refetch();
    },
    save: (featureKey, rounds) => write(BOUNDS_KEY, () => FeatureToolsApi.save(featureKey, rounds)),
    remove: (featureKey) => write(BOUNDS_KEY, () => FeatureToolsApi.remove(featureKey)),
    saveStructured: (featureKey, attempts) =>
      write(STRUCTURED_KEY, () => FeatureToolsApi.saveStructured(featureKey, attempts)),
    removeStructured: (featureKey) => write(STRUCTURED_KEY, () => FeatureToolsApi.removeStructured(featureKey)),
    saveCache: (featureKey, strategy) => write(CACHE_KEY, () => FeatureToolsApi.saveCachePolicy(featureKey, strategy)),
    removeCache: (featureKey) => write(CACHE_KEY, () => FeatureToolsApi.removeCachePolicy(featureKey)),
  };
}
