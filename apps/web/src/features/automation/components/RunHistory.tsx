"use client";

import * as React from "react";
import { enUS, fr } from "date-fns/locale";

import { timeAgoCell } from "@/core/components/timeAgo";
import { differenceInSeconds } from "date-fns";
import { Badge } from "@krizaka/ui/badge";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { AutomationRunStatusSchema, type AutomationRun, type AutomationRunStatus } from "@krizaka/orazaka-shared";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { useAutomationRuns } from "@/features/automation/hooks/useAutomations";

const TONE: Record<AutomationRunStatus, "neutral" | "accent" | "success" | "danger" | "warning"> = {
  queued: "neutral",
  running: "accent",
  succeeded: "success",
  failed: "danger",
  skipped_budget: "warning",
};

const STATUS = AutomationRunStatusSchema.enum;

/** Whole seconds between two ISO instants, or null while the run goes on. */
function secondsBetween(start: string, end?: string | null): number | null {
  return end ? Math.max(0, differenceInSeconds(new Date(end), new Date(start))) : null;
}

/**
 * The history of one automation, newest first, on the kit's DataTable (cards on a phone). A run that is queued or
 * running refreshes by itself; a failed one says why in its detail.
 */
export function RunHistory({ automationId }: Readonly<{ automationId: string }>) {
  const { t, locale } = useTranslation();
  const r = t.automations.runs;
  const dateLocale = locale === "fr" ? fr : enUS;
  const runs = useAutomationRuns(automationId);

  const columns = React.useMemo<DataTableColumn<AutomationRun>[]>(
    () => [
      {
        id: "status",
        header: r.columns.status,
        type: "status",
        value: (run) => r.status[run.status],
        cell: (run) => (
          <Badge tone={TONE[run.status]} size="sm" dot={run.status === STATUS.running}>
            {r.status[run.status]}
          </Badge>
        ),
      },
      {
        id: "trigger",
        header: r.columns.trigger,
        priority: 2,
        value: (run) => (run.trigger ? r.trigger[run.trigger] : "—"),
      },
      {
        id: "started",
        header: r.columns.started,
        type: "date",
        value: (run) => new Date(run.startedAt),
        cell: (run) => timeAgoCell(run.startedAt, dateLocale),
      },
      {
        id: "duration",
        header: r.columns.duration,
        priority: 3,
        type: "number",
        align: "end",
        value: (run) => secondsBetween(run.startedAt, run.finishedAt),
        cell: (run) => {
          const seconds = secondsBetween(run.startedAt, run.finishedAt);
          return seconds === null ? "—" : fill("{n} s", { n: seconds });
        },
      },
      {
        id: "detail",
        header: r.columns.detail,
        priority: 2,
        value: (run) => run.errorMessage ?? run.errorCode ?? "",
        cell: (run) => {
          if (run.status === STATUS.skipped_budget) return r.skippedBudget;
          if (run.errorMessage ?? run.errorCode)
            return <span className="text-danger">{run.errorMessage ?? run.errorCode}</span>;
          return run.studioRunId ? <code className="font-mono text-xs">{run.studioRunId.slice(0, 8)}</code> : "—";
        },
      },
    ],
    [r, dateLocale],
  );

  return (
    <DataTable
      caption={r.caption}
      rows={runs.data ?? []}
      columns={columns}
      getRowId={(run) => run.id}
      rowLabel={(run) => r.status[run.status]}
      loading={runs.isLoading}
      empty={<p className="p-4 text-center text-sm text-fg-secondary">{r.empty}</p>}
      labels={{
        loading: t.automations.loading,
        empty: r.empty,
        details: t.jobsTable.details,
        expandRow: (name: string) => fill(t.jobsTable.expandRow, { name }),
        showMore: (name: string) => fill(t.jobsTable.showMore, { name }),
        results: (n: number) => (n === 1 ? t.jobsTable.resultsOne : fill(t.jobsTable.results, { n })),
        filters: t.jobsTable.filters,
        clearFilters: t.jobsTable.clearFilters,
        removeFilter: (label: string) => fill(t.jobsTable.removeFilter, { label }),
        activeFilter: (header: string, value: string) => fill(t.jobsTable.activeFilter, { header, value }),
        search: (header: string) => fill(t.jobsTable.search, { header }),
      }}
    />
  );
}
