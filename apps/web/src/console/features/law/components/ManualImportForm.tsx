"use client";

import * as React from "react";
import { z } from "zod";
import { Alert } from "@krizaka/ui/alert";
import { createKrizakaForm } from "@krizaka/ui/form";
import { fill } from "@/core/context/fill";
import { LawAdminApi } from "@/console/services/law.admin.api";
import { LAW_LANGUAGES, type ImportResult } from "@/console/services/law.schemas";
import { asRefusal } from "@/console/features/law/hooks/useLawLoad";
import { useLawCopy } from "@/console/features/law/hooks/useLawCopy";
import { hasText, isHttpsUrl, isIsoDate } from "@/console/features/law/utils/law";

const required = z.string().refine(hasText, "law.required");

const importForm = createKrizakaForm({
  schema: z.object({
    file: z.array(z.instanceof(File)).min(1, "law.file"),
    jurisdiction: required,
    instrument: required,
    language: z.enum(LAW_LANGUAGES),
    datasetUrl: z.string().refine(isHttpsUrl, "law.https"),
    sha256: z.string().refine((v) => v === "" || /^[0-9a-f]{64}$/i.test(v), "law.sha"),
    consolidatedTo: z.string().refine((v) => v === "" || isIsoDate(v), "law.date"),
  }),
  defaultValues: {
    file: [] as File[],
    jurisdiction: "QC",
    instrument: "CCQ-1991",
    language: "fr",
    datasetUrl: "",
    sha256: "",
    consolidatedTo: "",
  },
});

/** The manual import of a consolidated XML file, with where it came from (multipart, through the BFF as it came). */
export function ManualImportForm({ onImported }: Readonly<{ onImported: () => void }>) {
  const { t, locale, refusal } = useLawCopy();
  const c = t.corpus;
  const [result, setResult] = React.useState<ImportResult | null>(null);
  const [problem, setProblem] = React.useState<string | null>(null);
  const words: Record<string, string> = {
    "law.required": t.editor.required,
    "law.https": t.editor.https,
    "law.file": c.fileRequired,
    "law.sha": c.sha256Invalid,
    "law.date": t.kind.invalidDate,
  };
  const form = importForm.useForm({
    i18n: { t: (key) => words[key], locale },
    onSubmit: async ({ file, sha256, consolidatedTo, ...fields }) => {
      try {
        setResult(
          await LawAdminApi.importFile({
            ...fields,
            file: file[0],
            sha256: sha256 || undefined,
            consolidatedTo: consolidatedTo || undefined,
          }),
        );
        setProblem(null);
        onImported();
      } catch (error) {
        setResult(null);
        setProblem(refusal(asRefusal(error)));
      }
    },
  });
  return (
    <section aria-label={c.manual} className="flex flex-col gap-3">
      <h2 className="text-lg font-bold text-fg">{c.manual}</h2>
      <p className="text-sm text-fg-secondary">{c.manualLead}</p>
      <form.Form aria-label={c.manual} className="flex flex-col gap-3">
        <form.FileUpload
          name="file"
          label={c.file}
          title={c.fileTitle}
          browseLabel={c.browse}
          accept=".xml,application/xml,text/xml"
          removeLabel={(name) => fill(c.remove, { name })}
        />
        <section className="grid gap-3 sm:grid-cols-3">
          <form.TextField name="jurisdiction" label={c.jurisdiction} autoComplete="off" />
          <form.TextField name="instrument" label={c.code} autoComplete="off" />
          <form.Select
            name="language"
            label={c.language}
            options={LAW_LANGUAGES.map((value) => ({ value, label: t.languages[value] }))}
          />
        </section>
        <form.TextField name="datasetUrl" label={c.datasetUrl} type="url" autoComplete="off" />
        <form.TextField name="sha256" label={c.sha256Optional} autoComplete="off" />
        <form.DateField name="consolidatedTo" label={c.consolidatedToOptional} entry="parts" labels={t.dateParts} />
        <form.FormError />
        <form.Submit variant="primary">{c.importSubmit}</form.Submit>
      </form.Form>
      {problem && <Alert tone="danger">{problem}</Alert>}
      {result && (
        <Alert
          tone={result.failures.length > 0 ? "warning" : "success"}
          title={fill(c.imported, { stored: result.stored, unchanged: result.unchanged })}
        >
          {result.failures.length > 0 && (
            <>
              <p className="font-semibold">{c.importFailures}</p>
              <ul className="font-mono text-xs">
                {result.failures.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
            </>
          )}
        </Alert>
      )}
    </section>
  );
}
