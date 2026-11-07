"use client";

import * as React from "react";
import { negotiateLocale } from "@/core/i18n/locale-negotiation";

/** The languages the console's translated screens speak (Law → Proposals first; the others follow, consoleCopy). */
export const CONSOLE_LOCALES = ["en", "fr"] as const;
export type ConsoleLocale = (typeof CONSOLE_LOCALES)[number];

/** The BCP 47 locale each language writes its dates and figures in. */
export const CONSOLE_INTL: Record<ConsoleLocale, string> = { en: "en-CA", fr: "fr-CA" };

/** French is the console's language by default (Loi 96). */
export const DEFAULT_CONSOLE_LOCALE: ConsoleLocale = "fr";

/** The console's language from the browser's, the site's rule: English only when the browser asks for English and never French, else French. */
export function localeFromLanguages(languages: readonly string[]): ConsoleLocale {
  return negotiateLocale(null, languages);
}

const LocaleContext = React.createContext<ConsoleLocale | null>(null);

const subscribe = (onChange: () => void) => {
  globalThis.addEventListener?.("languagechange", onChange);
  return () => globalThis.removeEventListener?.("languagechange", onChange);
};
const browserLocale = () =>
  localeFromLanguages(globalThis.navigator?.languages ?? [globalThis.navigator?.language ?? "en"]);
const serverLocale = (): ConsoleLocale => DEFAULT_CONSOLE_LOCALE;

/**
 * The console's language. Without `locale`, it follows the browser (`navigator.languages`, read without a hydration
 * mismatch: French on the server, the browser's after); tests and a future switch pass it.
 */
export function ConsoleLocaleProvider({
  locale,
  children,
}: Readonly<{ locale?: ConsoleLocale; children: React.ReactNode }>) {
  const fromBrowser = React.useSyncExternalStore(subscribe, browserLocale, serverLocale);
  return <LocaleContext.Provider value={locale ?? fromBrowser}>{children}</LocaleContext.Provider>;
}

/** The language of the screen; French outside a provider. */
export function useConsoleLocale(): ConsoleLocale {
  return React.useContext(LocaleContext) ?? DEFAULT_CONSOLE_LOCALE;
}
