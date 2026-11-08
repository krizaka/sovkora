"use client";

import * as React from "react";
import { Alert } from "@krizaka/ui/alert";
import { Button } from "@krizaka/ui/button";
import { DataTable, DataTableExport, DataTableFilters, type DataTableColumn } from "@krizaka/ui/data-table";
import { consoleCopy } from "@/console/core/copy/console";
import { type AuditRow } from "@/console/features/accounts/utils/audit";
import { useAuditRows } from "@/console/features/accounts/hooks/useAuditRows";

const t = consoleCopy.audit;

const columns: DataTableColumn<AuditRow>[] = [
  {
    id: "at",
    header: t.when,
    sortable: true,
    value: (e) => e.at,
    cell: (e) => (e.at ? e.at.replace("T", " ").slice(0, 19) : "—"),
  },
  { id: "source", header: t.source, filter: "facet", value: (e) => e.source },
  { id: "outcome", header: t.outcome, filter: "facet", value: (e) => e.outcome },
  { id: "actor", header: t.actor, filter: "text", value: (e) => e.actor },
  {
    id: "action",
    header: t.action,
    filter: "facet",
    card: "title",
    cell: (e) => <span className="font-mono text-xs">{e.action}</span>,
  },
  {
    id: "targetId",
    header: t.target,
    value: (e) => e.targetId,
    cell: (e) => <span className="font-mono text-[11px]">{e.targetId || "—"}</span>,
  },
  { id: "detail", header: t.detail, value: (e) => e.detail },
  // The request the action was made in (the edge's X-Request-Id): filter by it to see everything one request did.
  {
    id: "requestId",
    header: t.request,
    filter: "text",
    value: (e) => e.requestId,
    cell: (e) => <span className="font-mono text-[11px]">{e.requestId || "—"}</span>,
  },
];

/** Every service's operator audit trail, newest first, filterable and exportable. */
export function AuditScreen() {
  const { rows, loading, failed, reload } = useAuditRows();
  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">{t.title}</h1>
        <p className="max-w-3xl text-sm text-fg-secondary">{t.lead}</p>
      </header>
      {failed && (
        <Alert
          tone="warning"
          action={
            <Button variant="secondary" size="sm" onClick={() => void reload()}>
              {t.retry}
            </Button>
          }
        >
          {t.error}
        </Alert>
      )}
      <DataTable
        caption={t.title}
        rows={rows}
        columns={columns}
        getRowId={(e) => e.key}
        loading={loading}
        defaultPageSize={25}
        defaultSort={{ column: "at", direction: "descending" }}
        toolbar={
          <div className="flex flex-col gap-3">
            <DataTableFilters />
            <div className="flex justify-end">
              <DataTableExport filename="orazaka-audit.csv" />
            </div>
          </div>
        }
      />
    </article>
  );
}
