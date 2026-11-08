/** One field of a Studio's input schema, reduced to what a form needs. */
export interface SchemaField {
  readonly key: string;
  readonly title: string;
  readonly required: boolean;
  /** A `string[]` field is entered as one value per line. */
  readonly isList: boolean;
  readonly maxLength?: number;
  /** An `enum` field (the video Studio's `aspect`) is chosen, not typed. */
  readonly options?: readonly string[];
  /** An `integer`/`number` field (`seed`) is sent as a number. */
  readonly isNumber: boolean;
  readonly defaultValue?: string;
  /** The field takes an uploaded file (`format: asset:image`): a picker, never a typed asset id. Only photos are picked on the phone. */
  readonly assetKind?: "image" | "video" | "audio";
  /**
   * The field takes a document, sent as its own base64 text (`contentMediaType: application/pdf`, or the document-validation
   * pack's `documentBase64` while it declares nothing more): a file picker and a pre-flight, never a thousand-line text box.
   */
  readonly documentKind?: "pdf";
  /** A labelled list the server wrote in (`oneOf` of `const`): the catalogue's models, with what a picker shows. */
  readonly choices?: readonly Choice[];
  /** The values come from the model catalogue (`x-options`): a picker even when the list could not be loaded. */
  readonly fedByCatalogue?: boolean;
  /** `seed`: a number with a "random" toggle, never a text line. */
  readonly control?: "seed";
  /** The blueprint says this is rarely needed: it goes behind "More options". */
  readonly advanced?: boolean;
}

/** One entry of a closed list, with the catalogue's facts (every one optional: what is unknown is left out). */
export interface Choice {
  readonly value: string;
  readonly label: string;
  readonly hint: string | null;
  readonly runsOn: "local" | "cloud" | null;
  readonly speed: "fast" | "balanced" | "slow" | null;
  readonly typicalSeconds: number | null;
  readonly priceCents: number | null;
  readonly available: boolean;
  readonly unavailableReason: string | null;
}

interface RawChoice {
  readonly const?: unknown;
  readonly title?: string;
  readonly description?: string;
  readonly "x-meta"?: Record<string, unknown>;
}

/** The `oneOf` of `const` entries the server writes for a catalogue-fed field; undefined for anything else. */
function choicesOf(oneOf: unknown): Choice[] | undefined {
  if (
    !Array.isArray(oneOf) ||
    oneOf.length === 0 ||
    !oneOf.every((entry) => entry !== null && typeof entry === "object" && "const" in entry)
  ) {
    return undefined;
  }
  return (oneOf as RawChoice[]).map((entry) => {
    const meta = entry["x-meta"] ?? {};
    const text = (value: unknown): string | null => (typeof value === "string" ? value : null);
    const num = (value: unknown): number | null => (typeof value === "number" ? value : null);
    return {
      value: String(entry.const),
      label: entry.title ?? String(entry.const),
      hint: entry.description ?? null,
      runsOn: meta.runsOn === "local" || meta.runsOn === "cloud" ? meta.runsOn : null,
      speed: meta.speed === "fast" || meta.speed === "balanced" || meta.speed === "slow" ? meta.speed : null,
      typicalSeconds: num(meta.typicalSeconds),
      priceCents: num(meta.priceCents),
      available: meta.available !== false,
      unavailableReason: text(meta.unavailableReason),
    };
  });
}

interface RawProperty {
  readonly type?: string;
  readonly enum?: readonly (string | number)[];
  readonly default?: string | number;
  readonly title?: string;
  readonly maxLength?: number;
  readonly items?: { readonly type?: string };
  readonly format?: string;
  readonly contentMediaType?: string;
  readonly oneOf?: unknown;
  readonly "x-control"?: string;
  readonly "x-advanced"?: boolean;
  readonly "x-options"?: { readonly source?: string };
}

/**
 * The kind of file a field takes: `format: asset:image|video|audio` (orazaka-packs#30), or `asset-id` read with its
 * `contentMediaType`. A blueprint published before the pack declared anything still has `image` as its starting
 * image: that key alone is read as an image.
 */
export function assetKindOf(key: string, property: RawProperty): SchemaField["assetKind"] {
  if (property.type !== undefined && property.type !== "string") return undefined;
  const declared = /^asset:(image|video|audio)$/.exec(property.format ?? "");
  if (declared) return declared[1] as SchemaField["assetKind"];
  const media = /^(image|video|audio)\//.exec(property.contentMediaType ?? "");
  if (property.format === "asset-id" && media) return media[1] as SchemaField["assetKind"];
  return property.format === undefined && key === "image" ? "image" : undefined;
}

/** A string that carries a PDF as base64: by its standard JSON-Schema words, else by the one key the pack has used so far. */
export function documentKindOf(key: string, property: RawProperty): SchemaField["documentKind"] {
  if (property.type !== undefined && property.type !== "string") return undefined;
  if (property.contentMediaType === "application/pdf") return "pdf";
  return property.format === undefined && key === "documentBase64" ? "pdf" : undefined;
}

interface RawSchema {
  readonly required?: readonly string[];
  readonly properties?: Record<string, RawProperty>;
}

/**
 * Reduces a Studio's JSON-Schema to a flat field list.
 *
 * Only the subset the seeded blueprints actually use — strings and string arrays — because a
 * general JSON-Schema form renderer is a project, and a partial one that silently drops a field
 * type would produce runs missing an input the actor was never asked for. An unknown type is
 * therefore treated as a string rather than skipped: the actor can still answer it, and the
 * service validates the result either way.
 *
 * @param raw the `inputSchema` string from the Studio detail, possibly null
 * @returns the fields to render, required ones first
 */
export function parseInputSchema(raw: string | null | undefined): SchemaField[] {
  if (raw === null || raw === undefined || raw === "") {
    return [];
  }
  let schema: RawSchema;
  try {
    schema = JSON.parse(raw) as RawSchema;
  } catch {
    return [];
  }

  const required = new Set(schema.required ?? []);
  const fields = Object.entries(schema.properties ?? {}).map(([key, property]) => ({
    key,
    title: property.title ?? key,
    required: required.has(key),
    isList: property.type === "array",
    maxLength: property.maxLength,
    ...(assetKindOf(key, property) ? { assetKind: assetKindOf(key, property) } : {}),
    ...(documentKindOf(key, property) ? { documentKind: documentKindOf(key, property) } : {}),
    ...(property.enum ? { options: property.enum.map(String) } : {}),
    ...(choicesOf(property.oneOf) ? { choices: choicesOf(property.oneOf) } : {}),
    ...(property["x-options"]?.source === "models" ? { fedByCatalogue: true } : {}),
    ...(property["x-control"] === "seed" ? { control: "seed" as const } : {}),
    ...(property["x-advanced"] === true ? { advanced: true } : {}),
    isNumber: property.type === "integer" || property.type === "number",
    ...(property.default === undefined ? {} : { defaultValue: String(property.default) }),
  }));

  // Required first: on a phone the fields below the fold are the ones that get missed.
  return fields.sort((left, right) => Number(right.required) - Number(left.required));
}

type AnswerValue = string | number | string[];

/** One typed answer as the payload wants it: a list split on lines, a number as a number, else the text. */
function answerValue(field: SchemaField, answer: string): AnswerValue {
  if (field.isList) {
    return answer
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line !== "");
  }
  return field.isNumber && Number.isFinite(Number(answer)) ? Number(answer) : answer;
}

/**
 * Turns the typed answers into the `inputs` map the run endpoint expects.
 *
 * @param fields the schema fields
 * @param answers what the actor typed, keyed by field
 * @returns the inputs payload, list fields split on newlines with blank lines dropped
 */
export function toInputs(
  fields: readonly SchemaField[],
  answers: Readonly<Record<string, string>>,
): Record<string, AnswerValue> {
  const inputs: Record<string, AnswerValue> = {};
  for (const field of fields) {
    const answer = (answers[field.key] ?? "").trim();
    if (answer === "") {
      continue;
    }
    inputs[field.key] = answerValue(field, answer);
  }
  return inputs;
}

/**
 * Whether every required field has an answer.
 *
 * Checked before submitting so the actor is told what is missing by the form, not by a 400 that
 * arrives after the run has been refused.
 *
 * @param fields the schema fields
 * @param answers what the actor typed
 * @returns true when the run can be submitted
 */
export function isComplete(fields: readonly SchemaField[], answers: Readonly<Record<string, string>>): boolean {
  return fields.filter((field) => field.required).every((field) => (answers[field.key] ?? "").trim() !== "");
}
