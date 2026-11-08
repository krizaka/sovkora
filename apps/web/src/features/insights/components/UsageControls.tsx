"use client";

import type { Run } from "@krizaka/orazaka-shared";
import { ChevronDownIcon } from "@krizaka/icons";
import { Button } from "@krizaka/ui/button";
import { DropdownMenu } from "@krizaka/ui/dropdown-menu";
import { Tabs } from "@krizaka/ui/tabs";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { systemNow } from "@/core/jobs/systemClock";
import { DEFAULT_PERIOD_KEY, PERIOD_KEYS, PERIODS, usageCsv, usageJson } from "@/core/jobs/usageStats";
import type { Job } from "@/core/types/jobs.types";

/** The period (7 / 30 / 90 days) that drives every figure and chart of the page: `?period=` in the URL, read with `useRouteState("period", …)`. */
export function PeriodTabs() {
  const { t } = useTranslation();
  const u = t.insights.usage;
  return (
    <Tabs.Root variant="segmented" routeKey="period" routeValues={PERIOD_KEYS} defaultValue={DEFAULT_PERIOD_KEY}>
      <Tabs.List aria-label={u.period}>
        {PERIODS.map((days) => (
          <Tabs.Trigger key={days} value={String(days)}>
            {fill(u.periodOption, { days })}
          </Tabs.Trigger>
        ))}
      </Tabs.List>
    </Tabs.Root>
  );
}

function download(name: string, content: string, type: string) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

interface UsageExportProps {
  jobs: readonly Job[];
  runs: readonly Run[];
  /** The period exported, in days. */
  days: number;
}

/** « Export ▾ »: the period's jobs and Studio runs as CSV (formulas neutralised) or JSON. */
export function UsageExport({ jobs, runs, days }: Readonly<UsageExportProps>) {
  const { t } = useTranslation();
  const u = t.insights.usage;
  const file = fill(u.exportFile, { days });
  return (
    <DropdownMenu.Root modal={false}>
      <DropdownMenu.Trigger asChild>
        <Button variant="secondary" size="sm">
          {u.export}
          <ChevronDownIcon size={12} className="opacity-60" />
        </Button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Content align="end">
        <DropdownMenu.Item
          onSelect={() => download(`${file}.csv`, usageCsv(jobs, runs, days, systemNow()), "text/csv;charset=utf-8")}
        >
          {u.exportCsv}
        </DropdownMenu.Item>
        <DropdownMenu.Item
          onSelect={() => download(`${file}.json`, usageJson(jobs, runs, days, systemNow()), "application/json")}
        >
          {u.exportJson}
        </DropdownMenu.Item>
      </DropdownMenu.Content>
    </DropdownMenu.Root>
  );
}
