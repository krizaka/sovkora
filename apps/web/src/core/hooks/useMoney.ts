"use client";

import { useCallback } from "react";
import { formatMoney } from "@krizaka/intl/money";
import { BILLING_CURRENCY, MONEY_LOCALES } from "@/core/constants/money.constants";
import { useTranslation } from "@/core/context/LocaleContext";

/**
 * Formats an amount of money in the reader's language: `money(2900)` → "CA$29.00" in English, "29,00 $CA" in French.
 *
 * @returns a formatter from integer cents (the ledger's money, never a float) and an optional ISO 4217 code — the
 *   plan's own when it has one, the billing currency otherwise
 */
export function useMoney(): (cents: number, currency?: string | null) => string {
  const { locale } = useTranslation();
  return useCallback(
    (cents: number, currency?: string | null) =>
      formatMoney(cents, { currency: currency || BILLING_CURRENCY, locale: MONEY_LOCALES[locale] }),
    [locale],
  );
}
