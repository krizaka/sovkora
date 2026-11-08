"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminRefusal, PlatformAdminApi } from "@/console/services/platform.admin.api";
import type { AdminUser } from "@/console/services/platform.schemas";
import { refusalMessage } from "@/console/features/accounts/utils/accounts";

export interface Accounts {
  users: AdminUser[];
  total: number;
  query: string;
  setQuery: (q: string) => void;
  isLoading: boolean;
  hasError: boolean;
  /** The last refusal, worded; null when the last action went through. */
  problem: string | null;
  reload: () => void;
  run: (action: () => Promise<unknown>) => Promise<boolean>;
}

/** The accounts the identity service lists for an administrator, a search box, and the actions that change one. */
export function useAccounts(): Accounts {
  const [query, setQuery] = useState("");
  const [state, setState] = useState({ users: [] as AdminUser[], total: 0, isLoading: true, hasError: false });
  const [problem, setProblem] = useState<string | null>(null);

  const load = useCallback(async (q: string) => {
    setState((s) => ({ ...s, isLoading: true }));
    try {
      const page = await PlatformAdminApi.users(q, 0, 100);
      setState({ users: page.content, total: page.total, isLoading: false, hasError: false });
    } catch {
      setState({ users: [], total: 0, isLoading: false, hasError: true });
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => void load(query), query ? 250 : 0);
    return () => clearTimeout(timer);
  }, [load, query]);

  const run = useCallback(
    async (action: () => Promise<unknown>) => {
      try {
        await action();
        setProblem(null);
        await load(query);
        return true;
      } catch (error) {
        setProblem(
          error instanceof AdminRefusal
            ? refusalMessage(error.code, error.status)
            : "The identity service did not answer.",
        );
        return false;
      }
    },
    [load, query],
  );

  return { ...state, query, setQuery, problem, reload: () => void load(query), run };
}
