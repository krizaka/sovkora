import React from "react";
import { ChartsProvider } from "@krizaka/charts/native";
import { useTheme } from "@krizaka/ui/native";
import { useI18n } from "../i18n";

/**
 * The settings of every native chart (@krizaka/charts/native): the Sovkora palette (orange first) and the app's own
 * colour roles and scheme, read from the @krizaka/ui/native theme, and the app's language — the charts follow the app, not the device.
 */
export function AppCharts({ children }: Readonly<{ children: React.ReactNode }>): React.JSX.Element {
  const { scheme, theme } = useTheme();
  const { locale } = useI18n();
  return (
    <ChartsProvider brand="orazaka" locale={locale === "fr" ? "fr-CA" : "en-CA"} scheme={scheme} theme={theme}>
      {children}
    </ChartsProvider>
  );
}
