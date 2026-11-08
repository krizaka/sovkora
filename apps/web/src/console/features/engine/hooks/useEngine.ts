"use client";

import { useCallback } from "react";
import { useQuery, useQueryClient } from "@krizaka/query";
import {
  RuntimeConfigApi,
  RuntimeConfigRefusal,
  type RuntimeAuditEntry,
  type RuntimeSetting,
  type SettingValue,
} from "@/console/services/runtime-config.admin.api";
import { withSetting } from "@/console/features/engine/utils/engine";

export interface Engine {
  settings: RuntimeSetting[];
  isLoading: boolean;
  hasError: boolean;
  reload: () => void;
  /** Saves one value; resolves to the server's refusal message, or `null` when it was accepted. */
  save: (key: string, value: SettingValue) => Promise<string | null>;
}

const SETTINGS_KEY = ["engine", "settings"] as const;

/** The runtime settings, read once, and the one-key save that keeps the list at the server's answer. */
export function useEngine(): Engine {
  const client = useQueryClient();
  const query = useQuery({ queryKey: SETTINGS_KEY, queryFn: () => RuntimeConfigApi.list(), retry: false, gcTime: 0 });

  const save = useCallback(
    async (key: string, value: SettingValue) => {
      try {
        const saved = await RuntimeConfigApi.update(key, value);
        client.setQueryData<RuntimeSetting[]>(SETTINGS_KEY, (settings) => withSetting(settings ?? [], saved));
        return null;
      } catch (error) {
        return error instanceof RuntimeConfigRefusal ? error.message : "";
      }
    },
    [client],
  );

  return {
    settings: query.data ?? [],
    isLoading: query.isPending,
    hasError: query.isError,
    reload: () => void query.refetch(),
    save,
  };
}

/** The change history of one key, read when its panel opens; `version` (the setting's value) re-reads it after a save. */
export function useAudit(
  key: string,
  version: unknown,
): { entries: RuntimeAuditEntry[]; isLoading: boolean; hasError: boolean } {
  const query = useQuery({
    queryKey: ["engine", "audit", key, version],
    queryFn: () => RuntimeConfigApi.audit(key),
    retry: false,
    gcTime: 0,
  });
  return { entries: query.data ?? [], isLoading: query.isPending, hasError: query.isError };
}
