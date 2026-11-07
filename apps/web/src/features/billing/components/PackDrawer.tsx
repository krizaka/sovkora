"use client";

import Link from "next/link";
import { CheckIcon } from "@krizaka/icons";
import { Badge } from "@krizaka/ui/badge";
import { Button } from "@krizaka/ui/button";
import { Dialog } from "@krizaka/ui/dialog";
import { formatCredits, type PackSummary } from "@krizaka/orazaka-shared";
import { CatalogueIcon } from "@/components/icons/CatalogueIcon";
import { useTranslation } from "@/core/context/LocaleContext";
import { fill } from "@/core/context/fill";
import { useMoney } from "@/core/hooks/useMoney";

export interface PackDrawerProps {
  /** The pack to show; null keeps the drawer closed. */
  pack: PackSummary | null;
  owned: boolean;
  pending: boolean;
  /** Billing is not reachable: what you hold is unknown, so adding and removing are off. */
  holdingsUnavailable: boolean;
  /** The catalogue's Studios by key: their names and icons, in the reader's language. */
  studios: ReadonlyMap<string, { label: string; iconKey: string }>;
  onAdd: (packKey: string) => void;
  onRemove: (packKey: string) => void;
  onClose: () => void;
}

/**
 * One pack, in a side drawer: its price, what it adds (the Studios, each a link), and the one thing to do with it.
 * Opened from a Studio row (« Get pack ») or from « Plan & packs », so buying never means leaving the list or
 * guessing what you get. A pack billing could not price offers no button: there is no amount to agree to (ADR-036).
 */
export function PackDrawer({
  pack,
  owned,
  pending,
  holdingsUnavailable,
  studios,
  onAdd,
  onRemove,
  onClose,
}: Readonly<PackDrawerProps>) {
  const { t } = useTranslation();
  const p = t.plan;
  const money = useMoney();

  let price = p.priceUnavailable;
  if (pack?.priceCents === 0) price = p.free;
  else if (pack && pack.priceCents !== null) price = money(pack.priceCents);
  const included = pack?.access === "INCLUDED";
  const buyable = pack?.access === "BUYABLE" && !holdingsUnavailable;

  return (
    <Dialog.Root open={pack !== null} onOpenChange={(next) => !next && !pending && onClose()}>
      <Dialog.Content placement="right" closeLabel={p.close}>
        {pack && (
          <>
            <Dialog.Header>
              <Dialog.Title className="flex items-center gap-2">
                <CatalogueIcon iconKey={pack.iconKey} size={20} className="text-fg-accent" />
                {pack.label}
              </Dialog.Title>
              {pack.tagline && <Dialog.Description>{pack.tagline}</Dialog.Description>}
            </Dialog.Header>
            <Dialog.Body className="flex flex-col gap-5">
              <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1 text-sm text-fg">
                <span className="font-semibold tabular-nums">{included ? p.included : price}</span>
                {pack.includedCredits !== null && pack.includedCredits > 0 && (
                  <span className="text-fg-secondary">
                    {fill(p.credits, {
                      n: formatCredits(pack.includedCredits),
                    })}
                  </span>
                )}
                {owned && !included && (
                  <Badge tone="success">
                    <CheckIcon size={12} />
                    {p.owned}
                  </Badge>
                )}
              </p>
              <section className="flex flex-col gap-2" aria-labelledby="pack-drawer-studios">
                <h3 id="pack-drawer-studios" className="text-sm font-semibold text-fg">
                  {p.drawerStudios}{" "}
                  <span className="font-normal tabular-nums text-fg-muted">{pack.studioKeys.length}</span>
                </h3>
                <ul className="flex flex-col divide-y divide-border-subtle rounded-lg border border-border-default">
                  {pack.studioKeys.map((key) => {
                    const studio = studios.get(key);
                    return (
                      <li key={key}>
                        <Link
                          href={`/studios/${key}`}
                          className="flex items-center gap-2 px-3 py-2 text-sm text-fg hover:bg-surface-2"
                        >
                          <CatalogueIcon iconKey={studio?.iconKey} size={16} className="shrink-0 text-fg-secondary" />
                          {studio?.label ?? key}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              </section>
              {holdingsUnavailable && (
                <p role="status" className="text-xs text-fg-muted">
                  {t.packs.holdingsUnavailable}
                </p>
              )}
            </Dialog.Body>
            {buyable && (
              <Dialog.Footer>
                {owned ? (
                  <Button variant="ghost" size="sm" onClick={() => onRemove(pack.packKey)} loading={pending}>
                    {p.remove}
                  </Button>
                ) : (
                  <Button variant="primary" size="md" onClick={() => onAdd(pack.packKey)} loading={pending}>
                    {pending ? p.adding : p.add}
                  </Button>
                )}
              </Dialog.Footer>
            )}
          </>
        )}
      </Dialog.Content>
    </Dialog.Root>
  );
}
