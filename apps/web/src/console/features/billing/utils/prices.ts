import { addMinutes } from "date-fns";
import type { PriceEntryView, PriceFigures, PriceHistoryEntry, PricesView } from "@krizaka/ui/price-catalogue-editor";

/**
 * The price catalogue as krizaka-billing serves it (`/api/v1/billing/prices/admin`, krizaka/orazaka#417): the service
 * is the only holder of the amounts (ADR-009); this file only reshapes them for the editor. Amounts are integer cents,
 * `null` is « on quote » — never a float, never a zero standing for a quote.
 */
export interface AdminPrice {
  versionId: string;
  /** `product.plan` */
  key: string;
  unit: string;
  amountCents: number | null;
  currency: string;
  taxes: string;
  availability: string;
  limits: Record<string, number>;
  /** ISO instant the version starts. */
  effectiveFrom: string;
}

/** The wire values of the service's `PriceUnit` / `PriceAvailability`, in the order the fields offer them. */
export const PRICE_UNITS = ["month", "year", "once", "document", "order", "pack", "hour"];
export const PRICE_AVAILABILITIES = ["public", "internal", "founder-limited", "preparing"];

const figures = (p: AdminPrice): PriceFigures => ({
  amountCents: p.amountCents,
  unit: p.unit,
  availability: p.availability,
  limits: p.limits,
});

/**
 * The editor's view: each entry in force with the versions still waiting for their date.
 *
 * @param current - every entry in force (the admin read)
 * @param histories - every version of a key, by key (the history read); a missing key has none scheduled
 * @param now - the clock, injected
 */
export function toPricesView(current: AdminPrice[], histories: Record<string, AdminPrice[]>, now: Date): PricesView {
  const entries: PriceEntryView[] = current.map((p) => {
    const [product = "", plan = ""] = p.key.split(".");
    const upcoming = (histories[p.key] ?? [])
      .filter((h) => new Date(h.effectiveFrom).getTime() > now.getTime())
      .sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom))
      .map((h) => ({ ...figures(h), versionId: h.versionId, effectiveFrom: h.effectiveFrom }));
    return {
      ...figures(p),
      key: p.key,
      product,
      plan,
      currency: p.currency,
      versionId: p.versionId,
      effectiveFrom: p.effectiveFrom,
      upcoming,
    };
  });
  return { units: PRICE_UNITS, availabilities: PRICE_AVAILABILITIES, entries };
}

/**
 * An entry's history, oldest first, each version against the one before it. The service keeps the versions, not who
 * made them: `at` is the date the version starts and the actor is not known here.
 */
export function toHistory(versions: AdminPrice[]): PriceHistoryEntry[] {
  const ordered = [...versions].sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom));
  return ordered.map((version, i) => ({
    versionId: version.versionId,
    at: version.effectiveFrom,
    actorLabel: "",
    effectiveFrom: version.effectiveFrom,
    before: i === 0 ? null : figures(ordered[i - 1]!),
    after: figures(version),
  }));
}

/**
 * The instant a change starts, from the day the admin picked (`YYYY-MM-DD`). Today means two minutes from now (the clock may be a minute old) — the
 * service refuses a start in the past — and a day before today is refused here with the same reason.
 */
export function effectiveInstant(day: string, today: string, now: Date): string {
  if (day < today) {
    throw new Error("A price change cannot start in the past.");
  }
  if (day === today) {
    return addMinutes(now, 2).toISOString();
  }
  return new Date(`${day}T00:00:00Z`).toISOString();
}
