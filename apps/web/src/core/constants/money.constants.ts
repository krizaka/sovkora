/**
 * Money on screen — one currency, one way to write it.
 *
 * The currency is the billing service's (`billing.currency`, krizaka-billing): Canadian dollars by default, Krizaka
 * being a Montréal company. A plan carries its own ISO 4217 code and wins; a pack price carries none and is in this
 * one. `NEXT_PUBLIC_BILLING_CURRENCY` (read at build time) follows a deployment that bills in another currency.
 *
 * The locale is the **language**, not a region: `en` → "CA$29.00", `fr` → "29,00 $CA". A regional locale (`en-CA`,
 * `fr-CA`) would print a bare "$29.00" / "29,00 $" — clear in Canada, ambiguous for the international public the
 * product speaks to, so the currency is always named.
 */
export const BILLING_CURRENCY = (process.env.NEXT_PUBLIC_BILLING_CURRENCY || "CAD").trim().toUpperCase();

/** The locale each language writes money in (see above: the language, so the currency is always named). */
export const MONEY_LOCALES = { en: "en", fr: "fr" } as const;
