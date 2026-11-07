"use client";

import * as React from "react";
import { CheckIcon, CopyIcon } from "@krizaka/icons";
import { Button, IconButton } from "@krizaka/ui/button";
import { Dialog } from "@krizaka/ui/dialog";
import { Tabs } from "@krizaka/ui/tabs";
import type { JobModalProps } from "@/app/jobs/jobModal.types";
import { useTranslation } from "@/core/context/LocaleContext";
import { ResultDisplay } from "@/app/jobs/ResultDisplay";
import { jobLabel } from "@/core/jobs/jobMedia";
import { ProgressTimeline, StageLabels, StatusPill, LiveElapsed } from "./JobModalParts";
import type { TabKey } from "./JobModalParts";
import { JOB_STATUS } from "@/core/constants/http.constants";

/** A JSON block with its copy button (the @krizaka/ui icon button). */
function JsonBlock({
  text,
  copied,
  onCopy,
  label,
}: Readonly<{ text: string; copied: boolean; onCopy: () => void; label: string }>) {
  return (
    <section className="relative">
      <pre className="scrollbar-thin max-h-96 overflow-auto rounded-xl border border-border-subtle bg-surface-2 p-4 font-mono text-[11px] leading-relaxed text-fg">
        {text}
      </pre>
      <IconButton
        variant="secondary"
        size="sm"
        className="absolute right-2 top-2 w-8 px-0"
        label={label}
        onClick={onCopy}
      >
        {copied ? <CheckIcon size={14} className="text-fg-success" /> : <CopyIcon size={14} />}
      </IconButton>
    </section>
  );
}

/**
 * A job's details on the @krizaka/ui dialog (focus trap, Escape, scroll lock, return focus) and tabs (arrow keys):
 * the result first — what it made — then its input and, when it failed, its error.
 */
export const JobModal: React.FC<JobModalProps> = ({ job, modalType, onClose, copiedId, onCopy }) => {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = React.useState<TabKey>(modalType === "result" ? "result" : "payload");

  if (!job || !modalType) return null;

  const isLive = job.status === JOB_STATUS.PROCESSING || job.status === JOB_STATUS.PENDING;
  const payload = JSON.stringify(job.payload, null, 2);
  const result = JSON.stringify(job.result, null, 2);

  return (
    <Dialog.Root open onOpenChange={(open) => !open && onClose()}>
      <Dialog.Content size="lg" closeLabel={t.a11y.closeDetails} className="max-h-[85vh]">
        <Dialog.Header className="flex-row flex-wrap items-center gap-3 pr-12">
          <StatusPill status={job.status} />
          <div className="flex min-w-0 flex-col">
            <Dialog.Title className="text-base">{jobLabel(t, job)}</Dialog.Title>
            <Dialog.Description className="truncate font-mono text-[11px]">
              {job.featureKey} · {job.id}
            </Dialog.Description>
          </div>
          <span className="ml-auto">
            <LiveElapsed createdAt={job.createdAt} updatedAt={job.updatedAt} isLive={isLive} />
          </span>
        </Dialog.Header>

        <Dialog.Body className="flex flex-col gap-4 overflow-y-auto">
          <div>
            <ProgressTimeline status={job.status} />
            <StageLabels status={job.status} />
          </div>

          {/* @ephemeral: the result / payload switch of an open job modal, gone with the modal */}
          <Tabs.Root value={activeTab} onValueChange={(value) => setActiveTab(value as TabKey)} className="gap-4">
            <Tabs.List aria-label={jobLabel(t, job)}>
              {job.status === JOB_STATUS.COMPLETED && (
                <Tabs.Trigger value="result">{t.jobs.resultModalTitle}</Tabs.Trigger>
              )}
              <Tabs.Trigger value="payload">{t.jobs.payloadModalTitle}</Tabs.Trigger>
              {job.status === JOB_STATUS.FAILED && job.errorMessage && (
                <Tabs.Trigger value="error">{t.jobs.errorDetails}</Tabs.Trigger>
              )}
            </Tabs.List>

            <Tabs.Content value="result" className="flex flex-col gap-4">
              <section className="rounded-xl border border-border-subtle bg-surface-2 p-4">
                <ResultDisplay payload={job.result} />
              </section>
              <details className="text-[11px] text-fg-muted">
                <summary className="cursor-pointer select-none font-semibold hover:text-fg-secondary">
                  {t.showcase.jobs.rawJson}
                </summary>
                <div className="mt-2">
                  <JsonBlock
                    text={result}
                    copied={copiedId === "modal-json"}
                    onCopy={() => onCopy(result, "modal-json")}
                    label={t.a11y.copy}
                  />
                </div>
              </details>
            </Tabs.Content>
            <Tabs.Content value="payload">
              <JsonBlock
                text={payload}
                copied={copiedId === "modal-json"}
                onCopy={() => onCopy(payload, "modal-json")}
                label={t.a11y.copy}
              />
            </Tabs.Content>
            <Tabs.Content value="error">
              <JsonBlock
                text={job.errorMessage ?? ""}
                copied={copiedId === "modal-json"}
                onCopy={() => onCopy(job.errorMessage ?? "", "modal-json")}
                label={t.a11y.copy}
              />
            </Tabs.Content>
          </Tabs.Root>
        </Dialog.Body>

        <Dialog.Footer className="justify-between">
          <Button variant="ghost" size="sm" onClick={() => onCopy(String(job.id), "modal-id")}>
            {copiedId === "modal-id" ? <CheckIcon size={14} className="text-fg-success" /> : <CopyIcon size={14} />}
            {copiedId === "modal-id" ? t.jobs.copied : t.showcase.jobs.copyId}
          </Button>
          <Button variant="secondary" size="sm" onClick={onClose}>
            {t.showcase.jobs.close}
          </Button>
        </Dialog.Footer>
      </Dialog.Content>
    </Dialog.Root>
  );
};
