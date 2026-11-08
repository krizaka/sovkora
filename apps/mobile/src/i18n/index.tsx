/**
 * @file i18n/index.tsx
 * @description The app's words: one catalogue per language (`en.json` is the reference, `fr.json` has the same keys),
 * the engine is `@krizaka/i18n` like every Krizaka app. The language is the device's unless the person pinned one.
 *
 * A screen never writes a word: it reads `t("profile.signOut")`. `en.json`'s shape types the keys, so a missing French
 * key fails type-checking (`fr` is declared as `typeof en`).
 */
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import * as SecureStore from "expo-secure-store";
import { createI18n, type MessageKey, type Translate } from "@krizaka/i18n";
import en from "./en.json";
import fr from "./fr.json";
import { setRequestLanguage } from "../config/api";
import { deviceLanguageTags } from "./deviceTags";
import { asLanguagePreference, resolveLocale, type LanguagePreference, type Locale } from "./locale";

export type Messages = typeof en;
/** Every key of the catalogue — for the ones built from data (`studios.run.${status}`). */
export type Key = MessageKey<Messages>;

export const i18n = createI18n<Messages, Locale>({ en, fr }, { defaultLocale: "en" });

const PREFERENCE_KEY = "orazaka.language";

interface LocaleState {
  readonly locale: Locale;
  readonly preference: LanguagePreference;
  readonly setPreference: (preference: LanguagePreference) => void;
  readonly t: Translate<Messages>;
}

const LocaleContext = createContext<LocaleState | null>(null);

/** Speaks the device's language, or the one pinned in Profile; the pinned one is remembered on the device. */
export function LocaleProvider({ children }: Readonly<{ children: React.ReactNode }>): React.JSX.Element {
  const [preference, setPreference] = useState<LanguagePreference>("auto");

  useEffect(() => {
    void SecureStore.getItemAsync(PREFERENCE_KEY)
      .then((stored) => setPreference(asLanguagePreference(stored)))
      .catch(() => undefined);
  }, []);

  const changePreference = useCallback((next: LanguagePreference) => {
    setPreference(next);
    void SecureStore.setItemAsync(PREFERENCE_KEY, next).catch(() => undefined);
  }, []);

  const value = useMemo<LocaleState>(() => {
    const locale = resolveLocale(preference, deviceLanguageTags());
    // Before the children fetch: the platform answers in the language the app speaks.
    setRequestLanguage(locale);
    const translate = i18n.translator(locale);
    return { locale, preference, setPreference: changePreference, t: translate };
  }, [preference, changePreference]);

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

/** What a component reads outside a provider (a unit test): English, one stable translator. */
const ENGLISH: LocaleState = {
  locale: "en",
  preference: "auto",
  setPreference: () => undefined,
  t: i18n.translator("en"),
};

/** The translator and the active language. Outside a provider (a unit test) it speaks English. */
export function useI18n(): LocaleState {
  return useContext(LocaleContext) ?? ENGLISH;
}

/** `const t = useT()` — the common case. */
export function useT(): LocaleState["t"] {
  return useI18n().t;
}

export { format } from "@krizaka/i18n";
