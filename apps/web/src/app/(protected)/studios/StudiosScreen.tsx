"use client";

import { useMemo } from "react";
import { Button } from "@krizaka/ui/button";
import { EmptyState } from "@krizaka/ui/empty-state";
import { List } from "@krizaka/ui/list";
import { useTranslation } from "@/core/context/LocaleContext";
import { useDrawerParam } from "@/core/hooks/useDrawerParam";
import { PackDrawer } from "@/features/billing/components/PackDrawer";
import { PlanSummary } from "@/features/billing/components/PlanSummary";
import { usePacks } from "@/features/billing/hooks/usePacks";
import { usePlan } from "@/features/billing/hooks/usePlan";
import { buildRows, countViews, filterRows, groupByTrade, tradesOf } from "@/features/studio/catalogue/catalogue";
import { CatalogueView, type CataloguePack } from "@/features/studio/catalogue/catalogue.types";
import { RunsInProgress } from "@/features/studio/components/RunsInProgress";
import { StudioLoadError } from "@/features/studio/components/StudioAsyncState";
import { StudioCatalogueList } from "@/features/studio/components/StudioCatalogueList";
import { StudioCatalogueToolbar } from "@/features/studio/components/StudioCatalogueToolbar";
import { useCatalogueQuery } from "@/features/studio/hooks/useCatalogueQuery";
import { useInstallations } from "@/features/studio/hooks/useInstallations";
import { useStudios } from "@/features/studio/hooks/useStudios";

const PLANS_HREF = "/packs";

/**
 * /studios, the single home of the Studios: what the plan gives (one line), then every Studio in one list you can
 * search, narrow to « Ready » or « Get more », and read by trade, each with its state and one action. A pack opens in
 * a drawer in place. Composed here, at the app layer, because the list (studio) and the plan and packs (billing) are
 * two features that never import each other (dependency-cruiser).
 */
export function StudiosScreen() {
  const { t, locale } = useTranslation();
  const c = t.catalogue;
  const { studios, isLoading, hasError, reload } = useStudios(null);
  const { installations } = useInstallations();
  const { packs, ownedKeys, holdingsUnavailable, pendingKey, subscribe, remove } = usePacks(locale, t.packs.loadError);
  const { plan, isLoading: planLoading } = usePlan();
  const { query, setQuery } = useCatalogueQuery();
  const drawer = useDrawerParam("pack");

  const tradeLabel = useMemo(() => (trade: string) => t.studio.professions[trade] ?? trade, [t]);
  const cataloguePacks = useMemo<CataloguePack[]>(
    () =>
      packs.map((p) => ({
        packKey: p.packKey,
        label: p.label,
        access: p.access,
        owned: ownedKeys.has(p.packKey),
        priceCents: p.priceCents,
        studioKeys: p.studioKeys,
      })),
    [packs, ownedKeys],
  );
  const rows = useMemo(
    () => buildRows(studios, installations, cataloguePacks, tradeLabel),
    [studios, installations, cataloguePacks, tradeLabel],
  );
  const all = useMemo(() => countViews(rows, { query: "", trade: null }), [rows]);
  const counts = useMemo(() => countViews(rows, query), [rows, query]);
  const groups = useMemo(() => groupByTrade(filterRows(rows, query), tradeLabel), [rows, query, tradeLabel]);
  const studioNames = useMemo(
    () => new Map(studios.map((s) => [s.studioKey, { label: s.label, iconKey: s.iconKey }])),
    [studios],
  );
  const openPack = packs.find((p) => p.packKey === drawer.value) ?? null;

  const filtered = query.query !== "" || query.trade !== null || query.view !== CatalogueView.All;

  return (
    <div className="flex flex-col gap-4">
      <header className="flex flex-col gap-1">
        <h1 className="font-display text-2xl font-bold tracking-[-0.02em] text-fg">{c.title}</h1>
        <p className="text-sm text-fg-secondary">{c.subtitle}</p>
      </header>

      <PlanSummary plan={plan} loading={planLoading} ready={all.ready} more={all.more} href={PLANS_HREF} />
      <RunsInProgress />
      <StudioCatalogueToolbar
        query={query}
        counts={counts}
        total={all.all}
        trades={tradesOf(rows, tradeLabel)}
        onChange={setQuery}
      />

      {isLoading && (
        <List aria-label={c.title} loading items={[]} getId={() => ""} row={() => ({ title: "" })} density="compact" />
      )}
      {!isLoading && hasError && <StudioLoadError onRetry={reload} />}
      {!isLoading && !hasError && groups.length > 0 && (
        <StudioCatalogueList groups={groups} onGetPack={drawer.open} plansHref={PLANS_HREF} />
      )}
      {!isLoading && !hasError && groups.length === 0 && (
        <EmptyState
          role="status"
          title={query.view === CatalogueView.More && !query.query && !query.trade ? c.emptyMore : c.emptyTitle}
          description={filtered ? c.emptyBody : undefined}
          action={
            filtered ? (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setQuery({ query: "", trade: null, view: CatalogueView.All })}
              >
                {c.emptyReset}
              </Button>
            ) : undefined
          }
        />
      )}

      <PackDrawer
        pack={openPack}
        owned={openPack ? ownedKeys.has(openPack.packKey) : false}
        pending={openPack !== null && pendingKey === openPack.packKey}
        holdingsUnavailable={holdingsUnavailable}
        studios={studioNames}
        onAdd={(key) => void subscribe(key).then(reload)}
        onRemove={(key) => void remove(key).then(reload)}
        onClose={drawer.close}
      />
    </div>
  );
}
