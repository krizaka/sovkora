"use client";

import { usePathname } from "next/navigation";
import * as React from "react";
import { type Locale, type TranslationDictionary, translationsFor } from "@/core/context/translations";
import { useBrand } from "@/core/context/BrandContext";
import { BUILD_LOCALE } from "@public-build/build-locale";

export type { Locale, TranslationDictionary };

interface LocaleContextType {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: TranslationDictionary;
}

const LocaleContext = React.createContext<LocaleContextType | undefined>(undefined);

/**
 * `LocaleContext` of the public export (an alias of `next.config.public.mjs`): the build is written in one language, so the static HTML of a page
 * is already in it — no stored choice, no settings call, no effect after the first paint. Changing language is a link to the other
 * build (`/en/…`), never a state.
 */
export function LocaleProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const brand = useBrand();
  const path = usePathname() ?? "/";
  const dictionaries = React.useMemo(() => translationsFor(brand), [brand]);
  const value = React.useMemo(
    () => ({
      locale: BUILD_LOCALE,
      setLocale: (next: Locale) => globalThis.location.assign(`/${next}${path}`),
      t: dictionaries[BUILD_LOCALE],
    }),
    [path, dictionaries],
  );
  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function useTranslation() {
  const context = React.useContext(LocaleContext);
  if (context === undefined) {
    throw new Error("useTranslation must be used within a LocaleProvider");
  }
  return context;
}
