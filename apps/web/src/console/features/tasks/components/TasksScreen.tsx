"use client";

import * as React from "react";
import Link from "next/link";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { DataTable, DataTableFilters, type DataTableColumn } from "@krizaka/ui/data-table";
import { consoleCopy } from "@/console/core/copy/console";
import { useAuditRows } from "@/console/features/accounts/hooks/useAuditRows";
import { useOperations } from "@/console/features/operations/hooks/useOperations";
import { openTasks, type OperatorTask } from "@/console/features/tasks/utils/tasks";

const t = consoleCopy.tasks;

const columns: DataTableColumn<OperatorTask>[] = [
  { id: "at", header: t.when, sortable: true, value: (r) => r.at, cell: (r) => r.at.replace("T", " ").slice(0, 19) },
  { id: "kind", header: t.kind, filter: "facet", value: (r) => r.kind, cell: (r) => <Badge>{r.kind}</Badge> },
  { id: "source", header: t.service, filter: "facet", value: (r) => r.source },
  {
    id: "title",
    header: t.what,
    card: "title",
    value: (r) => r.title,
    cell: (r) => <span className="font-mono text-xs">{r.title}</span>,
  },
  { id: "why", header: t.why, value: (r) => r.why },
  {
    id: "request",
    header: t.request,
    value: (r) => r.requestId,
    cell: (r) =>
      r.requestId ? (
        <Link className="font-mono text-[11px] underline" href="/audit" title={t.followRequest}>
          {r.requestId}
        </Link>
      ) : (
        "—"
      ),
  },
];

/** What needs an operator, derived from the services' audit and the job queue (ADR-085): never a store of its own. */
export function TasksScreen() {
  const audit = useAuditRows();
  const operations = useOperations();
  // The page's own clock, read once (no ambient Date in component code).
  const [now] = React.useState(() => performance.timeOrigin + performance.now());
  const tasks = React.useMemo(() => openTasks(audit.rows, operations.jobs, now), [audit.rows, operations.jobs, now]);
  const loading = audit.loading || operations.isLoading;
  const failed = audit.failed || operations.hasError;
  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">{t.title}</h1>
        <p className="max-w-3xl text-sm text-fg-secondary">{t.lead}</p>
      </header>
      {failed && !loading && (
        <Alert
          tone="warning"
          action={
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                void audit.reload();
                operations.reload();
              }}
            >
              {t.retry}
            </Button>
          }
        >
          {t.error}
        </Alert>
      )}
      <DataTable
        caption={t.title}
        rows={tasks}
        columns={columns}
        getRowId={(r) => r.key}
        loading={loading}
        defaultPageSize={25}
        empty={t.empty}
        toolbar={<DataTableFilters />}
      />
    </article>
  );
}
