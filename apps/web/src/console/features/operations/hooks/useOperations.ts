"use client";

import { useCallback, useEffect, useState } from "react";
import type { JobRecord } from "@krizaka/orazaka-shared";
import { PlatformAdminApi } from "@/console/services/platform.admin.api";
import type { Health, Infrastructure } from "@/console/services/platform.schemas";

export interface Operations {
  health: Health | null;
  infrastructure: Infrastructure | null;
  jobs: JobRecord[];
  isLoading: boolean;
  /** At least one source failed: what did answer is still shown. */
  hasError: boolean;
  reload: () => void;
}

/** The three sources of the operations screen, read in parallel; one failing never blanks the others. */
export function useOperations(): Operations {
  const [state, setState] = useState<Omit<Operations, "reload">>({
    health: null,
    infrastructure: null,
    jobs: [],
    isLoading: true,
    hasError: false,
  });

  const load = useCallback(async () => {
    setState((s) => ({ ...s, isLoading: true }));
    const [health, infrastructure, jobs] = await Promise.allSettled([
      PlatformAdminApi.health(),
      PlatformAdminApi.infrastructure(),
      PlatformAdminApi.fetchJobs(),
    ]);
    setState({
      health: health.status === "fulfilled" ? health.value : null,
      infrastructure: infrastructure.status === "fulfilled" ? infrastructure.value : null,
      jobs: jobs.status === "fulfilled" ? jobs.value.content : [],
      isLoading: false,
      hasError: [health, infrastructure, jobs].some((r) => r.status === "rejected"),
    });
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  return { ...state, reload: () => void load() };
}
