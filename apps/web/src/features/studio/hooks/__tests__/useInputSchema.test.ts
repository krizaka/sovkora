import { renderHook } from "@testing-library/react";
import { assetKindOf, HELPER_MAX, shortHelper, useInputSchema } from "@/features/studio/hooks/useInputSchema";

describe("assetKindOf — which inputs take an uploaded file", () => {
  it.each([
    ["image", { type: "string", format: "asset:image" }, "image"],
    ["clip", { type: "string", format: "asset:video" }, "video"],
    ["voice", { type: "string", format: "asset:audio" }, "audio"],
    ["assetId", { type: "string", format: "asset-id", contentMediaType: "audio/*" }, "audio"],
    ["image", { type: "string" }, "image"], // a blueprint published before the pack declared anything
    ["prompt", { type: "string", format: "prose" }, null],
    ["image", { type: "string", format: "prose" }, null],
    ["subject", { type: "string" }, null],
    ["assetId", { type: "string", format: "asset-id" }, null],
    ["image", { type: "array", format: "asset:image" }, null],
  ])("%s %j is %s", (key, spec, kind) => {
    expect(assetKindOf(key, spec)).toBe(kind);
  });
});

describe("useInputSchema", () => {
  it("marks the image input as an image picker, not a text line", () => {
    const schema = JSON.stringify({
      type: "object",
      properties: {
        image: { type: "string", format: "asset:image", maxLength: 120, title: "x" },
        prompt: { type: "string" },
      },
    });
    const { result } = renderHook(() => useInputSchema(schema));
    expect(result.current.map((f) => [f.key, f.assetKind])).toEqual([
      ["image", "image"],
      ["prompt", null],
    ]);
  });
});

describe("useInputSchema — closed sets are choices, not text", () => {
  const schema = JSON.stringify({
    type: "object",
    required: ["prompt"],
    properties: {
      prompt: { type: "string" },
      model: {
        type: "string",
        title: "Model",
        "x-advanced": true,
        "x-options": { source: "models", category: "image" },
        default: "sd-hosted",
        oneOf: [
          {
            const: "sd-hosted",
            title: "SD hosted",
            description: "Hosted.",
            "x-meta": { runsOn: "cloud", speed: "fast", typicalSeconds: 8, priceCents: 8, available: true },
          },
          {
            const: "flux",
            title: "FLUX",
            "x-meta": {
              runsOn: "local",
              available: false,
              unavailableReason: "FLUX is not running",
              unavailableCode: null,
            },
          },
        ],
      },
      seed: { type: "string", "x-control": "seed", "x-advanced": true },
      voiceless: { type: "string", "x-options": { source: "models", category: "speech" } },
      size: { type: "string", enum: ["1024x1024", "512x512"], default: "1024x1024" },
    },
  });
  const field = (key: string) => renderHook(() => useInputSchema(schema)).result.current.find((f) => f.key === key)!;

  it("reads a labelled oneOf as choices with the catalogue facts a picker shows", () => {
    const model = field("model");
    expect(model.choices).toEqual([
      {
        value: "sd-hosted",
        label: "SD hosted",
        hint: "Hosted.",
        facts: [],
        meta: {
          runsOn: "cloud",
          speed: "fast",
          typicalSeconds: 8,
          priceCents: 8,
          available: true,
          unavailableReason: null,
          unavailableCode: null,
        },
      },
      {
        value: "flux",
        label: "FLUX",
        hint: null,
        facts: [],
        meta: {
          runsOn: "local",
          speed: null,
          typicalSeconds: null,
          priceCents: null,
          available: false,
          unavailableReason: "FLUX is not running",
          unavailableCode: null,
        },
      },
    ]);
    expect(model.defaultValue).toBe("sd-hosted");
    expect(model.fedByCatalogue).toBe(true);
    expect(model.advanced).toBe(true);
  });

  it("a catalogue-fed field whose list did not load is still a picker, with no choices", () => {
    const f = field("voiceless");
    expect(f.fedByCatalogue).toBe(true);
    expect(f.choices).toBeNull();
  });

  it("reads the seed control and the static enum", () => {
    expect(field("seed")).toMatchObject({ control: "seed", advanced: true, choices: null });
    expect(field("size")).toMatchObject({
      type: "enum",
      options: ["1024x1024", "512x512"],
      defaultValue: "1024x1024",
      advanced: false,
    });
    expect(field("prompt")).toMatchObject({ control: null, advanced: false, fedByCatalogue: false });
  });
});

describe("useInputSchema — the words come from the pack, in the page's language (x-i18n)", () => {
  const schema = JSON.stringify({
    type: "object",
    properties: {
      aspect: {
        type: "string",
        enum: ["9:16", "16:9"],
        title: "Format",
        description: "9:16 : la vidéo verticale.",
        "x-i18n": {
          en: {
            title: "Frame",
            description: "9:16 is vertical video.",
            options: { "9:16": "Vertical 9:16", "16:9": "Wide 16:9" },
          },
          fr: {
            title: "Cadre",
            description: "9:16 : la vidéo verticale.",
            options: { "9:16": "Vertical 9:16", "16:9": "Large 16:9" },
          },
        },
      },
      model: {
        type: "string",
        title: "Modèle",
        description: "x".repeat(900),
        "x-options": { source: "models", category: "video", facts: { ltx: ["1216×704", "5 s"] } },
        "x-i18n": {
          en: {
            title: "Model",
            description: "Auto picks the best one.",
            options: { ltx: { label: "LTX-Video · fast", hint: "Image or text to video." } },
          },
          fr: {
            title: "Modèle",
            description: "Auto choisit le meilleur.",
            options: { ltx: { label: "LTX-Video · rapide", hint: "Image ou texte vers vidéo." } },
          },
        },
        oneOf: [
          {
            const: "ltx",
            title: "LTX-Video 0.9.8 13B distilled (720p, GPU)",
            description: "A very long catalogue sentence. ".repeat(10),
            "x-meta": { available: true },
          },
        ],
      },
      legacy: {
        type: "string",
        enum: ["a", "b"],
        title: "Ancien",
        description: "Une phrase courte. " + "Puis un long paragraphe qui n'en finit plus. ".repeat(8),
      },
    },
  });
  const read = (locale: "en" | "fr") => renderHook(() => useInputSchema(schema, locale)).result.current;

  it("titles, helpers and option labels follow the language", () => {
    const en = read("en")[0];
    const fr = read("fr")[0];
    expect([en.title, en.description, en.optionLabels, en.wordedByPack]).toEqual([
      "Frame",
      "9:16 is vertical video.",
      { "9:16": "Vertical 9:16", "16:9": "Wide 16:9" },
      true,
    ]);
    expect([fr.title, fr.optionLabels?.["16:9"]]).toEqual(["Cadre", "Large 16:9"]);
  });

  it("a model takes the pack's short label and hint (not the catalogue's paragraph) and its neutral facts", () => {
    const model = read("en")[1];
    expect(model.title).toBe("Model");
    expect(model.choices).toMatchObject([
      { value: "ltx", label: "LTX-Video · fast", hint: "Image or text to video.", facts: ["1216×704", "5 s"] },
    ]);
    expect(read("fr")[1].choices?.[0]).toMatchObject({ label: "LTX-Video · rapide" });
  });

  it("without the pack's words, a list's helper is cut to a line and a catalogue sentence is cut too", () => {
    const legacy = read("en")[2];
    expect(legacy.wordedByPack).toBe(false);
    expect(legacy.description).toBe("Une phrase courte.");
    expect(shortHelper("y".repeat(300))!.length).toBeLessThanOrEqual(HELPER_MAX);
    expect(shortHelper("a ".repeat(200))!.endsWith("…")).toBe(true);
    expect(shortHelper(null)).toBeNull();
  });
});
