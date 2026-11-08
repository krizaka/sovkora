"use client";

import * as React from "react";
import { Button } from "@krizaka/ui/button";
import { useTranslation } from "@/core/context/LocaleContext";
import { useAutomationMutations, useAutomations, useRunnableStudios } from "@/features/automation/hooks/useAutomations";
import { AutomationCard } from "./AutomationCard";
import { NewAutomationDialog } from "./NewAutomationDialog";

/**
 * The automations of the signed-in user (ADR-084): what runs, how it starts (a signed webhook, a schedule) and what it
 * did. Real data from the automation service — nothing here is a demo.
 */
export function AutomationsSection() {
  const { t } = useTranslation();
  const a = t.automations;
  const automations = useAutomations();
  const studios = useRunnableStudios();
  const mutations = useAutomationMutations();
  const [creating, setCreating] = React.useState(false);
  const labels = new Map((studios.data ?? []).map((studio) => [studio.studioKey, studio.label]));
  const busy = mutations.setEnabled.isPending || mutations.remove.isPending;

  return (
    <section id="automations" className="flex flex-col gap-4 py-8">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <span className="flex flex-col gap-1">
          <h2 className="hud-title text-2xl text-fg">{a.title}</h2>
          <p className="text-sm text-fg-secondary">{a.subtitle}</p>
        </span>
        <Button type="button" size="sm" onClick={() => setCreating(true)}>
          {a.create}
        </Button>
      </header>

      {automations.isLoading && <p className="text-sm text-fg-secondary">{a.loading}</p>}
      {automations.isError && (
        <div role="alert" className="flex items-center gap-3 text-sm text-danger">
          <span>{a.loadError}</span>
          <Button type="button" size="sm" variant="secondary" onClick={() => void automations.refetch()}>
            {a.retry}
          </Button>
        </div>
      )}
      {automations.data?.length === 0 && (
        <div className="rounded-lg border border-dashed border-border-subtle p-6 text-center">
          <p className="text-sm font-medium text-fg">{a.emptyTitle}</p>
          <p className="text-xs text-fg-muted">{a.emptyDesc}</p>
        </div>
      )}
      <ul className="flex flex-col gap-3">
        {(automations.data ?? []).map((automation) => (
          <li key={automation.id}>
            <AutomationCard
              automation={automation}
              studioLabel={labels.get(automation.studioKey) ?? automation.studioKey}
              busy={busy}
              onEnabled={(enabled) => mutations.setEnabled.mutate({ id: automation.id, enabled })}
              onDelete={() => {
                if (window.confirm(a.deleteConfirm)) mutations.remove.mutate(automation.id);
              }}
            />
          </li>
        ))}
      </ul>
      <NewAutomationDialog open={creating} onClose={() => setCreating(false)} />
    </section>
  );
}
