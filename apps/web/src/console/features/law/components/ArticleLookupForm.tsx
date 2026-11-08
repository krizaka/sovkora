"use client";

import * as React from "react";
import { z } from "zod";
import { Alert } from "@krizaka/ui/alert";
import { DataTable, type DataTableColumn } from "@krizaka/ui/data-table";
import { createKrizakaForm } from "@krizaka/ui/form";
import { fill } from "@/core/context/fill";
import { LawAdminApi } from "@/console/services/law.admin.api";
import { LAW_LANGUAGES, type ArticleLookup, type ArticleVersion } from "@/console/services/law.schemas";
import { asRefusal } from "@/console/features/law/hooks/useLawLoad";
import { useLawCopy, type LawWords } from "@/console/features/law/hooks/useLawCopy";
import { hasText, isIsoDate } from "@/console/features/law/utils/law";

const required = z.string().refine(hasText, "law.required");

const lookupForm = createKrizakaForm({
  schema: z.object({
    jurisdiction: required,
    instrument: required,
    ref: required,
    language: z.enum(LAW_LANGUAGES),
    asOf: z.string().refine(isIsoDate, "law.date"),
  }),
  defaultValues: { jurisdiction: "QC", instrument: "CCQ-1991", ref: "", language: "fr", asOf: "" },
});

const short = (hash: string) => hash.slice(0, 12);

/** The versions of an article, newest first as the service lists them. */
function versionColumns({ t, date }: LawWords): DataTableColumn<ArticleVersion>[] {
  const c = t.corpus;
  return [
    { id: "inForceFrom", header: c.from, card: "title", value: (v) => v.inForceFrom, cell: (v) => date(v.inForceFrom) },
    { id: "inForceTo", header: c.to, value: (v) => (v.inForceTo ? date(v.inForceTo) : c.current) },
    {
      id: "contentHash",
      header: c.hash,
      cell: (v) => (
        <code className="text-xs" title={v.contentHash}>
          {short(v.contentHash)}
        </code>
      ),
    },
    { id: "length", header: c.length, align: "end" },
    {
      id: "officialUrl",
      header: c.official,
      cell: (v) => (
        <a href={v.officialUrl} target="_blank" rel="noreferrer" className="text-fg-accent underline">
          {c.official}
        </a>
      ),
    },
  ];
}

/** Which version of an article was in force on a date: metadata only (hash, length, official address) — never the text. */
export function ArticleLookupForm() {
  const copy = useLawCopy();
  const { t, locale, date, refusal, table } = copy;
  const c = t.corpus;
  const [found, setFound] = React.useState<ArticleLookup | null>(null);
  const [problem, setProblem] = React.useState<string | null>(null);
  const words: Record<string, string> = { "law.required": t.editor.required, "law.date": t.kind.invalidDate };
  const form = lookupForm.useForm({
    i18n: { t: (key) => words[key], locale },
    onSubmit: async (query) => {
      try {
        setFound(await LawAdminApi.article(query));
        setProblem(null);
      } catch (error) {
        setFound(null);
        setProblem(refusal(asRefusal(error)));
      }
    },
  });
  const columns = versionColumns(copy);
  return (
    <section aria-label={c.lookup} className="flex flex-col gap-3">
      <h2 className="text-lg font-bold text-fg">{c.lookup}</h2>
      <p className="text-sm text-fg-secondary">{c.lookupLead}</p>
      <form.Form aria-label={c.lookup} className="flex flex-col gap-3">
        <section className="grid gap-3 sm:grid-cols-2">
          <form.TextField name="jurisdiction" label={c.jurisdiction} autoComplete="off" />
          <form.TextField name="instrument" label={c.code} autoComplete="off" />
          <form.TextField name="ref" label={c.ref} autoComplete="off" />
          <form.Select
            name="language"
            label={c.language}
            options={LAW_LANGUAGES.map((value) => ({ value, label: t.languages[value] }))}
          />
        </section>
        <form.DateField name="asOf" label={c.asOf} entry="parts" labels={t.dateParts} />
        <form.Submit variant="secondary">{c.lookupSubmit}</form.Submit>
      </form.Form>
      {problem && <Alert tone="danger">{problem}</Alert>}
      {found && (
        <>
          <Alert tone={found.inForce ? "success" : "warning"}>
            {found.inForce
              ? fill(c.inForce, {
                  date: date(found.asOf),
                  from: date(found.inForce.inForceFrom),
                  length: found.inForce.length,
                  hash: short(found.inForce.contentHash),
                })
              : fill(c.notInForce, { date: date(found.asOf) })}
          </Alert>
          <DataTable
            caption={c.history}
            rows={found.history}
            columns={columns}
            getRowId={(v) => `${v.inForceFrom}-${v.contentHash}`}
            labels={table}
          />
        </>
      )}
    </section>
  );
}
