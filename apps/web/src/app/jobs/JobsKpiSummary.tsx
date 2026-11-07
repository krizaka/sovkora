"use client";

import { KpiStat } from "@krizaka/charts/card";
import { StatGrid } from "@krizaka/ui/stat";
import * as React from "react";

import { INTL_LOCALES } from "@/core/constants/locale.constants";
import { useTranslation } from "@/core/context/LocaleContext";
import { Job } from "@/core/types/jobs.types";

import { computeJobStats } from "./jobStats.utils";

interface JobsKpiSummaryProps {
  jobs: Job[];
}

const SECONDS = { style: "unit", unit: "second" } as const;

/** The jobs at a glance: the kit's KpiStat in a StatGrid (one look with the usage view; cards on a phone). */
export const JobsKpiSummary: React.FC<JobsKpiSummaryProps> = ({ jobs }) => {
  const { locale, t } = useTranslation();
  const stats = React.useMemo(() => computeJobStats(jobs), [jobs]);
  const intl = INTL_LOCALES[locale];

  return (
    <StatGrid minWidth={11}>
      <KpiStat
        label={t.jobs.kpiTotalJobs}
        value={stats.total}
        format="integer"
        hint={`${stats.completed} completed`}
        locale={intl}
      />
      <KpiStat
        label={t.jobs.kpiSuccessRate}
        value={stats.total > 0 ? stats.completed / stats.total : 0}
        format="percent"
        hint={`${stats.failed} failed`}
        locale={intl}
      />
      <KpiStat
        label="Avg. Duration"
        value={stats.avgMs / 1000}
        format={SECONDS}
        good="down"
        hint={`max ${stats.maxDuration}`}
        locale={intl}
      />
      <KpiStat
        label={t.jobs.kpiModelsUsed}
        value={stats.uniqueModels}
        format="integer"
        hint={stats.topModel || "—"}
        locale={intl}
      />
      <KpiStat
        label={t.jobs.kpiCapabilities}
        value={stats.uniqueFeatures}
        format="integer"
        hint={stats.topFeature || "—"}
        locale={intl}
      />
      <KpiStat
        label={t.jobs.kpiTotalInference}
        value={stats.totalMs / 1000}
        format={SECONDS}
        hint={`across ${stats.completed} jobs`}
        locale={intl}
      />
    </StatGrid>
  );
};
