"use client";

import type { ReactNode } from "react";
import { ChartsProvider } from "@krizaka/charts/provider";
import { useTranslation } from "@/core/context/LocaleContext";
import { INTL_LOCALES } from "@/core/constants/locale.constants";

/**
 * The settings of every chart of the app (@krizaka/charts), mounted once in `Providers`: the reader's locale (figures
 * and dates written the same on the server and the client), the Sovkora palette (orange first) and the charts' words.
 */
export function AppCharts({ children }: Readonly<{ children: ReactNode }>) {
  const { locale, t } = useTranslation();
  return (
    <ChartsProvider locale={INTL_LOCALES[locale]} brand="orazaka" labels={t.insights.charts}>
      {children}
    </ChartsProvider>
  );
}
