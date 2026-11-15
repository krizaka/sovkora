import { useState } from "react";
import { useQuery } from "@krizaka/query";
import { Timeline, type ActivityEvent, type ActivityTone } from "@krizaka/ui/activity-feed";
import { Button } from "@krizaka/ui/button";
import { Card } from "@krizaka/ui/card";
import { automationsApi, slotLabel, type Run, type RunSummary, type StepStatus } from "../../lib/api";
import { useI18n } from "../../lib/i18n";
import { RunStatusBadge } from "./RunStatusBadge";

const STEP_TONE = { ok: "success", error: "danger", skipped: "neutral", "dry-run": "info", waiting: "warning" } as const satisfies Record<StepStatus, ActivityTone>;

/** Run history, and one run's trace step by step (each step: a tool call, a model call, the approval gate). */
export function RunHistory({ runs }: Readonly<{ runs: RunSummary[] }>) {
  const { t, format } = useI18n();
  const [open, setOpen] = useState<string | null>(null);
  const dateLocale = t("common.dateLocale");
  const latest = runs.find((r) => r.id === open);

  // The trace of the open run, read again when its summary moves (status, steps) while the run goes on. Keyed by the run,
  // so the answer for a run since closed or replaced never shows; the previous trace of the same run stays until the new one.
  const trace = useQuery({
    queryKey: ["automations", "run", open, latest?.status, latest?.steps],
    enabled: open !== null,
    retry: false,
    queryFn: () => automationsApi.run(open as string),
    placeholderData: (previous, previousQuery) => (previousQuery?.queryKey[2] === open ? previous : undefined),
  });
  const run: Run | null = open ? (trace.data ?? null) : null;

  const events: ActivityEvent[] = (run?.steps ?? []).map((s) => ({
    id: `${s.seq}`,
    at: s.at,
    title: `${s.name} · ${s.detail}`,
    description: s.durationMs > 0 ? format(t("automations.runs.duration"), { seconds: (s.durationMs / 1000).toFixed(1) }) : undefined,
    tone: STEP_TONE[s.status],
  }));

  return (
    <section aria-labelledby="runs-title" className="space-y-3">
      <h3 id="runs-title" className="text-sm font-semibold">{t("automations.runs.title")}</h3>
      {runs.length === 0 && <p className="text-xs text-fg-secondary">{t("automations.runs.none")}</p>}
      <ul className="space-y-2">
        {runs.slice(0, 20).map((r) => (
          <li key={r.id}>
            <Card.Root data-testid="run">
              <Card.Body padding="sm">
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <RunStatusBadge status={r.status} />
                  <span className="font-medium">{r.automationName}</span>
                  <span className="text-xs text-fg-secondary">
                    {t(`automations.runs.trigger.${r.trigger}`)} · {slotLabel(r.startedAt, dateLocale)}
                  </span>
                  <span className="text-xs text-fg-secondary">
                    {format(t("automations.runs.tokens"), { prompt: r.usage.promptTokens, completion: r.usage.completionTokens })}
                    {r.written > 0 && ` · ${format(t("automations.runs.written"), { count: r.written })}`}
                  </span>
                  <Button size="sm" variant="ghost" className="ml-auto" data-testid="run-trace" aria-expanded={open === r.id} onClick={() => setOpen(open === r.id ? null : r.id)}>
                    {open === r.id ? t("automations.runs.close") : t("automations.runs.trace")}
                  </Button>
                </div>
                {open === r.id && run && (
                  <div className="space-y-2 border-t border-border-subtle pt-3">
                    <p className="font-mono text-[11px] text-fg-secondary">
                      {format(t("automations.runs.job"), { id: run.jobId })} · {run.featureKey} · {format(t("automations.runs.credits"), { credits: run.usage.credits })}
                    </p>
                    <Timeline events={events} aria-label={t("automations.runs.trace")} locale={dateLocale} timeStyle="time" />
                  </div>
                )}
              </Card.Body>
            </Card.Root>
          </li>
        ))}
      </ul>
    </section>
  );
}
