import { formatMoney } from "@krizaka/intl/money";

/**
 * The billing service's currency (`billing.currency`, krizaka-billing): Canadian dollars by default — Krizaka is a
 * Montréal company. A plan carries its own ISO 4217 code and wins; a pack price carries none and is in this one.
 * `NEXT_PUBLIC_BILLING_CURRENCY` (read at build time) follows a deployment that bills in another currency.
 */
export const BILLING_CURRENCY = (process.env.NEXT_PUBLIC_BILLING_CURRENCY || "CAD").trim().toUpperCase();

/**
 * An amount in the console's language (French): `money(2900)` → "29,00 $CA". The language locale `fr`, not `fr-CA`,
 * so the currency is always named ("29,00 $" alone would be ambiguous for a reader outside Canada).
 *
 * @param cents - integer cents (the ledger's money, never a float)
 * @param currency - the plan's own ISO 4217 code, the billing currency when absent
 */
export function money(cents: number, currency?: string | null): string {
  return formatMoney(cents, { currency: currency || BILLING_CURRENCY, locale: "fr" });
}
