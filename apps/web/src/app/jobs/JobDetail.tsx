"use client";

import { Button } from "@krizaka/ui/button";
import { DescriptionList } from "@krizaka/ui/description-list";
import { CheckIcon, CopyIcon } from "@krizaka/icons";
import { JOB_STATUS } from "@/core/constants/http.constants";
import { useTranslation } from "@/core/context/LocaleContext";
import { MODEL_NAMES } from "./modelNames";
import { type FactSection, jobFacts, promptOf } from "@/features/jobs/utils/jobFacts";
import type { Job } from "@/core/types/jobs.types";

const ORDER: FactSection[] = ["output", "generation", "hardware", "timing", "other"];

/**
 * The facts of a job, grouped and in words — Output · Generation · Hardware · Timing — under its row: formatted values
 * with units (never the worker's raw keys), the prompt, and for a failure what went wrong, copyable.
 */
export function JobDetail({
  job,
  copiedId,
  onCopy,
}: Readonly<{ job: Job; copiedId: string | null; onCopy: (text: string, id: string) => void }>) {
  const { t } = useTranslation();
  const d = t.jobsTable;
  const facts = jobFacts(job, { modelNames: MODEL_NAMES });
  const prompt = promptOf(job);
  const sections = ORDER.map((section) => ({ section, items: facts.filter((f) => f.section === section) })).filter(
    (s) => s.items.length > 0,
  );
  const errorId = `err-${job.id}`;

  return (
    <div className="flex flex-col gap-4 p-1 text-left">
      {prompt && (
        <section aria-label={d.sections.prompt} className="flex flex-col gap-1">
          <h4 className="text-xs font-semibold text-fg-muted">{d.sections.prompt}</h4>
          <p className="m-0 text-sm text-fg">{prompt}</p>
        </section>
      )}
      {job.status === JOB_STATUS.FAILED && job.errorMessage && (
        <section
          aria-label={d.sections.error}
          className="flex flex-col gap-2 rounded-lg border border-danger/30 bg-danger/10 p-3"
        >
          <div className="flex items-center justify-between gap-2">
            <h4 className="text-xs font-semibold text-danger">{d.sections.error}</h4>
            <Button variant="ghost" size="sm" onClick={() => onCopy(job.errorMessage ?? "", errorId)}>
              {copiedId === errorId ? <CheckIcon size={14} /> : <CopyIcon size={14} />}
              {copiedId === errorId ? d.actions.copied : d.actions.copyError}
            </Button>
          </div>
          <pre className="m-0 max-w-full overflow-x-auto whitespace-pre-wrap font-mono text-xs text-danger">
            {job.errorMessage}
          </pre>
        </section>
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {sections.map(({ section, items }) => (
          <section key={section} aria-label={d.sections[section]} className="flex min-w-0 flex-col gap-1">
            <h4 className="text-xs font-semibold text-fg-muted">{d.sections[section]}</h4>
            <DescriptionList
              layout="inline"
              items={items.map((f) => ({
                id: f.id,
                term: f.label ?? d.facts[f.id as keyof typeof d.facts],
                details: f.value,
              }))}
            />
          </section>
        ))}
      </div>
    </div>
  );
}
