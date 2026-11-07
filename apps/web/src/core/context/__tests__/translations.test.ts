/**
 * @file translations.test.ts
 * @description Tests for the i18n translation dictionary completeness and consistency.
 */

import { translations, type Locale } from "@/core/context/translations";

const LOCALES: Locale[] = ["en", "fr"];

/** Recursively collects all leaf keys from a nested object as dot-paths (array items by index). */
function collectKeys(obj: Record<string, unknown>, prefix = ""): string[] {
  const keys: string[] = [];
  for (const [key, value] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === "object" && value !== null) {
      keys.push(...collectKeys(value as Record<string, unknown>, path));
    } else {
      keys.push(path);
    }
  }
  return keys;
}

describe("translations", () => {
  test("both locales exist", () => {
    expect(translations.en).toBeDefined();
    expect(translations.fr).toBeDefined();
  });

  test("en and fr have the same key structure", () => {
    const enKeys = collectKeys(translations.en as unknown as Record<string, unknown>).sort((a, b) =>
      a.localeCompare(b),
    );
    const frKeys = collectKeys(translations.fr as unknown as Record<string, unknown>).sort((a, b) =>
      a.localeCompare(b),
    );
    expect(enKeys).toEqual(frKeys);
  });

  test.each(LOCALES)("all values in '%s' are non-empty strings or functions", (locale) => {
    const dict = translations[locale] as unknown as Record<string, unknown>;
    const keys = collectKeys(dict);
    for (const key of keys) {
      const value = key.split(".").reduce<unknown>((obj, k) => {
        return (obj as Record<string, unknown>)?.[k];
      }, dict);
      const valueType = typeof value;
      expect(valueType === "string" || valueType === "function").toBe(true);
      if (valueType === "string") {
        expect((value as string).trim().length).toBeGreaterThan(0);
      }
    }
  });

  test("sidebar section has all required keys", () => {
    const required = ["dashboard", "chatSessions", "logout", "memoryBlocks", "playground"];
    for (const key of required) {
      expect(translations.en.sidebar).toHaveProperty(key);
      expect(translations.fr.sidebar).toHaveProperty(key);
    }
  });

  test("chat section has connection error key", () => {
    expect(translations.en.chat.connectionError).toBe("Connection lost.");
    expect(translations.fr.chat.connectionError).toBe("Connexion interrompue.");
  });
});
