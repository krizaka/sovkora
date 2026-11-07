"use client";

import Link from "next/link";
import { Button } from "@krizaka/ui/button";
import { useTranslation } from "@/core/context/LocaleContext";
import { fill } from "@/core/context/fill";
import { StudioState, type CatalogueRow } from "@/features/studio/catalogue/catalogue.types";

interface StudioRowActionProps {
  row: CatalogueRow;
  /** Opens the pack in place (the drawer), so getting it never leaves the list. */
  onGetPack: (packKey: string) => void;
  /** Where the plans are read and compared. */
  plansHref: string;
}

/**
 * The one action of a row, chosen by its state: Run a ready Studio, Get the pack that opens it, or See the plans that
 * include it. A Studio billing could not check offers nothing, never a purchase (ADR-034 §8.3).
 */
export function StudioRowAction({ row, onGetPack, plansHref }: Readonly<StudioRowActionProps>) {
  const { t } = useTranslation();
  const c = t.catalogue;
  const studio = row.studio.label;

  if (row.state === StudioState.Ready) {
    return (
      <Button asChild variant="primary" size="sm">
        <Link href={`/studios/${row.studio.studioKey}`} aria-label={fill(c.actionLabels.run, { studio })}>
          {c.actions.run}
        </Link>
      </Button>
    );
  }
  if (row.state === StudioState.NeedsPack && row.pack) {
    const packKey = row.pack.packKey;
    return (
      <Button
        variant="secondary"
        size="sm"
        onClick={() => onGetPack(packKey)}
        aria-label={fill(c.actionLabels.getPack, { studio })}
      >
        {c.actions.getPack}
      </Button>
    );
  }
  if (row.state === StudioState.NeedsPlan) {
    return (
      <Button asChild variant="ghost" size="sm">
        <Link href={plansHref} aria-label={fill(c.actionLabels.seePlans, { studio })}>
          {c.actions.seePlans}
        </Link>
      </Button>
    );
  }
  return null;
}
