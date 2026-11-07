"use client";

import { useCallback, useEffect, useState } from "react";
import type { ActorConsumption, CapabilityUsage, JobRecord } from "@krizaka/orazaka-shared";
import { BillingAdminApi } from "@/console/services/billing.admin.api";
import { PlatformAdminApi } from "@/console/services/platform.admin.api";

export interface Supervision {
  usage: CapabilityUsage[];
  consumers: ActorConsumption[];
  jobs: JobRecord[];
  connections: number | null;
  isLoading: boolean;
  /** At least one source failed: what did answer is still shown. */
  hasError: boolean;
  reload: () => void;
}

/**
 * The platform's figures, read in parallel from the billing ledger (usage, top consumers, 30 days) and the job
 * service (the latest jobs, the open streams). One source failing never blanks the others: a supervision screen is
 * most needed when part of the platform is down.
 */
export function useSupervision(days = 30): Supervision {
  const [state, setState] = useState<Omit<Supervision, "reload">>({
    usage: [],
    consumers: [],
    jobs: [],
    connections: null,
    isLoading: true,
    hasError: false,
  });

  const load = useCallback(async () => {
    setState((s) => ({ ...s, isLoading: true }));
    const [usage, top, jobs, connections] = await Promise.allSettled([
      BillingAdminApi.usageByCapability(days),
      BillingAdminApi.topConsumers(days, 8),
      PlatformAdminApi.fetchJobs(),
      PlatformAdminApi.activeConnections(),
    ]);
    setState({
      usage: usage.status === "fulfilled" ? usage.value : [],
      consumers: top.status === "fulfilled" ? top.value : [],
      jobs: jobs.status === "fulfilled" ? jobs.value.content : [],
      connections: connections.status === "fulfilled" ? connections.value : null,
      isLoading: false,
      hasError: [usage, top, jobs, connections].some((r) => r.status === "rejected"),
    });
  }, [days]);

  useEffect(() => {
    // Deferred: loading synchronously inside the effect sets state during the same commit.
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  return { ...state, reload: () => void load() };
}
