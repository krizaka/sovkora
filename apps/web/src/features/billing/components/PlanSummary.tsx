"use client";

import Link from "next/link";
import { formatCredits, type Plan } from "@krizaka/orazaka-shared";
import { useTranslation } from "@/core/context/LocaleContext";
import { fill } from "@/core/context/fill";

interface PlanSummaryProps {
  plan: Plan | null;
  loading: boolean;
  /** How many Studios run now, and how many a pack or another plan would open (from the Studio list). */
  ready: number;
  more: number;
  href: string;
}

/**
 * The first thing on /studios: what the plan gives (its name and monthly credits), how many Studios that makes ready,
 * how many more exist, and the way to « Plan & packs ». One line, so it answers before the list starts.
 */
export function PlanSummary({ plan, loading, ready, more, href }: Readonly<PlanSummaryProps>) {
  const { t } = useTranslation();
  const p = t.plan;

  let name = p.unknown;
  if (loading) name = p.loading;
  else if (plan) name = fill(p.name, { plan: plan.label });

  return (
    <p
      aria-live="polite"
      className="flex flex-wrap items-baseline gap-x-4 gap-y-1 rounded-lg border border-border-subtle bg-surface-1 px-3 py-2 text-sm text-fg-secondary"
    >
      <strong className="font-semibold text-fg">{name}</strong>
      {plan && plan.monthlyCreditGrant > 0 && (
        <span className="hidden sm:inline">
          {fill(p.creditsMonthly, {
            n: formatCredits(plan.monthlyCreditGrant),
          })}
        </span>
      )}
      <span className="tabular-nums">{fill(p.ready, { n: ready })}</span>
      {more > 0 && <span className="hidden tabular-nums md:inline">{fill(p.more, { n: more })}</span>}
      <Link href={href} className="ml-auto font-medium text-fg-accent underline-offset-4 hover:underline">
        {p.link}
      </Link>
    </p>
  );
}
