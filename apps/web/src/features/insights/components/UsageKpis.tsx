"use client";

import { KpiStat } from "@krizaka/charts/card";
import { Skeleton } from "@krizaka/ui/skeleton";
import { StatGrid } from "@krizaka/ui/stat";
import { fill } from "@/core/context/fill";
import { INTL_LOCALES } from "@/core/constants/locale.constants";
import { useTranslation } from "@/core/context/LocaleContext";
import type { UsageSummary } from "@/core/jobs/usageStats";

interface UsageKpisProps {
  summary: UsageSummary;
  /** The length of the period, in days. */
  days: number;
  loading?: boolean;
}

/**
 * The head of a usage view: jobs, success rate, time to a result and Studio runs — each with its change against the
 * period before and the shape of the period (the kit's `Stat` with a sparkline, @krizaka/charts).
 */
export function UsageKpis({ summary, days, loading }: Readonly<UsageKpisProps>) {
  const { locale, t } = useTranslation();
  const u = t.insights.usage;
  const { current, previous, perDay } = summary;
  if (loading) {
    return (
      <StatGrid minWidth={13}>
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-28 w-full" />
        ))}
      </StatGrid>
    );
  }
  const jobsPerDay = perDay.map((d) => d.completed + d.failed + d.running);
  const hint = fill(u.kpiPeriodHint, { days });
  return (
    <StatGrid minWidth={13}>
      <KpiStat
        label={u.kpiJobs}
        value={current.jobs}
        previous={previous.jobs}
        data={jobsPerDay}
        categories={perDay.map((d) => d.day)}
        variant="bar"
        hint={hint}
        format="integer"
        locale={INTL_LOCALES[locale]}
      />
      <KpiStat
        label={u.kpiSuccess}
        value={current.successRate ?? 0}
        previous={previous.successRate ?? undefined}
        format="percent"
        hint={current.successRate === null ? u.jobsInsightNone : u.kpiSuccessHint}
        locale={INTL_LOCALES[locale]}
      />
      <KpiStat
        label={u.kpiTime}
        value={current.medianSeconds ?? 0}
        previous={previous.medianSeconds ?? undefined}
        good="down"
        format={{ style: "unit", unit: "second" }}
        hint={current.medianSeconds === null ? u.timeInsightNone : u.kpiTimeHint}
        locale={INTL_LOCALES[locale]}
      />
      <KpiStat
        label={u.kpiRuns}
        value={current.runs}
        previous={previous.runs}
        hint={hint}
        format="integer"
        locale={INTL_LOCALES[locale]}
      />
    </StatGrid>
  );
}
