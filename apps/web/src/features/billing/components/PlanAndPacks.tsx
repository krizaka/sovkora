"use client";

import Link from "next/link";
import { formatCredits, type PackSummary } from "@krizaka/orazaka-shared";
import { Button } from "@krizaka/ui/button";
import { List } from "@krizaka/ui/list";
import { CatalogueIcon } from "@/components/icons/CatalogueIcon";
import { useTranslation } from "@/core/context/LocaleContext";
import { fill } from "@/core/context/fill";
import { useDrawerParam } from "@/core/hooks/useDrawerParam";
import { useMoney } from "@/core/hooks/useMoney";
import { PackDrawer } from "@/features/billing/components/PackDrawer";
import { usePacks } from "@/features/billing/hooks/usePacks";
import { usePlan } from "@/features/billing/hooks/usePlan";

type StudioNames = ReadonlyMap<string, { label: string; iconKey: string }>;

/**
 * « Plan & packs » (/packs): the money side of the Studios, in three short blocks. Your plan (name, price, monthly
 * credits), the packs you hold or your plan includes, and the others, one line each with what it adds; « Details »
 * opens the pack's drawer (`?pack=`), where it is added or removed. The Studios themselves live on /studios.
 */
export function PlanAndPacks({ studios }: Readonly<{ studios: StudioNames }>) {
  const { t, locale } = useTranslation();
  const p = t.plan;
  const money = useMoney();
  const { plan, isLoading: planLoading } = usePlan();
  const { packs, ownedKeys, holdingsUnavailable, isLoading, error, pendingKey, reload, subscribe, remove } = usePacks(
    locale,
    t.packs.loadError,
  );
  const drawer = useDrawerParam("pack");

  const isMine = (pack: PackSummary) => pack.access === "INCLUDED" || ownedKeys.has(pack.packKey);
  const mine = packs.filter(isMine);
  const more = packs.filter((pack) => !isMine(pack));
  const open = packs.find((pack) => pack.packKey === drawer.value) ?? null;

  const priceOf = (pack: PackSummary) => {
    if (pack.access === "INCLUDED") return p.included;
    if (ownedKeys.has(pack.packKey)) return p.owned;
    if (pack.priceCents === null) return p.priceUnavailable;
    return pack.priceCents === 0 ? p.free : money(pack.priceCents);
  };
  const packList = (items: PackSummary[], label: string) => (
    <List
      aria-label={label}
      density="compact"
      loading={isLoading}
      loadingRows={3}
      items={items}
      getId={(pack) => pack.packKey}
      row={(pack) => ({
        title: pack.label,
        description: [
          fill(pack.studioKeys.length === 1 ? p.studiosCount.one : p.studiosCount.other, { n: pack.studioKeys.length }),
          pack.studioKeys.map((k) => studios.get(k)?.label ?? k).join(", "),
        ].join(" · "),
        leading: <CatalogueIcon iconKey={pack.iconKey} size={18} />,
        meta: <span className="hidden sm:inline">{priceOf(pack)}</span>,
        actions: (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => drawer.open(pack.packKey)}
            aria-label={fill(p.detailsLabel, { pack: pack.label })}
          >
            {p.details}
          </Button>
        ),
      })}
    />
  );

  let planPrice = "";
  if (plan && plan.priceCents > 0)
    planPrice = fill(p.pricePerMonth, {
      price: money(plan.priceCents, plan.currency),
    });
  let planName = p.unknown;
  if (planLoading) planName = p.loading;
  else if (plan) planName = fill(p.name, { plan: plan.label });

  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-2xl font-bold tracking-[-0.02em] text-fg">{p.title}</h1>
        <p className="max-w-2xl text-sm text-fg-secondary">{p.subtitle}</p>
      </header>

      <section aria-labelledby="plan-title" className="flex flex-col gap-2">
        <h2 id="plan-title" className="px-1 text-sm font-semibold text-fg">
          {p.yourPlan}
        </h2>
        <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1 rounded-xl border border-border-default bg-surface-1 px-4 py-3 text-sm text-fg-secondary">
          <strong className="text-base font-semibold text-fg">{planName}</strong>
          {planPrice && <span>{planPrice}</span>}
          {plan && (
            <span>
              {fill(p.creditsMonthly, {
                n: formatCredits(plan.monthlyCreditGrant),
              })}
            </span>
          )}
          <Link href="/usage" className="ml-auto font-medium text-fg-accent underline-offset-4 hover:underline">
            {p.seeUsage}
          </Link>
        </div>
      </section>

      {error && (
        <div className="flex items-center gap-3">
          <p role="alert" className="text-xs text-fg-danger">
            {error}
          </p>
          <Button variant="secondary" size="sm" onClick={reload}>
            {t.packs.retry}
          </Button>
        </div>
      )}
      {holdingsUnavailable && !error && (
        <p role="status" className="max-w-2xl text-[13px] text-fg-muted">
          {t.packs.holdingsUnavailable}
        </p>
      )}

      <section aria-labelledby="mine-title" className="flex flex-col gap-2">
        <h2 id="mine-title" className="px-1 text-sm font-semibold text-fg">
          {p.yourPacks}
        </h2>
        {!isLoading && mine.length === 0 ? (
          <p className="px-1 text-sm text-fg-secondary">{p.noPacks}</p>
        ) : (
          packList(mine, p.yourPacks)
        )}
      </section>

      <section aria-labelledby="more-title" className="flex flex-col gap-2">
        <h2 id="more-title" className="px-1 text-sm font-semibold text-fg">
          {p.morePacks}
        </h2>
        {!isLoading && more.length === 0 ? (
          <p className="px-1 text-sm text-fg-secondary">{p.noMorePacks}</p>
        ) : (
          packList(more, p.morePacks)
        )}
      </section>

      <PackDrawer
        pack={open}
        owned={open ? ownedKeys.has(open.packKey) : false}
        pending={open !== null && pendingKey === open.packKey}
        holdingsUnavailable={holdingsUnavailable}
        studios={studios}
        onAdd={(key) => void subscribe(key)}
        onRemove={(key) => void remove(key)}
        onClose={drawer.close}
      />
    </article>
  );
}
