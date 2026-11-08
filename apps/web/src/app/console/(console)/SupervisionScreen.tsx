"use client";

import * as React from "react";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { LoadingRegion } from "@krizaka/ui/loader";
import { Skeleton } from "@krizaka/ui/skeleton";
import { consoleCopy } from "@/console/core/copy/console";
import { ActivityCharts } from "@/console/features/supervision/components/ActivityCharts";
import { SupervisionKpis } from "@/console/features/supervision/components/SupervisionKpis";
import { UsageCharts } from "@/console/features/supervision/components/UsageCharts";
import { useSupervision } from "@/console/features/supervision/hooks/useSupervision";
import {
  byCapability,
  consumers,
  jobsPerDay,
  latencyByCapability,
  usageTotals,
} from "@/console/features/supervision/utils/supervision";

const KPI_SKELETON = (
  <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
    {[0, 1, 2, 3].map((i) => (
      <li key={i}>
        <Skeleton className="h-[7.5rem] w-full rounded-xl" />
      </li>
    ))}
  </ul>
);

/**
 * The supervision of the platform: the four figures first (behind a skeleton of their shape, words if the wait grows),
 * then the ledger and the job service drawn. A source that fails is said once, and the rest is still shown.
 */
export function SupervisionScreen() {
  const s = consoleCopy.supervision;
  const data = useSupervision(30);
  const totals = React.useMemo(() => usageTotals(data.usage), [data.usage]);
  const capabilities = React.useMemo(() => byCapability(data.usage), [data.usage]);
  const top = React.useMemo(() => consumers(data.consumers), [data.consumers]);
  const perDay = React.useMemo(() => jobsPerDay(data.jobs, 14), [data.jobs]);
  const latency = React.useMemo(() => latencyByCapability(data.jobs), [data.jobs]);

  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">{s.title}</h1>
        <p className="max-w-3xl text-sm text-fg-secondary">{s.lead}</p>
      </header>

      {data.hasError && !data.isLoading && (
        <Alert
          tone="warning"
          action={
            <Button variant="secondary" size="sm" onClick={data.reload}>
              {s.retry}
            </Button>
          }
        >
          {s.error}
        </Alert>
      )}

      <LoadingRegion loading={data.isLoading} skeleton={KPI_SKELETON} label={s.loading} slowLabel={s.slow}>
        <SupervisionKpis
          requests={totals.requests}
          credits={totals.credits}
          refusalRate={totals.refusalRate}
          connections={data.connections}
        />
      </LoadingRegion>

      <UsageCharts capabilities={capabilities} top={top} loading={data.isLoading} error={data.hasError} />
      <ActivityCharts perDay={perDay} latency={latency} loading={data.isLoading} error={data.hasError} />
    </article>
  );
}
