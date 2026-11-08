import { NativeModules, Platform } from "react-native";

/**
 * The device's preferred languages, most preferred first.
 *
 * iOS keeps them in `SettingsManager`; Android has one `localeIdentifier`. `Intl` is the last resort (it reflects the
 * first of them on Hermes). No extra native module: a language never needs a new build.
 */
export function deviceLanguageTags(): string[] {
  const tags: string[] = [];
  try {
    if (Platform.OS === "ios") {
      const settings = (NativeModules.SettingsManager as { settings?: Record<string, unknown> } | undefined)?.settings;
      const languages = settings?.AppleLanguages;
      if (Array.isArray(languages)) tags.push(...languages.filter((l): l is string => typeof l === "string"));
      if (typeof settings?.AppleLocale === "string") tags.push(settings.AppleLocale);
    } else {
      const id = (NativeModules.I18nManager as { localeIdentifier?: string } | undefined)?.localeIdentifier;
      if (id) tags.push(id);
    }
    tags.push(new Intl.DateTimeFormat().resolvedOptions().locale);
  } catch {
    // An unreadable device setting means English, which `deviceLocale` already does for an empty list.
  }
  return tags;
}
