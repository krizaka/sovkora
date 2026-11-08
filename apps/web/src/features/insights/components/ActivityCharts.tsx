"use client";

import * as React from "react";
import { BarChart } from "@krizaka/charts/bar";
import { ChartCard } from "@krizaka/charts/card";
import { DonutChart } from "@krizaka/charts/pie";
import { fill } from "@/core/context/fill";
import { INTL_LOCALES } from "@/core/constants/locale.constants";
import { useTranslation } from "@/core/context/LocaleContext";
import { jobsByKind } from "@/core/jobs/jobInsights";
import { changeOf, formatChange, type UsageSummary } from "@/core/jobs/usageStats";
import type { Job } from "@/core/types/jobs.types";

interface ActivityChartsProps {
  summary: UsageSummary;
  /** The jobs of the period (the donut's data). */
  jobs: readonly Job[];
  /** The length of the period, in days. */
  days: number;
  loading?: boolean;
  error?: boolean;
}

/**
 * Two ChartCards of the person's own work: the jobs of each day by outcome (stacked bars zoomed on the days that have
 * data; a failure is a muted red, never a block of alarm), and what they made by kind (a donut whose legend filters).
 * Each states what it shows in one line, and carries its actions menu (table, CSV, PNG, SVG, copy).
 */
export function ActivityCharts({ summary, jobs, days, loading, error }: Readonly<ActivityChartsProps>) {
  const { locale, t } = useTranslation();
  const w = t.insights;
  const tag = INTL_LOCALES[locale];
  const kinds = React.useMemo(() => jobsByKind(jobs).map((k) => ({ ...k, label: w.kinds[k.kind] })), [jobs, w]);
  const { current, previous } = summary;
  const change = formatChange(changeOf(current.jobs, previous.jobs), tag);
  const changedInsight = change
    ? fill(w.usage.jobsInsight, { jobs: current.jobs, change })
    : fill(w.usage.jobsInsightFlat, { jobs: current.jobs });
  const perDayInsight = current.jobs === 0 ? w.usage.jobsInsightNone : changedInsight;
  const top = kinds[0];

  return (
    <section className="grid gap-4 lg:grid-cols-[1.6fr_1fr]">
      <ChartCard
        title={w.jobsPerDay}
        insight={perDayInsight}
        description={fill(w.jobsPerDayDesc, { days })}
        footer={w.usage.source}
      >
        <BarChart
          data={current.jobs === 0 ? [] : summary.perDay}
          x="day"
          xFormat="day"
          xLabel={w.day}
          series={[
            { key: "completed", label: w.completed, color: "accent" },
            { key: "failed", label: w.failed, color: "danger" },
            { key: "running", label: w.running, color: "neutral" },
          ]}
          format="integer"
          stacked
          autoRange
          height={220}
          loading={loading}
          error={error}
        />
      </ChartCard>
      <ChartCard
        title={w.byKind}
        insight={
          top ? fill(w.usage.kindInsight, { kind: top.label.toLowerCase(), count: top.count }) : w.usage.kindInsightNone
        }
        description={w.byKindDesc}
      >
        <DonutChart
          data={kinds}
          label="label"
          value="count"
          labelHeader={w.kindHeader}
          valueHeader={w.jobsHeader}
          centerLabel={w.jobsCenter}
          format="integer"
          height={220}
          loading={loading}
          error={error}
        />
      </ChartCard>
    </section>
  );
}
