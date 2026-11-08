"use client";

import * as React from "react";
import { z } from "zod";
import { Alert } from "@krizaka/ui/alert";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { createKrizakaForm } from "@krizaka/ui/form";
import { fill } from "@/core/context/fill";
import { LawAdminApi } from "@/console/services/law.admin.api";
import type { Golden, Promotion, RuleProposal } from "@/console/services/law.schemas";
import { asRefusal, useLawLoad } from "@/console/features/law/hooks/useLawLoad";
import { useLawCopy, type LawWords } from "@/console/features/law/hooks/useLawCopy";
import { isIsoDate, orDash, promotable, type Notice } from "@/console/features/law/utils/law";

function caseColumns({ t }: LawWords): DataTableColumn<Golden["cases"][number]>[] {
  const k = t.kind;
  const forms = k.forms as Record<string, string>;
  return [
    { id: "name", header: k.caseName, card: "title", cell: (c) => <code className="text-xs">{c.name}</code> },
    { id: "form", header: k.caseForm, value: (c) => forms[c.form] ?? c.form },
  ];
}

function GoldenCases({ kindId, golden }: Readonly<{ kindId: string; golden: Golden }>) {
  const copy = useLawCopy();
  const { t, table } = copy;
  const k = t.kind;
  const columns = caseColumns(copy);
  return (
    <section aria-label={fill(k.golden, { kind: kindId })} className="flex flex-col gap-2">
      <header className="flex flex-wrap items-center gap-2">
        <h3 className="text-sm font-bold text-fg">{fill(k.golden, { kind: kindId })}</h3>
        <Badge tone={golden.met ? "success" : "warning"}>{golden.met ? k.met : k.notMet}</Badge>
      </header>
      <p className="text-sm text-fg-secondary">
        {fill(k.goldenSummary, {
          documents: golden.documents,
          compliant: golden.compliant,
          defective: golden.defective,
          blank: golden.blank,
          required: golden.required,
        })}
      </p>
      <DataTable
        caption={fill(k.golden, { kind: kindId })}
        rows={golden.cases}
        columns={columns}
        getRowId={(c) => c.name}
        labels={table}
      />
    </section>
  );
}

function approvedColumns({ t }: LawWords): DataTableColumn<RuleProposal>[] {
  return [
    { id: "ruleId", header: t.queue.rule, card: "title", cell: (p) => <code className="text-xs">{p.ruleId}</code> },
    { id: "title", header: t.editor.title, value: (p) => orDash(p.rule.title) },
    { id: "revision", header: t.queue.revision, align: "end", value: (p) => p.proposalRevision },
  ];
}

const promoteForm = createKrizakaForm({
  schema: z.object({ effectiveFrom: z.string().refine(isIsoDate, "law.date"), revisionNote: z.string() }),
  defaultValues: { effectiveFrom: "", revisionNote: "" },
});

function PromoteForm({
  kindId,
  approved,
  onPromoted,
}: Readonly<{ kindId: string; approved: RuleProposal[]; onPromoted: () => void }>) {
  const copy = useLawCopy();
  const { t, refusal, locale, table } = copy;
  const k = t.kind;
  const [selected, setSelected] = React.useState<string[]>([]);
  const [notice, setNotice] = React.useState<Notice | null>(null);
  const [promotion, setPromotion] = React.useState<Promotion | null>(null);
  const words: Record<string, string> = { "law.date": k.invalidDate };
  const form = promoteForm.useForm({
    i18n: { t: (key) => words[key], locale },
    onSubmit: async ({ effectiveFrom, revisionNote }) => {
      const ids = selected.filter((id) => approved.some((p) => p.id === id));
      if (ids.length === 0) return setNotice({ tone: "warning", text: k.nothingSelected });
      try {
        const done = await LawAdminApi.promote(kindId, {
          proposalIds: ids,
          effectiveFrom,
          ...(revisionNote.trim() ? { revisionNote } : {}),
        });
        setPromotion(done);
        setNotice(null);
        setSelected([]);
        onPromoted();
      } catch (error) {
        setNotice({ tone: "danger", text: refusal(asRefusal(error)) });
      }
    },
  });
  const columns = approvedColumns(copy);
  return (
    <section aria-label={fill(k.promote, { kind: kindId })} className="flex flex-col gap-3">
      <h3 className="text-sm font-bold text-fg">{fill(k.promote, { kind: kindId })}</h3>
      <p className="text-sm text-fg-secondary">{k.promoteLead}</p>
      {promotion && (
        <Alert
          tone="success"
          action={
            <Button variant="secondary" size="sm" asChild>
              <a href={promotion.prUrl} target="_blank" rel="noreferrer">
                {k.openPr}
              </a>
            </Button>
          }
        >
          {fill(k.opened, { revision: promotion.revision, path: promotion.rulesetPath })}
        </Alert>
      )}
      {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
      {approved.length === 0 ? (
        <p className="text-sm text-fg-muted">{k.noneApproved}</p>
      ) : (
        <>
          <DataTable
            caption={fill(k.approvedCaption, { kind: kindId })}
            rows={approved}
            columns={columns}
            getRowId={(p) => p.id}
            rowLabel={(p) => p.ruleId}
            selectable
            selected={selected}
            onSelectedChange={setSelected}
            labels={{ ...table, selectAll: k.selectAll, selectRow: (name: string) => fill(k.selectRow, { name }) }}
          />
          <form.Form aria-label={fill(k.promote, { kind: kindId })} className="flex max-w-xl flex-col gap-3">
            <form.DateField name="effectiveFrom" label={k.effectiveFrom} entry="parts" labels={t.dateParts} />
            <form.TextArea name="revisionNote" label={k.revisionNote} rows={2} />
            <form.Submit variant="primary">{k.submit}</form.Submit>
          </form.Form>
        </>
      )}
    </section>
  );
}

/** One kind: its golden cases (from orazaka-packs `main`) and the promotion of its approved rules. */
export function KindPanel({
  kindId,
  version,
  onPromoted,
}: Readonly<{ kindId: string; version: number; onPromoted: () => void }>) {
  const { t, refusal } = useLawCopy();
  // `version` changes when a decision of the queue may have changed what this kind can promote.
  const loadGolden = React.useCallback(() => LawAdminApi.golden(kindId), [kindId]);
  const loadApproved = React.useCallback(() => LawAdminApi.proposals("APPROVED", kindId), [kindId]);
  const golden = useLawLoad<Golden | null>(loadGolden, null);
  const approved = useLawLoad<RuleProposal[]>(loadApproved, [], version);
  return (
    <section className="flex flex-col gap-6 rounded-lg border border-border-subtle p-4">
      {golden.refusal && <Alert tone="warning">{refusal(golden.refusal)}</Alert>}
      {golden.data && <GoldenCases kindId={kindId} golden={golden.data} />}
      {approved.refusal ? (
        <Alert tone="danger">{refusal(approved.refusal)}</Alert>
      ) : (
        <PromoteForm kindId={kindId} approved={promotable(approved.data, kindId)} onPromoted={onPromoted} />
      )}
      {(golden.isLoading || approved.isLoading) && <p className="text-sm text-fg-muted">{t.loading}</p>}
    </section>
  );
}
