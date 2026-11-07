"use client";

import { useQuery } from "@krizaka/query";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { studiosCopy, type StudiosLocale, type StudiosWords } from "@/console/core/copy/studios";
import { useConsoleLocale } from "@/console/core/providers/ConsoleLocale";
import { StudioAdminApi, type StudioUsageRow } from "@/console/services/studio.admin.api";

/**
 * How the marketplace is actually performing (design §14).
 *
 * Sorted busiest first by the server, and rows with no runs are kept rather than
 * filtered: a Studio installed by four people and run by none is the single most
 * actionable line on this screen, and hiding it would leave the operator looking at
 * only the products that already work.
 */
export function StudioAnalyticsScreen({ locale }: Readonly<{ locale?: StudiosLocale }>) {
  const fromContext = useConsoleLocale();
  const words = studiosCopy[locale ?? fromContext].analytics;
  const usage = useQuery({ queryKey: ["studio-analytics"], queryFn: () => StudioAdminApi.analytics() });
  const rows: StudioUsageRow[] = usage.data ?? [];
  const isLoading = usage.isPending;
  const failure = usage.error;
  let error: string | null = null;
  if (failure) {
    error = failure instanceof Error ? failure.message : words.failed;
  }

  if (isLoading) {
    return <p className="text-sm text-fg-muted">{words.loading}</p>;
  }
  if (error) {
    return <p className="text-sm text-fg-danger">{error}</p>;
  }

  return (
    <DataTable
      caption={words.caption}
      rows={rows}
      columns={columnsOf(words)}
      getRowId={(row) => row.studioKey}
      labels={{ loading: words.reading, empty: words.empty }}
    />
  );
}

// Below half the runs succeeding, a Studio is a support queue rather than a product.
const isHealthy = (row: StudioUsageRow) => row.runs > 0 && row.successRate >= 0.5;

const columnsOf = (words: StudiosWords["analytics"]): DataTableColumn<StudioUsageRow>[] => [
  {
    id: "label",
    header: words.studio,
    sortable: true,
    card: "title",
    cell: (row) => (
      <>
        {row.label}
        <span className="block font-mono text-[10px] text-fg-muted">{row.studioKey}</span>
      </>
    ),
  },
  { id: "installations", header: words.installations, sortable: true, align: "end" },
  {
    id: "runs",
    header: words.runs,
    sortable: true,
    align: "end",
    cell: (row) => (
      <>
        {row.runs}
        {row.failed > 0 && (
          <span className="text-fg-danger">
            {" "}
            ({row.failed} {words.failures})
          </span>
        )}
      </>
    ),
  },
  {
    id: "successRate",
    header: words.successRate,
    sortable: true,
    align: "end",
    cell: (row) => (
      <span className={isHealthy(row) ? "text-fg-success" : "text-fg-muted"}>
        {row.runs === 0 ? "—" : `${Math.round(row.successRate * 100)} %`}
      </span>
    ),
  },
  { id: "estimatedCredits", header: words.credits, sortable: true, align: "end" },
  {
    id: "installedButUnused",
    header: "",
    cell: (row) =>
      row.installedButUnused ? (
        <span title={words.unusedHint} className="text-[11px] text-fg-warning">
          {words.unused}
        </span>
      ) : null,
  },
];
