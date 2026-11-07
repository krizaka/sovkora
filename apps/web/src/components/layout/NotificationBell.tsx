"use client";

import * as React from "react";
import Link from "next/link";
import { useJobStream } from "@/core/context/JobStreamContext";
import { useTranslation } from "@/core/context/LocaleContext";
import { formatDistanceToNow } from "date-fns";
import { enUS, fr } from "date-fns/locale";
import { CheckIcon, NotificationIcon, WarningIcon } from "@krizaka/icons";
import { JOB_STATUS } from "@/core/constants/http.constants";
import { jobKind, jobLabel, jobMediaUrl, jobPrompt } from "@/core/jobs/jobMedia";

import { Badge } from "@krizaka/ui/badge";
import { Button, IconButton } from "@krizaka/ui/button";
import { Mascot } from "@krizaka/ui/mascots";
import { Popover } from "@krizaka/ui/popover";
import { Spinner } from "@krizaka/ui/spinner";
import type { Job } from "@/core/types/jobs.types";

export interface NotificationBellProps {
  bellOpen: boolean;
  onToggle: (open: boolean) => void;
}

/**
 * The background-task bell: a @krizaka/ui Popover (Radix — placed against the bell, closed by Escape or an outside
 * click, focus returned to the bell). Its open state stays controlled by the Header, which closes its other menus
 * when this one opens.
 */
export const NotificationBell: React.FC<NotificationBellProps> = ({ bellOpen, onToggle }) => {
  const { activeJobsCount, lastJobs } = useJobStream();
  const { t } = useTranslation();

  return (
    <Popover.Root open={bellOpen} onOpenChange={onToggle}>
      <Popover.Trigger asChild>
        <IconButton id="notification-bell" variant="ghost" label={t.notifications.region} className="relative">
          <NotificationIcon size={20} nodeColor={activeJobsCount > 0 ? "var(--kz-accent)" : undefined} />
          {activeJobsCount > 0 && (
            <span className="absolute right-1 top-1 grid h-4 min-w-4 place-items-center rounded-sm bg-accent px-1 text-[10px] font-bold text-on-accent motion-safe:animate-pulse">
              {activeJobsCount}
            </span>
          )}
        </IconButton>
      </Popover.Trigger>

      <Popover.Content align="end" className="w-96 p-1.5">
        <header className="flex items-center justify-between border-b border-border-subtle/80 px-3 py-2">
          <span className="text-sm font-semibold">{t.notifications.title}</span>
          {activeJobsCount > 0 && (
            <Badge tone="accent" dot pulse>
              {activeJobsCount} {t.notifications.active}
            </Badge>
          )}
        </header>
        <section className="scrollbar-thin max-h-96 overflow-y-auto py-1">
          {lastJobs.length === 0 ? (
            <div role="status" className="flex flex-col items-center gap-2 px-4 py-6 text-center">
              <Mascot bird="pigeon" mood="empty" size={72} />
              <p className="text-sm font-semibold text-fg">{t.showcase.notifications.emptyTitle}</p>
              <p className="text-xs text-fg-secondary">{t.showcase.notifications.emptyBody}</p>
            </div>
          ) : (
            <ul className="divide-y divide-border-subtle/40">
              {lastJobs.map((job) => (
                <JobItem key={job.id} job={job} onSelect={() => onToggle(false)} />
              ))}
            </ul>
          )}
        </section>
        <footer className="border-t border-border-subtle/80 p-1.5">
          <Button asChild variant="ghost" size="sm" className="w-full">
            <Link href="/jobs" onClick={() => onToggle(false)}>
              {t.notifications.viewAll}
            </Link>
          </Button>
        </footer>
      </Popover.Content>
    </Popover.Root>
  );
};

/** One recent job: a thumbnail of what it made (or its status), its name, its prompt, when — a link to it. */
function JobItem({ job, onSelect }: Readonly<{ job: Job; onSelect: () => void }>) {
  const { t, locale } = useTranslation();
  const url = jobMediaUrl(job);
  const kind = jobKind(job);
  const prompt = jobPrompt(job);
  const running = job.status === JOB_STATUS.PROCESSING || job.status === JOB_STATUS.PENDING;
  const failed = job.status === JOB_STATUS.FAILED;
  const when = new Date(job.updatedAt ?? job.createdAt);
  let status = <CheckIcon size={18} className="text-fg-success" />;
  if (running) status = <Spinner size="sm" label={t.showcase.notifications.running} />;
  else if (failed) status = <WarningIcon size={18} className="text-fg-danger" />;

  return (
    <li>
      <Link
        href={`/jobs?jobId=${job.id}`}
        onClick={onSelect}
        className="flex w-full items-center gap-3 rounded-lg p-2.5 text-left transition-colors hover:bg-surface-2 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring"
      >
        <span className="grid h-12 w-12 flex-shrink-0 place-items-center overflow-hidden rounded-lg border border-border-subtle bg-media">
          {url && kind === "image" ? (
            /* eslint-disable-next-line @next/next/no-img-element -- a generated asset is a runtime URL. */
            <img src={url} alt="" className="h-full w-full object-cover" loading="lazy" />
          ) : (
            status
          )}
        </span>
        <span className="min-w-0 flex-1">
          <span className="flex items-center gap-2">
            <span className="truncate text-[13px] font-semibold text-fg">{jobLabel(t, job)}</span>
            {Number.isFinite(when.getTime()) && (
              <span className="ml-auto flex-shrink-0 text-[11px] text-fg-muted">
                {formatDistanceToNow(when, { addSuffix: true, locale: locale === "fr" ? fr : enUS })}
              </span>
            )}
          </span>
          <span className="mt-0.5 block truncate text-xs text-fg-secondary">
            {prompt ??
              (running
                ? t.showcase.notifications.running
                : failed
                  ? t.showcase.notifications.failed
                  : t.showcase.notifications.done)}
          </span>
        </span>
      </Link>
    </li>
  );
}
