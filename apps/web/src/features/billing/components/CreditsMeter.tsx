"use client";

import Link from "next/link";
import { GlowCard } from "@krizaka/ui/glow-card";
import { Meter } from "@krizaka/ui/meter";
import { NumberTicker } from "@krizaka/ui/number-ticker";
import { Skeleton } from "@krizaka/ui/skeleton";
import { formatCredits, type Plan, type Wallet } from "@krizaka/orazaka-shared";
import { fill } from "@/core/context/fill";
import { useTranslation } from "@/core/context/LocaleContext";
import { INTL_LOCALES } from "@/core/constants/locale.constants";

interface CreditsMeterProps {
  /** The balances (billing), or null when the person has never been billed. */
  wallet: Wallet | null;
  /** What can be spent now: the two buckets minus what work in progress holds. */
  available: number;
  /** The plan behind the grant, once billing answered. */
  plan: Plan | null;
  /** Billing has not answered yet. */
  loading?: boolean;
  /** Where the card leads (the credits page from the dashboard; nothing on the credits page itself). */
  href?: string;
}

/**
 * The balance as a figure that climbs into view and rolls to each new value (the wallet is pushed over SSE), split
 * into where the credits come from — the plan's grant, what was bought, what running work holds — and what the plan
 * grants each period.
 */
export function CreditsMeter({ wallet, available, plan, loading, href }: Readonly<CreditsMeterProps>) {
  const { locale, t } = useTranslation();
  const w = t.insights.credits;
  const numberLocale = INTL_LOCALES[locale];
  const grant = plan?.monthlyCreditGrant ?? 0;
  /** Nothing granted, bought or held: a clear next step instead of a zero. */
  const noCredits =
    !loading && available <= 0 && (!wallet || wallet.balanceGranted + wallet.balancePurchased + wallet.held <= 0);

  const body = (
    <section className="flex flex-col gap-5 p-6">
      <header className="flex items-start justify-between gap-4">
        <span className="flex flex-col gap-1">
          <span className="text-xs font-semibold text-fg-muted">{w.available}</span>
          {loading ? (
            <Skeleton className="h-10 w-40" />
          ) : noCredits ? (
            <span className="flex flex-col gap-1">
              <span className="font-display text-2xl font-bold tracking-tight text-fg">
                {grant > 0 ? t.insights.usage.noCreditsLeft : t.insights.usage.noCredits}
              </span>
              <Link href="/packs" className="text-sm font-medium text-fg-accent underline-offset-4 hover:underline">
                {t.insights.usage.noCreditsAction}
              </Link>
            </span>
          ) : (
            <NumberTicker
              value={available}
              locale={numberLocale}
              trigger="change"
              announce
              align="start"
              className="font-display text-4xl font-bold tracking-tight text-fg"
            />
          )}
        </span>
        {plan && (
          <span className="rounded-sm border border-border-default px-3 py-1 text-xs font-medium text-fg-secondary">
            {fill(w.plan, { plan: plan.label })}
          </span>
        )}
      </header>
      {wallet && (
        <Meter
          label={w.meterLabel}
          max={Math.max(1, wallet.balanceGranted + wallet.balancePurchased + wallet.held)}
          parts={[
            { label: w.granted, value: wallet.balanceGranted, valueText: formatCredits(wallet.balanceGranted) },
            { label: w.purchased, value: wallet.balancePurchased, valueText: formatCredits(wallet.balancePurchased) },
            { label: w.held, value: wallet.held, valueText: formatCredits(wallet.held) },
          ]}
        />
      )}
      {grant > 0 && <p className="text-xs text-fg-muted">{fill(w.perPeriod, { grant: formatCredits(grant) })}</p>}
    </section>
  );

  return (
    <GlowCard glow="border" asChild={Boolean(href)} className="rounded-xl">
      {href ? (
        <Link href={href} aria-label={w.title}>
          {body}
        </Link>
      ) : (
        body
      )}
    </GlowCard>
  );
}
