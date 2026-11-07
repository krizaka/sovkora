"use client";

/**
 * @file LocaleContext.tsx
 * @description Provides internationalization context, switching controls, and translations.
 * Supports English ('en') and French ('fr').
 *
 * Client Directive: "use client"
 * State & Syncing: Syncs language code preferences to local browser storage and settings database service.
 */

import * as React from "react";
import { usePathname } from "next/navigation";
import { FALLBACK_LOCALE, negotiateLocale, splitLocalePrefix } from "@/core/i18n/locale-negotiation";
import { useSettings } from "@/core/hooks/useSettings";
import { type Locale, type TranslationDictionary, translationsFor } from "./translations";
import { useBrand } from "./BrandContext";
export type { Locale, TranslationDictionary };

/**
 * Shape of the language translation context state.
 */
interface LocaleContextType {
  /** The current active locale. */
  locale: Locale;
  /** Callback to change active locale. */
  setLocale: (locale: Locale) => void;
  /** Translation dictionary matching the active locale. */
  t: TranslationDictionary;
}

/**
 * React Context storing current UI language preferences.
 */
const LocaleContext = React.createContext<LocaleContextType | undefined>(undefined);

/**
 * LocaleProvider component handling internationalization context wrapper.
 * Syncs locale settings between client memory, browser local storage, and gateway services.
 *
 * @param props - Component React properties.
 * @param props.children - Child nodes to render inside language context.
 * @returns The context Provider element.
 */
export function LocaleProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const { settings, updateSettings } = useSettings();
  const brand = useBrand();
  const [locale, setLocaleState] = React.useState<Locale>(FALLBACK_LOCALE);
  const prefix = splitLocalePrefix(usePathname() ?? "/").locale;

  // The address names the language when it can (`/fr/login`); else the choice stored in this browser; else the browser's
  // languages: English only when it asks for English and never for French, French otherwise (Loi 96).
  React.useEffect(() => {
    const stored = globalThis.window === undefined ? null : localStorage.getItem("orazaka_language");
    const languages = typeof navigator === "undefined" ? [] : navigator.languages;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLocaleState(prefix ?? negotiateLocale(stored, languages));
  }, [prefix]);

  // The page says its language to assistive technology and search engines.
  React.useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  // Sync with settings backend configuration if it updates
  React.useEffect(() => {
    if (settings?.language) {
      const settingsLang = settings.language as Locale;
      if (settingsLang === "en" || settingsLang === "fr") {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setLocaleState(settingsLang);
        localStorage.setItem("orazaka_language", settingsLang);
      }
    }
  }, [settings]);

  const setLocale = React.useCallback(
    (newLocale: Locale) => {
      setLocaleState(newLocale);
      if (globalThis.window !== undefined) {
        localStorage.setItem("orazaka_language", newLocale);
      }

      // If settings are available and backend language differs, update it
      if (settings && settings.language !== newLocale) {
        updateSettings({ language: newLocale });
      }
    },
    [settings, updateSettings],
  );

  const dictionaries = React.useMemo(() => translationsFor(brand), [brand]);

  const contextValue = React.useMemo(
    () => ({
      locale,
      setLocale,
      t: dictionaries[locale],
    }),
    [locale, setLocale, dictionaries],
  );

  return <LocaleContext.Provider value={contextValue}>{children}</LocaleContext.Provider>;
}

/**
 * Custom React Hook to consume translation dictionary and locale switching functionality.
 *
 * @throws {Error} If called outside of a wrapping {@link LocaleProvider}.
 * @returns The active {@link LocaleContextType} interface.
 */
export function useTranslation() {
  const context = React.useContext(LocaleContext);
  if (context === undefined) {
    throw new Error("useTranslation must be used within a LocaleProvider");
  }
  return context;
}
