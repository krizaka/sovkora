"use client";

import { Tabs } from "@krizaka/ui/tabs";
import { studiosCopy } from "@/console/core/copy/studios";
import { useConsoleLocale } from "@/console/core/providers/ConsoleLocale";
import { StudioBuilderScreen } from "@/console/features/studio/components/StudioBuilderScreen";
import { StudioAnalyticsScreen } from "@/console/features/studio/components/StudioAnalyticsScreen";

/**
 * The Studio Builder console (ADR-034 §12.4).
 *
 * This page is the whole claim of the feature: shipping a new profession is an
 * admin action here, never a deploy.
 */
export default function StudiosPage() {
  const words = studiosCopy[useConsoleLocale()].page;
  // Usage opens first, like the billing console: an operator arriving here usually wants to know
  // whether the Studios they already published are working before authoring another.

  return (
    <main className="mx-auto max-w-7xl space-y-8 px-4 py-8 sm:px-6">
      <header>
        <h1 className="font-display text-3xl font-bold tracking-tight text-fg">{words.title}</h1>
        <p className="text-sm text-fg-muted">{words.lead}</p>
      </header>
      <Tabs.Root routeKey="tab" routeValues={["usage", "builder"]}>
        <Tabs.List aria-label={words.title}>
          <Tabs.Trigger value="usage">{words.usage}</Tabs.Trigger>
          <Tabs.Trigger value="builder">{words.builder}</Tabs.Trigger>
        </Tabs.List>
        <Tabs.Content value="usage">
          <StudioAnalyticsScreen />
        </Tabs.Content>
        <Tabs.Content value="builder">
          <StudioBuilderScreen />
        </Tabs.Content>
      </Tabs.Root>
    </main>
  );
}
