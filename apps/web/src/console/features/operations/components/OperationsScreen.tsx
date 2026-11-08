"use client";

import * as React from "react";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { Stat, StatGrid } from "@krizaka/ui/stat";
import { DataTable, DataTableFilters, type DataTableColumn } from "@krizaka/ui/data-table";
import type { JobRecord, JobStatus } from "@krizaka/orazaka-shared";
import { consoleCopy } from "@/console/core/copy/console";
import { APP_ENV, APP_VERSION } from "@/console/core/config/version";
import {
  QUEUE_STATES,
  dependencyChecks,
  jobSeconds,
  queueDepths,
  recentFailures,
} from "@/console/features/operations/utils/operations";
import { useOperations } from "@/console/features/operations/hooks/useOperations";

const o = consoleCopy.operations;
const when = (iso: string) => iso.replace("T", " ").slice(0, 16);

const STATE_TONE: Record<JobStatus, "danger" | "success" | "warning" | "neutral"> = {
  PENDING: "warning",
  PROCESSING: "warning",
  COMPLETED: "success",
  FAILED: "danger",
  CANCELLED: "neutral",
  DELETED: "neutral",
};

const columns: DataTableColumn<JobRecord>[] = [
  {
    id: "featureKey",
    header: o.feature,
    sortable: true,
    filter: "facet",
    card: "title",
    cell: (j) => j.featureKey.replace(/^orazaka\./, ""),
  },
  {
    id: "status",
    header: o.status,
    sortable: true,
    filter: "facet",
    cell: (j) => <Badge tone={STATE_TONE[j.status]}>{o.states[j.status]}</Badge>,
  },
  { id: "userId", header: o.user, value: (j) => j.userId, cell: (j) => `${j.userId.slice(0, 8)}…` },
  { id: "createdAt", header: o.started, sortable: true, cell: (j) => when(j.createdAt) },
  {
    id: "seconds",
    header: o.seconds,
    sortable: true,
    align: "end",
    value: (j) => jobSeconds(j),
    cell: (j) => jobSeconds(j) ?? "—",
  },
];

const failureColumns: DataTableColumn<JobRecord>[] = [
  { id: "featureKey", header: o.feature, card: "title", cell: (j) => j.featureKey.replace(/^orazaka\./, "") },
  { id: "updatedAt", header: o.started, cell: (j) => when(j.updatedAt) },
  { id: "errorMessage", header: o.message, cell: (j) => j.errorMessage ?? "—" },
];

/** The platform's operations: dependencies, engines, the queue, recent errors, the runs, and the deployment's version. */
export function OperationsScreen() {
  const data = useOperations();
  const depths = React.useMemo(() => queueDepths(data.jobs), [data.jobs]);
  const failures = React.useMemo(() => recentFailures(data.jobs, 10), [data.jobs]);
  const checks = data.health ? dependencyChecks(data.health) : [];

  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">{o.title}</h1>
        <p className="max-w-3xl text-sm text-fg-secondary">{o.lead}</p>
      </header>

      {data.hasError && !data.isLoading && (
        <Alert
          tone="warning"
          action={
            <Button variant="secondary" size="sm" onClick={data.reload}>
              {o.retry}
            </Button>
          }
        >
          {o.error}
        </Alert>
      )}

      <StatGrid divided aria-label={o.queue}>
        {QUEUE_STATES.map((state) => (
          <Stat
            key={state}
            label={o.states[state]}
            value={data.isLoading ? "…" : depths[state]}
            hint={state === "PENDING" ? o.queueDesc : undefined}
          />
        ))}
      </StatGrid>

      <section className="grid gap-4 lg:grid-cols-3" aria-label={o.dependencies}>
        <Card.Root className="lg:col-span-2">
          <Card.Body>
            <Card.Title>{o.dependencies}</Card.Title>
            <Card.Description>{o.dependenciesDesc}</Card.Description>
            <ul className="flex flex-col gap-2">
              {checks.length === 0 && <li className="text-sm text-fg-muted">—</li>}
              {checks.map((c) => (
                <li key={c.id} className="flex items-center justify-between gap-3 text-sm">
                  <span className="text-fg">{o.checks[c.id]}</span>
                  <Badge tone={c.tone} dot>
                    {c.detail}
                  </Badge>
                </li>
              ))}
              {data.infrastructure && (
                <>
                  <li className="flex items-center justify-between gap-3 text-sm">
                    <span className="text-fg">{o.video}</span>
                    <Badge tone={data.infrastructure.video ? "success" : "warning"} dot>
                      {data.infrastructure.video ? o.online : o.offline}
                    </Badge>
                  </li>
                  <li className="flex items-center justify-between gap-3 text-sm">
                    <span className="text-fg">{o.image}</span>
                    <Badge tone={data.infrastructure.image ? "success" : "warning"} dot>
                      {data.infrastructure.image ? o.online : o.offline}
                    </Badge>
                  </li>
                </>
              )}
            </ul>
          </Card.Body>
        </Card.Root>
        <Card.Root>
          <Card.Body>
            <Card.Title>{o.deployment}</Card.Title>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
              <dt className="text-fg-muted">{o.version}</dt>
              <dd className="font-mono text-fg">{APP_VERSION}</dd>
              <dt className="text-fg-muted">{o.environment}</dt>
              <dd className="text-fg">{APP_ENV}</dd>
            </dl>
            <p className="mt-3 text-xs text-fg-muted">{o.pool}</p>
          </Card.Body>
        </Card.Root>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold text-fg">{o.failures}</h2>
        <DataTable
          caption={o.failures}
          rows={failures}
          columns={failureColumns}
          getRowId={(j) => j.id}
          loading={data.isLoading}
          empty={<p className="p-4 text-sm text-fg-muted">{o.noFailures}</p>}
          layout="auto"
        />
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold text-fg">{o.jobs}</h2>
        <p className="text-sm text-fg-secondary">{o.jobsDesc}</p>
        <DataTable
          caption={o.jobs}
          rows={data.jobs}
          columns={columns}
          getRowId={(j) => j.id}
          loading={data.isLoading}
          defaultPageSize={15}
          defaultSort={{ column: "createdAt", direction: "descending" }}
          toolbar={<DataTableFilters />}
          renderDetail={(j) => (
            <pre className="overflow-x-auto p-3 text-xs text-fg-secondary">
              {JSON.stringify({ error: j.errorMessage ?? null, payload: j.payload }, null, 2)}
            </pre>
          )}
        />
      </section>
    </article>
  );
}
