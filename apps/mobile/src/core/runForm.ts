import type { SchemaField } from "./inputSchema";

/** Fields a phone form keeps behind "More options": optional tuning that a person rarely needs and a raw model id. */
const ADVANCED = new Set(["seed", "model", "image", "negativePrompt"]);

/** True for an optional tuning field (seed, model, starting image, things to avoid). */
export function isAdvanced(field: SchemaField): boolean {
  return !field.required && (field.advanced === true || ADVANCED.has(field.key));
}

/** Splits a Studio's fields into the ones always shown and the ones behind "More options". */
export function splitFields(fields: readonly SchemaField[]): {
  readonly basic: SchemaField[];
  readonly advanced: SchemaField[];
} {
  return { basic: fields.filter((f) => !isAdvanced(f)), advanced: fields.filter(isAdvanced) };
}

/** How far a run is, 0–100, from its steps — null while nothing is known (the progress bar then just moves). */
export function runPercent(steps: readonly { readonly status: string }[]): number | null {
  if (steps.length === 0) return null;
  const done = steps.filter((s) => s.status === "SUCCEEDED" || s.status === "SKIPPED").length;
  return Math.round((done / steps.length) * 100);
}

/**
 * What each field holds before the person touches it: its declared default, else — for a choice — the first option,
 * which is the one the chips show as selected. What is shown selected is what is sent, never a silent platform default.
 */
export function initialAnswers(fields: readonly SchemaField[]): Record<string, string> {
  return Object.fromEntries(
    fields.flatMap((f) => {
      // A catalogue-fed choice starts on "Automatic" (nothing chosen): the platform keeps picking the best available
      // model when the run starts, and says so if its first choice is off. Its default is shown, not sent.
      if (f.fedByCatalogue === true || f.choices !== undefined) return [];
      const value = f.defaultValue ?? f.options?.[0];
      return value === undefined ? [] : [[f.key, value]];
    }),
  );
}
