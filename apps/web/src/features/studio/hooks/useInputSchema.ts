"use client";

import { useMemo } from "react";
import type { Locale } from "@/core/context/LocaleContext";

/** One field the run form renders, derived from the blueprint's input schema. */
export interface InputField {
  key: string;
  title: string;
  type: "string" | "array" | "enum";
  options: string[] | null;
  required: boolean;
  maxItems: number | null;
  /** The schema's `description`: a line of help under the field. */
  description: string | null;
  /** A long text (`maxLength` over 500): a text area rather than a line. */
  multiline: boolean;
  /** What file the field takes, when it takes an uploaded one (a picker, never a typed asset id). */
  assetKind: AssetKind | null;
  /** A labelled list the server wrote in (`oneOf` of `const`): the catalogue's models, with the facts a picker shows. */
  choices: Choice[] | null;
  /** The value the form starts on, when the schema (or the catalogue's resolved default) states one. */
  defaultValue: string | null;
  /** An `integer` or `number` field: a number input with the schema's bounds, never a free text line. */
  numeric: { min: number | null; max: number | null } | null;
  /** `seed`: a number with a "random" toggle rather than a text line. */
  control: "seed" | null;
  /** Rarely needed: folded under "More options". */
  advanced: boolean;
  /** The values come from the model catalogue (`x-options`): a picker even when the list could not be loaded. */
  fedByCatalogue: boolean;
  /** The pack wrote this field's title and helper in the page's language (`x-i18n`): the client's own words must not replace them. */
  wordedByPack: boolean;
  /** An enum's choices in the page's language (`x-i18n`), by value; null when the pack gave none. */
  optionLabels: Record<string, string> | null;
  /**
   * On a catalogue-fed model field: the aspects each model can make (`x-options.aspects`) and the field they govern
   * (`x-options.aspectField`): the form offers only the selected model's, never the blueprint's whole list.
   */
  aspectsByModel: { field: string; aspects: Record<string, string[]> } | null;
  /** An example of what to type, in the page's language (`x-i18n.<lang>.placeholder`); null when the pack gave none. */
  placeholder: string | null;
}

/** The longest helper line a field shows (the pack's data check holds its selects to the same). */
// @fixed: a helper is one line of the form (≤ 140 characters, the same limit as orazaka-packs/tests/closed-sets)
export const HELPER_MAX = 140;

/** One language's words for a field, as the pack writes them (`x-i18n.<lang>`). */
interface PackWords {
  title?: string;
  description?: string;
  placeholder?: string;
  options?: Record<string, string | { label?: string; hint?: string }>;
}

/** The pack's words for a locale: that language, else English, else nothing (the blueprint's own text stays). */
function wordsFor(i18n: unknown, locale: Locale): PackWords | null {
  if (!i18n || typeof i18n !== "object") return null;
  const byLang = i18n as Record<string, PackWords | undefined>;
  return byLang[locale] ?? byLang.en ?? null;
}

/**
 * A helper kept to one line: the first sentence when it fits, else the text cut at a word with an ellipsis. A pack's
 * selects are held to the limit by its own data check; this protects a form whose pack predates it.
 */
export function shortHelper(text: string | null): string | null {
  if (!text) return null;
  const clean = text.trim().replace(/\s+/g, " ");
  if (clean.length <= HELPER_MAX) return clean;
  const sentence = /^.{1,138}?[.!?](?=\s|$)/.exec(clean);
  if (sentence) return sentence[0];
  const cut = clean.slice(0, HELPER_MAX - 1);
  return `${cut.slice(0, Math.max(cut.lastIndexOf(" "), 40)).replace(/[\s,;:·(]+$/, "")}…`;
}

/** What the catalogue says about one model: shown beside its name so a person can choose, not guess. */
export interface ChoiceMeta {
  runsOn: "local" | "cloud" | null;
  speed: "fast" | "balanced" | "slow" | null;
  typicalSeconds: number | null;
  priceCents: number | null;
  available: boolean;
  unavailableReason: string | null;
  /** Why a model cannot run, as a code the client words in the page's language (`gpu_offline`, `worker_offline`, `provider_down`). */
  unavailableCode: string | null;
}

/** One entry of a closed list. */
export interface Choice {
  value: string;
  label: string;
  hint: string | null;
  meta: ChoiceMeta | null;
  /** Language-neutral facts the pack states for a comparison (a resolution, a duration): « 1216×704 », « 5 s ». */
  facts: string[];
}

interface RawChoice {
  const?: unknown;
  title?: string;
  description?: string;
  "x-meta"?: Partial<Record<keyof ChoiceMeta, unknown>>;
}

/** The `oneOf` of `const` entries the server writes for a catalogue-fed field; null for anything else. */
function choicesOf(
  oneOf: unknown,
  words: PackWords | null,
  facts: Record<string, unknown> | undefined,
): Choice[] | null {
  if (
    !Array.isArray(oneOf) ||
    oneOf.length === 0 ||
    !oneOf.every((entry) => entry && typeof entry === "object" && "const" in entry)
  ) {
    return null;
  }
  return (oneOf as RawChoice[]).map((entry) => {
    const meta = entry["x-meta"];
    const text = (value: unknown) => (typeof value === "string" ? value : null);
    const num = (value: unknown) => (typeof value === "number" ? value : null);
    const value = String(entry.const);
    const packed = words?.options?.[value];
    const own = packed && typeof packed === "object" ? packed : null;
    const known = facts?.[value];
    return {
      value,
      label: own?.label ?? entry.title ?? value,
      // The catalogue's own sentence is a paragraph; a pack that wrote the hint wins, else it is cut to a line.
      hint: own?.hint ?? shortHelper(entry.description ?? null),
      facts: Array.isArray(known) ? known.filter((fact): fact is string => typeof fact === "string") : [],
      meta: meta
        ? {
            runsOn: meta.runsOn === "local" || meta.runsOn === "cloud" ? meta.runsOn : null,
            speed: meta.speed === "fast" || meta.speed === "balanced" || meta.speed === "slow" ? meta.speed : null,
            typicalSeconds: num(meta.typicalSeconds),
            priceCents: num(meta.priceCents),
            available: meta.available !== false,
            unavailableReason: text(meta.unavailableReason),
            unavailableCode: text(meta.unavailableCode),
          }
        : null,
    };
  });
}

/** An enum's value → label map, as the pack wrote it for the language (a model's `{ label, hint }` is read by its choices). */
function enumLabels(words: PackWords | null): Record<string, string> | null {
  const entries = Object.entries(words?.options ?? {}).filter(
    (entry): entry is [string, string] => typeof entry[1] === "string",
  );
  return entries.length > 0 ? Object.fromEntries(entries) : null;
}

/** The per-model aspects a model field declares, or null. */
function aspectsOf(
  options: { aspectField?: string; aspects?: Record<string, unknown> } | undefined,
): InputField["aspectsByModel"] {
  if (!options?.aspectField || !options.aspects || typeof options.aspects !== "object") {
    return null;
  }
  const aspects: Record<string, string[]> = {};
  for (const [model, list] of Object.entries(options.aspects)) {
    if (Array.isArray(list)) {
      aspects[model] = list.filter((aspect): aspect is string => typeof aspect === "string");
    }
  }
  return { field: options.aspectField, aspects };
}

/** A pack's helper is already one line; a list field's older one is cut to a line; a text field's guidance stays whole. */
function shortHelperOf(
  text: string | null,
  writtenByPack: boolean,
  spec: { enum?: unknown; oneOf?: unknown; "x-options"?: unknown },
): string | null {
  const isList = Array.isArray(spec.enum) || Boolean(spec["x-options"]) || Array.isArray(spec.oneOf);
  return writtenByPack || !isList ? text : shortHelper(text);
}

export type AssetKind = "image" | "video" | "audio";

/**
 * The kind of file an input takes: `format: "asset:image|video|audio"` (orazaka-packs#30), or the older
 * `format: "asset-id"` read with its `contentMediaType` (`image/*`). A blueprint published before the pack
 * declared anything still has `image` as its starting image: that key alone is read as an image.
 */
export function assetKindOf(
  key: string,
  spec: { format?: string; contentMediaType?: string; type?: string },
): AssetKind | null {
  if (spec.type && spec.type !== "string") {
    return null;
  }
  const declared = /^asset:(image|video|audio)$/.exec(spec.format ?? "");
  if (declared) {
    return declared[1] as AssetKind;
  }
  const media = /^(image|video|audio)\//.exec(spec.contentMediaType ?? "");
  if (spec.format === "asset-id" && media) {
    return media[1] as AssetKind;
  }
  return !spec.format && key === "image" ? "image" : null;
}

/**
 * Turns a blueprint's raw JSON Schema into the fields the run form renders.
 *
 * Generated, never hand-written per Studio: a bespoke form per profession would put
 * every new Studio behind a frontend release — the deploy-per-Studio failure ADR-034
 * exists to remove, reappearing on this side of the wire.
 *
 * A malformed schema yields no fields rather than throwing. The server validates
 * authoritatively anyway, so a broken schema should degrade the form, not the page.
 *
 * @param inputSchema - the raw JSON Schema string, or null when unpublished
 */
export function useInputSchema(inputSchema: string | null, locale: Locale = "en"): InputField[] {
  return useMemo(() => {
    if (!inputSchema) {
      return [];
    }
    try {
      const parsed = JSON.parse(inputSchema) as {
        required?: string[];
        properties?: Record<string, Record<string, unknown>>;
      };
      const required = new Set(parsed.required ?? []);
      return Object.entries(parsed.properties ?? {}).map(([key, raw]) => {
        const spec = raw as {
          title?: string;
          type?: string;
          enum?: string[];
          maxItems?: number;
          maxLength?: number;
          minimum?: number;
          maximum?: number;
          description?: string;
          format?: string;
          contentMediaType?: string;
          oneOf?: unknown;
          default?: unknown;
          "x-control"?: string;
          "x-advanced"?: boolean;
          "x-options"?: {
            source?: string;
            facts?: Record<string, unknown>;
            aspectField?: string;
            aspects?: Record<string, unknown>;
          };
          "x-i18n"?: unknown;
        };
        const words = wordsFor(spec["x-i18n"], locale);
        const choices = choicesOf(spec.oneOf, words, spec["x-options"]?.facts);
        let type: InputField["type"] = "string";
        if (Array.isArray(spec.enum)) {
          type = "enum";
        } else if (spec.type === "array") {
          type = "array";
        }
        const assetKind = type === "string" ? assetKindOf(key, spec) : null;
        return {
          key,
          title: words?.title ?? spec.title ?? key,
          type,
          options: Array.isArray(spec.enum) ? spec.enum : null,
          required: required.has(key),
          maxItems: typeof spec.maxItems === "number" ? spec.maxItems : null,
          description: shortHelperOf(words?.description ?? spec.description ?? null, Boolean(words?.description), spec),
          assetKind,
          multiline: type === "string" && !assetKind && typeof spec.maxLength === "number" && spec.maxLength > 500,
          choices,
          defaultValue: spec.default === undefined || spec.default === null ? null : String(spec.default),
          numeric:
            spec.type === "integer" || spec.type === "number"
              ? {
                  min: typeof spec.minimum === "number" ? spec.minimum : null,
                  max: typeof spec.maximum === "number" ? spec.maximum : null,
                }
              : null,
          control: spec["x-control"] === "seed" ? "seed" : null,
          advanced: spec["x-advanced"] === true,
          fedByCatalogue: spec["x-options"]?.source === "models",
          wordedByPack: Boolean(words?.title),
          optionLabels: enumLabels(words),
          aspectsByModel: aspectsOf(spec["x-options"]),
          placeholder: words?.placeholder?.trim() || null,
        } satisfies InputField;
      });
    } catch {
      return [];
    }
  }, [inputSchema, locale]);
}
