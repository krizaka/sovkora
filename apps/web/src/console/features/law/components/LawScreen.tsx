"use client";

import { Tabs } from "@krizaka/ui/tabs";
import { useLawCopy } from "@/console/features/law/hooks/useLawCopy";
import { ProposalsPanel } from "@/console/features/law/components/ProposalsPanel";
import { CorpusPanel } from "@/console/features/law/components/CorpusPanel";
import { DemandPanel } from "@/console/features/law/components/DemandPanel";

const TABS = ["proposals", "corpus", "demand"] as const;

/**
 * Law → Proposals (ADR-089 §8): the review queue a rule passes before it reaches a pack, the corpus it is checked
 * against, and the demand that says which kind to derive next. The tab is the URL (`?tab=`), like the queue's filters.
 */
export function LawScreen() {
  const { t } = useLawCopy();
  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">{t.title}</h1>
        <p className="max-w-3xl text-sm text-fg-secondary">{t.lead}</p>
      </header>
      <Tabs.Root routeKey="tab" routeValues={TABS} defaultValue="proposals">
        <Tabs.List aria-label={t.tabs.label} className="overflow-x-auto">
          {TABS.map((tab) => (
            <Tabs.Trigger key={tab} value={tab}>
              {t.tabs[tab]}
            </Tabs.Trigger>
          ))}
        </Tabs.List>
        <Tabs.Content value="proposals">
          <ProposalsPanel />
        </Tabs.Content>
        <Tabs.Content value="corpus">
          <CorpusPanel />
        </Tabs.Content>
        <Tabs.Content value="demand">
          <DemandPanel />
        </Tabs.Content>
      </Tabs.Root>
    </article>
  );
}
