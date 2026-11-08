"use client";

import { BarChart } from "@krizaka/charts/bar";
import { Card } from "@krizaka/ui/card";
import { consoleCopy } from "@/console/core/copy/console";
import type { jobsPerDay, latencyByCapability } from "@/console/features/supervision/utils/supervision";

interface ActivityChartsProps {
  perDay: ReturnType<typeof jobsPerDay>;
  latency: ReturnType<typeof latencyByCapability>;
  loading?: boolean;
  error?: boolean;
}

/** The job service, drawn: every workspace's jobs per day by outcome, and the median time to a result per capability. */
export function ActivityCharts({ perDay, latency, loading, error }: Readonly<ActivityChartsProps>) {
  const s = consoleCopy.supervision;
  return (
    <section className="grid gap-4 lg:grid-cols-[1.6fr_1fr]">
      <Card.Root>
        <Card.Body padding="lg">
          <BarChart
            title={s.jobsPerDay}
            description={s.jobsPerDayDesc}
            data={perDay}
            x="day"
            xFormat="day"
            xLabel={s.day}
            series={[
              { key: "completed", label: s.completed, color: "accent" },
              { key: "failed", label: s.failed, color: "danger" },
              { key: "running", label: s.running, color: "neutral" },
            ]}
            format="integer"
            stacked
            height={240}
            loading={loading}
            error={error && perDay.every((d) => d.completed + d.failed + d.running === 0)}
            exportable={["csv", "png"]}
          />
        </Card.Body>
      </Card.Root>
      <Card.Root>
        <Card.Body padding="lg">
          <BarChart
            title={s.latency}
            description={s.latencyDesc}
            data={latency}
            x="capability"
            xLabel={s.capability}
            y="seconds"
            yLabel={s.seconds}
            format={{ style: "unit", unit: "second" }}
            horizontal
            height={Math.max(180, latency.length * 44)}
            loading={loading}
            error={error && latency.length === 0}
          />
        </Card.Body>
      </Card.Root>
    </section>
  );
}
