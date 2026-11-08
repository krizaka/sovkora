/**
 * @file locale.ts
 * @description Which language the app speaks: the device's, unless the person chose one in Profile.
 *
 * Pure (no React, no native module) so the rule is unit-tested: "auto" follows the device, anything the app has no
 * catalogue for falls back to English, and nothing is ever half one language, half the other.
 */

/** The languages the app has a catalogue for. */
export const LOCALES = ["en", "fr"] as const;
export type Locale = (typeof LOCALES)[number];

/** What a person can pick: follow the device, or pin a language. */
export type LanguagePreference = "auto" | Locale;

export const DEFAULT_LOCALE: Locale = "en";

/** `fr-CA`, `fr_FR`, `FR` → `fr`; a tag the app has no catalogue for → `null`. */
export function localeOfTag(tag: string | null | undefined): Locale | null {
  const primary = (tag ?? "").toLowerCase().split(/[-_]/)[0] ?? "";
  return (LOCALES as readonly string[]).includes(primary) ? (primary as Locale) : null;
}

/** The device's language: the first of its tags the app speaks, else English. */
export function deviceLocale(tags: readonly string[]): Locale {
  for (const tag of tags) {
    const locale = localeOfTag(tag);
    if (locale !== null) return locale;
  }
  return DEFAULT_LOCALE;
}

/** The language to speak now. */
export function resolveLocale(preference: LanguagePreference, tags: readonly string[]): Locale {
  return preference === "auto" ? deviceLocale(tags) : preference;
}

/** Reads a stored preference, tolerating anything unexpected (a corrupt entry means "follow the device"). */
export function asLanguagePreference(value: string | null | undefined): LanguagePreference {
  return value === "en" || value === "fr" ? value : "auto";
}
