"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { formatDistanceToNow } from "date-fns";
import { enUS, fr } from "date-fns/locale";
import { isTerminalRun, type Run } from "@krizaka/orazaka-shared";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { StudioApi } from "@/services/studio.api";

/** How often the list is re-read while something runs: the run page has the live stream, this list only needs to be fresh. */
const REFRESH_MS = 5000;

/**
 * "Your runs in progress": the person can leave a run's page and come back to it from here — the run keeps going on the
 * server. Renders nothing when nothing runs (it is a way back, not a section to fill).
 */
export function RunsInProgress() {
  const { t, locale } = useTranslation();
  const [runs, setRuns] = useState<Run[]>([]);

  const load = useCallback(async () => {
    try {
      setRuns((await StudioApi.fetchRuns()).filter((run) => !isTerminalRun(run.status)));
    } catch {
      // A list that cannot load is not worth an alarm: the run pages and the toasts still work.
    }
  }, []);

  useEffect(() => {
    const first = setTimeout(() => void load(), 0);
    const timer = setInterval(() => void load(), REFRESH_MS);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [load]);

  if (runs.length === 0) return null;
  const w = t.runProgress.inProgress;
  const dateLocale = locale === "fr" ? fr : enUS;
  return (
    <section
      aria-label={w.title}
      className="flex flex-col gap-2 rounded-xl border border-border-default bg-surface-1 p-4"
    >
      <h2 className="text-xs font-semibold text-fg-muted">{w.title}</h2>
      <ul className="flex flex-col gap-2">
        {runs.map((run) => (
          <li key={run.id} className="flex flex-wrap items-center gap-3">
            <Badge tone="accent" size="sm" dot>
              {run.studioKey}
            </Badge>
            <span className="text-xs text-fg-muted">
              {fill(w.since, {
                time: formatDistanceToNow(new Date(run.startedAt), { addSuffix: true, locale: dateLocale }),
              })}
            </span>
            <Button asChild variant="ghost" size="sm" className="ml-auto">
              <Link href={`/studios/runs/${run.id}`}>{w.open}</Link>
            </Button>
          </li>
        ))}
      </ul>
    </section>
  );
}
