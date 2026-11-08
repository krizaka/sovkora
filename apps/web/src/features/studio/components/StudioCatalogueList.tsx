"use client";

import { Badge } from "@krizaka/ui/badge";
import { List } from "@krizaka/ui/list";
import { CatalogueIcon } from "@/components/icons/CatalogueIcon";
import { useTranslation } from "@/core/context/LocaleContext";
import { fill } from "@/core/context/fill";
import { useMoney } from "@/core/hooks/useMoney";
import { StudioState, type CatalogueGroup, type CatalogueRow } from "@/features/studio/catalogue/catalogue.types";
import { StudioRowAction } from "@/features/studio/components/StudioRowAction";

interface StudioCatalogueListProps {
  groups: readonly CatalogueGroup[];
  onGetPack: (packKey: string) => void;
  plansHref: string;
}

const TONE = {
  [StudioState.Ready]: "success",
  [StudioState.NeedsPack]: "accent",
  [StudioState.NeedsPlan]: "warning",
  [StudioState.Unknown]: "neutral",
} as const;

/**
 * The Studios, one line each under their trade's heading: the name, where it comes from or what would open it, its
 * state, and one action. Dense on purpose (a phone shows a dozen rows, a desktop a whole trade), so finding a Studio
 * is reading a list, not scrolling past posters.
 */
export function StudioCatalogueList({ groups, onGetPack, plansHref }: Readonly<StudioCatalogueListProps>) {
  const { t } = useTranslation();
  const c = t.catalogue;
  const money = useMoney();

  const line = (row: CatalogueRow): string => {
    const parts: string[] = [];
    if (row.state === StudioState.Ready && row.source)
      parts.push(fill(c.sources[row.source], { pack: row.pack?.label ?? "" }));
    if (row.state === StudioState.NeedsPack) {
      const pack = row.pack?.label ?? row.studio.packKey ?? "";
      const price = row.pack?.priceCents;
      parts.push(
        price === null || price === undefined
          ? fill(c.needsPackNoPrice, { pack })
          : fill(c.needsPack, { pack, price: money(price) }),
      );
    }
    if (row.state === StudioState.NeedsPlan) parts.push(c.needsPlan);
    if (row.state === StudioState.Unknown) parts.push(c.unknown);
    if (row.hasUpdate) parts.push(c.update);
    if (row.studio.tagline) parts.push(row.studio.tagline);
    return parts.join(" · ");
  };

  return (
    <div className="flex flex-col gap-5">
      {groups.map((group) => (
        <section key={group.trade} aria-labelledby={`trade-${group.trade}`} className="flex flex-col gap-2">
          <h2 id={`trade-${group.trade}`} className="flex items-baseline gap-2 px-1 text-sm font-semibold text-fg">
            {group.label}
            <span className="text-xs font-normal tabular-nums text-fg-muted">{group.rows.length}</span>
          </h2>
          <List
            aria-label={fill(c.listLabel, { trade: group.label })}
            density="compact"
            items={group.rows}
            getId={(row) => row.studio.studioKey}
            row={(row) => ({
              title: row.studio.label,
              description: line(row),
              leading: <CatalogueIcon iconKey={row.studio.iconKey} size={18} />,
              href: `/studios/${row.studio.studioKey}`,
              meta: (
                <Badge
                  tone={TONE[row.state]}
                  className="hidden normal-case tracking-normal sm:inline-flex"
                  data-state={row.state}
                >
                  {c.states[row.state]}
                </Badge>
              ),
              actions: <StudioRowAction row={row} onGetPack={onGetPack} plansHref={plansHref} />,
            })}
          />
        </section>
      ))}
    </div>
  );
}
