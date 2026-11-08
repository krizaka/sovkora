"use client";

import { useCallback, useEffect, useState } from "react";
import { PlatformAdminApi } from "@/console/services/platform.admin.api";
import type { Capability, ModelEntry } from "@/console/services/platform.schemas";
import { withCapability } from "@/console/features/catalogue/utils/catalogue";

export interface Catalogue {
  models: ModelEntry[];
  providers: string[];
  capabilities: Capability[];
  isLoading: boolean;
  hasError: boolean;
  /** True when the last switch was refused; the list is back to the server's truth. */
  switchFailed: boolean;
  reload: () => void;
  toggle: (featureKey: string, enabled: boolean) => Promise<void>;
}

/** The registry's three lists, read in parallel (one failing never blanks the others), and the capability switch. */
export function useCatalogue(): Catalogue {
  const [state, setState] = useState({
    models: [] as ModelEntry[],
    providers: [] as string[],
    capabilities: [] as Capability[],
    isLoading: true,
    hasError: false,
    switchFailed: false,
  });

  const load = useCallback(async () => {
    setState((s) => ({ ...s, isLoading: true }));
    const [models, providers, capabilities] = await Promise.allSettled([
      PlatformAdminApi.models(),
      PlatformAdminApi.providers(),
      PlatformAdminApi.capabilities(),
    ]);
    setState({
      models: models.status === "fulfilled" ? models.value : [],
      providers: providers.status === "fulfilled" ? providers.value : [],
      capabilities: capabilities.status === "fulfilled" ? capabilities.value : [],
      isLoading: false,
      hasError: [models, providers, capabilities].some((r) => r.status === "rejected"),
      switchFailed: false,
    });
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const toggle = useCallback(async (featureKey: string, enabled: boolean) => {
    setState((s) => ({ ...s, switchFailed: false, capabilities: withCapability(s.capabilities, featureKey, enabled) }));
    try {
      const saved = await PlatformAdminApi.setCapability(featureKey, enabled);
      setState((s) => ({ ...s, capabilities: withCapability(s.capabilities, featureKey, saved.enabled) }));
    } catch {
      setState((s) => ({
        ...s,
        switchFailed: true,
        capabilities: withCapability(s.capabilities, featureKey, !enabled),
      }));
    }
  }, []);

  return { ...state, reload: () => void load(), toggle };
}
