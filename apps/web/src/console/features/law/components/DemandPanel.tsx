"use client";

import * as React from "react";
import { z } from "zod";
import { Alert } from "@krizaka/ui/alert";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { createKrizakaForm } from "@krizaka/ui/form";
import { fill } from "@/core/context/fill";
import { LawAdminApi } from "@/console/services/law.admin.api";
import { LAW_LANGUAGES, type Demand, type Derivation } from "@/console/services/law.schemas";
import { asRefusal, useLawLoad } from "@/console/features/law/hooks/useLawLoad";
import { useLawCopy, type LawWords } from "@/console/features/law/hooks/useLawCopy";
import { hasText, isIsoDate } from "@/console/features/law/utils/law";

const UNKNOWN_KIND = "UNKNOWN";
const required = z.string().refine(hasText, "law.required");

const deriveForm = createKrizakaForm({
  schema: z.object({
    jurisdiction: required,
    kindId: required,
    asOf: z.string().refine(isIsoDate, "law.date"),
    language: z.enum(LAW_LANGUAGES),
  }),
  defaultValues: { jurisdiction: "QC", kindId: "", asOf: "", language: "fr" },
});

/** « Start a derivation »: derivation is an administrator's act (ADR-089 §3, §8) — a Studio run within its budget. */
function DerivationForm() {
  const { t, locale, refusal } = useLawCopy();
  const d = t.demand;
  const [run, setRun] = React.useState<Derivation | null>(null);
  const [problem, setProblem] = React.useState<string | null>(null);
  const words: Record<string, string> = { "law.required": t.editor.required, "law.date": t.kind.invalidDate };
  const outcomes = d.outcomes as Record<string, string>;
  const form = deriveForm.useForm({
    i18n: { t: (key) => words[key], locale },
    onSubmit: async (request) => {
      try {
        setRun(await LawAdminApi.derive(request));
        setProblem(null);
      } catch (error) {
        setRun(null);
        setProblem(refusal(asRefusal(error)));
      }
    },
  });
  return (
    <section aria-label={d.derive} className="flex max-w-xl flex-col gap-3">
      <h2 className="text-lg font-bold text-fg">{d.derive}</h2>
      <p className="text-sm text-fg-secondary">{d.deriveLead}</p>
      <form.Form aria-label={d.derive} className="flex flex-col gap-3">
        <section className="grid gap-3 sm:grid-cols-2">
          <form.TextField name="jurisdiction" label={d.jurisdiction} autoComplete="off" />
          <form.TextField name="kindId" label={d.kindId} autoComplete="off" />
        </section>
        <form.DateField name="asOf" label={d.asOf} entry="parts" labels={t.dateParts} />
        <form.Select
          name="language"
          label={d.language}
          options={LAW_LANGUAGES.map((value) => ({ value, label: t.languages[value] }))}
        />
        <form.Submit variant="primary">{d.deriveSubmit}</form.Submit>
      </form.Form>
      {problem && <Alert tone="danger">{problem}</Alert>}
      {run && (
        <Alert tone="success">
          {fill(d.derived, {
            runId: run.runId,
            outcome: outcomes[run.outcome] ?? run.outcome,
            proposals: run.proposals,
            rejected: run.rejected,
            credits: run.creditsSpent,
          })}
        </Alert>
      )}
    </section>
  );
}

function demandColumns({ t, date }: LawWords): DataTableColumn<Demand>[] {
  const d = t.demand;
  return [
    {
      id: "kindId",
      header: d.kind,
      card: "title",
      sortable: true,
      value: (r) => (r.kindId === UNKNOWN_KIND ? d.unknown : r.kindId),
      cell: (r) => (r.kindId === UNKNOWN_KIND ? d.unknown : <code className="text-xs">{r.kindId}</code>),
    },
    { id: "jurisdiction", header: d.jurisdiction },
    { id: "count", header: d.count, align: "end", sortable: true },
    { id: "firstSeen", header: d.firstSeen, value: (r) => r.firstSeen, cell: (r) => date(r.firstSeen, true) },
    {
      id: "lastSeen",
      header: d.lastSeen,
      sortable: true,
      value: (r) => r.lastSeen,
      cell: (r) => date(r.lastSeen, true),
    },
  ];
}

/** What people sent that no promoted ruleset checks yet (`kind_demand`), counted — and the derivation that answers it. */
export function DemandPanel() {
  const copy = useLawCopy();
  const { t, refusal, table } = copy;
  const d = t.demand;
  const demand = useLawLoad<Demand[]>(LawAdminApi.demand, []);
  const columns = demandColumns(copy);
  return (
    <section className="flex flex-col gap-8">
      <section aria-label={d.caption} className="flex flex-col gap-2">
        <h2 className="text-lg font-bold text-fg">{d.caption}</h2>
        <p className="text-sm text-fg-secondary">{d.lead}</p>
        {demand.refusal && <Alert tone="warning">{refusal(demand.refusal)}</Alert>}
        <DataTable
          caption={d.caption}
          rows={demand.data}
          columns={columns}
          getRowId={(r) => `${r.jurisdiction}-${r.kindId}`}
          loading={demand.isLoading}
          labels={table}
        />
      </section>
      <DerivationForm />
    </section>
  );
}
