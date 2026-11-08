"use client";

import { BarChart } from "@krizaka/charts/bar";
import { DonutChart } from "@krizaka/charts/pie";
import { Card } from "@krizaka/ui/card";
import { consoleCopy } from "@/console/core/copy/console";
import type { byCapability, consumers } from "@/console/features/supervision/utils/supervision";

interface UsageChartsProps {
  capabilities: ReturnType<typeof byCapability>;
  top: ReturnType<typeof consumers>;
  loading?: boolean;
  error?: boolean;
}

/**
 * The ledger, drawn: where the credits went (a donut, the total in its hole), requests against refusals per capability
 * (a refused share that grows says a price or a paywall is wrong), and the heaviest consumers. Each chart carries its
 * data table and a written summary.
 */
export function UsageCharts({ capabilities, top, loading, error }: Readonly<UsageChartsProps>) {
  const s = consoleCopy.supervision;
  return (
    <section className="grid gap-4 lg:grid-cols-3">
      <Card.Root>
        <Card.Body padding="lg">
          <DonutChart
            title={s.creditsByCapability}
            description={s.creditsByCapabilityDesc}
            data={capabilities}
            label="capability"
            value="credits"
            labelHeader={s.capability}
            valueHeader={s.credits}
            centerLabel={s.credits.toLowerCase()}
            format="integer"
            height={220}
            loading={loading}
            error={error && capabilities.length === 0}
            exportable={["csv", "png"]}
          />
        </Card.Body>
      </Card.Root>
      <Card.Root>
        <Card.Body padding="lg">
          <BarChart
            title={s.requestsByCapability}
            description={s.requestsByCapabilityDesc}
            data={capabilities}
            x="capability"
            xLabel={s.capability}
            series={[
              { key: "events", label: s.requests, color: "accent" },
              { key: "refusals", label: s.refusals, color: "danger" },
            ]}
            format="integer"
            stacked
            height={220}
            loading={loading}
            error={error && capabilities.length === 0}
            exportable={["csv", "png"]}
          />
        </Card.Body>
      </Card.Root>
      <Card.Root>
        <Card.Body padding="lg">
          <BarChart
            title={s.topConsumers}
            description={s.topConsumersDesc}
            data={top}
            x="actor"
            xLabel={s.actor}
            y="credits"
            yLabel={s.credits}
            format="integer"
            horizontal
            height={Math.max(180, top.length * 36)}
            loading={loading}
            error={error && top.length === 0}
            exportable={["csv"]}
          />
        </Card.Body>
      </Card.Root>
    </section>
  );
}
