import type { Key } from "../i18n";
import type { SchemaField } from "./inputSchema";

type Translate = (key: Key) => string;

/** The words of a Studio's run form: the app's own for what it knows, else the platform's. */
export function fieldWords(t: Translate, studioKey: string) {
  return {
    /** By Studio, then by key; the platform's title when the app has no words for the field. */
    titleOf: (field: SchemaField): string => {
      for (const key of [`studios.fields.${studioKey}.${field.key}`, `studios.fields.${field.key}`] as Key[]) {
        const text = t(key);
        if (text !== key) return text;
      }
      return field.title;
    },
    /** A readable name for a value the blueprint lists itself (« Square · 1024×1024 »), else the value. */
    choiceLabel: (fieldKey: string, value: string): string => {
      const key = `studios.choices.${fieldKey}.${value}` as Key;
      const text = t(key);
      return text === key ? value : text;
    },
  };
}
