/**
 * @file public-prices.ts
 * @description The public price catalogue (krizaka-billing `GET /api/v1/billing/prices`, krizaka/orazaka#417), read on
 * the server for `/pricing`. Prices are data (ADR-009): no figure is typed on this site, and when the catalogue cannot
 * be read — no credential, billing down, a wrong shape — the page shows no figure instead of a stale or invented one.
 */
import { z } from "zod";
import { formatMoney } from "@krizaka/intl/money";

const PublicPriceSchema = z.object({
  key: z.string(),
  unit: z.string(),
  amountCents: z.number().int().nonnegative().nullable(),
  availability: z.string(),
  limits: z.record(z.string(), z.number().int()),
});

export type PublicPrice = z.infer<typeof PublicPriceSchema>;

/** The billing currency (`NEXT_PUBLIC_BILLING_CURRENCY`, read at build), Canadian dollars by default. */
const CURRENCY = (process.env.NEXT_PUBLIC_BILLING_CURRENCY || "CAD").trim().toUpperCase();

/** Reads the public catalogue through the edge with the catalogue's service credential; `[]` when it cannot be read. */
export async function loadPublicPrices(): Promise<PublicPrice[]> {
  const credential = process.env.PAYMENTS_CATALOGUE_TOKEN;
  if (!credential) return [];
  const edge = process.env.ROUTER_URL || process.env.GATEWAY_URL || "http://localhost:8088";
  try {
    const res = await fetch(`${edge}/api/v1/billing/prices`, {
      headers: { Authorization: `Bearer ${credential}` },
      next: { revalidate: 300 },
    });
    if (!res.ok) return [];
    return z.array(PublicPriceSchema).parse(await res.json());
  } catch {
    return [];
  }
}

export interface ShownPrice {
  /** The amount in the visitor's language, the currency named. */
  text: string;
  /** The catalogue's unit id (`year`, `month`, `once`…): the page words it. */
  unit: string;
}

/**
 * The price to show for an edition (`orazaka.<edition>`), or `null` when the page keeps its own words: not in the
 * catalogue, preparing, internal, on quote, or free (the edition's words say what free means).
 */
export function priceFor(prices: readonly PublicPrice[], edition: string, locale: string): ShownPrice | null {
  const entry = prices.find((p) => p.key === `orazaka.${edition}`);
  if (!entry || entry.amountCents === null || entry.amountCents === 0) return null;
  if (entry.availability !== "public" && entry.availability !== "founder-limited") return null;
  return { text: formatMoney(entry.amountCents, { currency: CURRENCY, locale }), unit: entry.unit };
}
