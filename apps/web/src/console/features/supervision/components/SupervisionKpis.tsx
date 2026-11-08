"use client";

import { GlowCard } from "@krizaka/ui/glow-card";
import { NumberTicker } from "@krizaka/ui/number-ticker";
import { consoleCopy } from "@/console/core/copy/console";

interface SupervisionKpisProps {
  requests: number;
  credits: number;
  /** Refused share of the attempts, 0–1. */
  refusalRate: number;
  /** Open job streams, or null when the job service did not answer. */
  connections: number | null;
}

/** The four figures an operator reads first, each climbing into view and rolling to its next value on reload. */
export function SupervisionKpis({ requests, credits, refusalRate, connections }: Readonly<SupervisionKpisProps>) {
  const k = consoleCopy.supervision.kpi;
  const cards = [
    { label: k.requests, hint: k.requestsHint, value: requests, format: undefined },
    { label: k.credits, hint: k.creditsHint, value: credits, format: undefined },
    {
      label: k.refusals,
      hint: k.refusalsHint,
      value: refusalRate,
      format: { style: "percent", maximumFractionDigits: 1 } as const,
    },
    { label: k.live, hint: k.liveHint, value: connections, format: undefined },
  ];
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((card) => (
        <li key={card.label}>
          <GlowCard glow="border" className="flex h-full flex-col gap-2 rounded-xl p-5">
            <span className="text-xs font-semibold text-fg-muted">{card.label}</span>
            <span className="font-display text-3xl font-bold tracking-tight text-fg">
              {card.value === null ? (
                "—"
              ) : (
                <NumberTicker value={card.value} locale="en-US" format={card.format} trigger="change" align="start" />
              )}
            </span>
            <span className="text-xs text-fg-secondary">{card.hint}</span>
          </GlowCard>
        </li>
      ))}
    </ul>
  );
}
