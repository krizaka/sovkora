"use client";

import { Chip } from "@krizaka/ui/chip";
import { Select } from "@krizaka/ui/field";
import { SearchField } from "@krizaka/ui/search-field";
import { useTranslation } from "@/core/context/LocaleContext";
import { fill } from "@/core/context/fill";
import { CatalogueView, type CatalogueQuery } from "@/features/studio/catalogue/catalogue.types";

interface StudioCatalogueToolbarProps {
  query: CatalogueQuery;
  counts: Record<CatalogueView, number>;
  total: number;
  trades: readonly { key: string; label: string }[];
  onChange: (next: Partial<CatalogueQuery>) => void;
}

const VIEWS = [CatalogueView.All, CatalogueView.Ready, CatalogueView.More] as const;
const ALL_TRADES = "";

/**
 * Search, state and trade: the three ways to narrow the list, each kept in the address by the screen (a filtered list
 * is a link). The search answers as you type (⌘K stays the app's command palette).
 */
export function StudioCatalogueToolbar({
  query,
  counts,
  total,
  trades,
  onChange,
}: Readonly<StudioCatalogueToolbarProps>) {
  const { t } = useTranslation();
  const c = t.catalogue;

  return (
    <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
      <div className="min-w-0 lg:flex-1">
        <SearchField
          size="sm"
          label={c.searchLabel}
          placeholder={fill(c.searchPlaceholder, { n: total })}
          clearLabel={c.clearSearch}
          value={query.query}
          onValueChange={(value) => onChange({ query: value })}
        />
      </div>
      <div className="flex flex-wrap items-center gap-2 sm:flex-nowrap">
        {/* @ephemeral: already in the URL through useCatalogueQuery (replaceState); to move to routeKey with its siblings */}
        <Chip.Group
          type="single"
          size="sm"
          required
          label={c.viewLabel}
          value={query.view}
          onValueChange={(value) => onChange({ view: value as CatalogueView })}
          className="flex-nowrap gap-1.5"
        >
          {VIEWS.map((view) => (
            <Chip key={view} value={view}>
              {c.views[view]} <span className="tabular-nums text-fg-muted">{counts[view]}</span>
            </Chip>
          ))}
        </Chip.Group>
        <span className="block w-full min-w-0 sm:w-48 sm:flex-none">
          <Select
            aria-label={c.tradeLabel}
            className="h-8 w-full text-xs"
            value={query.trade ?? ALL_TRADES}
            onChange={(event) => onChange({ trade: event.target.value || null })}
          >
            <option value={ALL_TRADES}>{c.allTrades}</option>
            {trades.map((trade) => (
              <option key={trade.key} value={trade.key}>
                {trade.label}
              </option>
            ))}
          </Select>
        </span>
      </div>
    </div>
  );
}
