"use client";

import * as React from "react";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { LawAdminApi } from "@/console/services/law.admin.api";
import type { ImportRow, Instrument } from "@/console/services/law.schemas";
import { useLawLoad } from "@/console/features/law/hooks/useLawLoad";
import { useLawCopy, type LawWords } from "@/console/features/law/hooks/useLawCopy";
import { ArticleLookupForm } from "@/console/features/law/components/ArticleLookupForm";
import { ManualImportForm } from "@/console/features/law/components/ManualImportForm";

const STATUS_TONE: Record<string, "neutral" | "success" | "danger"> = {
  RUNNING: "neutral",
  SUCCEEDED: "success",
  FAILED: "danger",
};
const TITLE = { fr: "titleFr", en: "titleEn" } as const;
const short = (hash: string | null | undefined) => (hash ? `${hash.slice(0, 12)}…` : "—");

function instrumentColumns({ t, locale }: LawWords): DataTableColumn<Instrument>[] {
  const c = t.corpus;
  return [
    {
      id: "code",
      header: c.code,
      card: "title",
      sortable: true,
      cell: (i) => <code className="text-xs font-semibold">{i.code}</code>,
    },
    { id: "title", header: c.titleCol, value: (i) => i[TITLE[locale]] ?? i.titleFr ?? i.titleEn ?? "—" },
    { id: "jurisdiction", header: c.jurisdiction },
    { id: "sourceKey", header: c.source },
    { id: "articles", header: c.articles, align: "end", sortable: true },
    { id: "versions", header: c.versions, align: "end", sortable: true },
  ];
}

function importColumns({ t, date }: LawWords): DataTableColumn<ImportRow>[] {
  const c = t.corpus;
  const modes = c.modes as Record<string, string>;
  const statuses = c.statuses as Record<string, string>;
  return [
    {
      id: "startedAt",
      header: c.started,
      card: "title",
      sortable: true,
      value: (r) => r.startedAt,
      cell: (r) => date(r.startedAt, true),
    },
    { id: "sourceKey", header: c.source },
    { id: "mode", header: c.mode, value: (r) => modes[r.mode] ?? r.mode },
    {
      id: "status",
      header: c.importStatus,
      value: (r) => statuses[r.status] ?? r.status,
      cell: (r) => (
        <Badge tone={STATUS_TONE[r.status] ?? "neutral"} size="sm">
          {statuses[r.status] ?? r.status}
        </Badge>
      ),
    },
    {
      id: "datasetSha256",
      header: c.sha256,
      value: (r) => r.datasetSha256 ?? "",
      cell: (r) => (
        <code className="text-xs" title={r.datasetSha256 ?? undefined}>
          {short(r.datasetSha256)}
        </code>
      ),
    },
    {
      id: "counts",
      header: c.counts,
      align: "end",
      value: (r) => `${r.articlesNew} · ${r.articlesChanged} · ${r.articlesUnchanged}`,
    },
    { id: "consolidatedTo", header: c.consolidatedTo, value: (r) => (r.consolidatedTo ? date(r.consolidatedTo) : "—") },
    { id: "importedBy", header: c.importedBy, value: (r) => r.importedBy ?? "—" },
  ];
}

/** The corpus: its instruments, its imports with their hashes and counts, an article by point in time, a manual import. */
export function CorpusPanel() {
  const copy = useLawCopy();
  const { t, refusal, table } = copy;
  const c = t.corpus;
  const instruments = useLawLoad<Instrument[]>(LawAdminApi.instruments, []);
  const loadImports = React.useCallback(() => LawAdminApi.imports(50), []);
  const imports = useLawLoad<ImportRow[]>(loadImports, []);
  const problem = instruments.refusal ?? imports.refusal;

  const instruments_ = instrumentColumns(copy);
  const imports_ = importColumns(copy);

  return (
    <section className="flex flex-col gap-8">
      {problem && <Alert tone="warning">{refusal(problem)}</Alert>}
      <section aria-label={c.instruments} className="flex flex-col gap-2">
        <h2 className="text-lg font-bold text-fg">{c.instruments}</h2>
        <DataTable
          caption={c.instruments}
          rows={instruments.data}
          columns={instruments_}
          getRowId={(i) => `${i.jurisdiction}-${i.code}`}
          loading={instruments.isLoading}
          labels={table}
        />
      </section>
      <section aria-label={c.imports} className="flex flex-col gap-2">
        <h2 className="text-lg font-bold text-fg">{c.imports}</h2>
        <DataTable
          caption={c.imports}
          rows={imports.data}
          columns={imports_}
          getRowId={(r) => r.id}
          loading={imports.isLoading}
          defaultPageSize={10}
          labels={table}
          renderDetail={(r) => (
            <p className="break-all p-3 font-mono text-xs text-fg-secondary">
              {c.dataset}: {r.datasetUrl ?? "—"} · {c.sha256}: {r.datasetSha256 ?? "—"}
            </p>
          )}
        />
      </section>
      <section className="grid gap-8 lg:grid-cols-2">
        <ArticleLookupForm />
        <ManualImportForm onImported={imports.reload} />
      </section>
    </section>
  );
}
