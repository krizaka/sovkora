import { isComplete, parseInputSchema, toInputs } from "../inputSchema";

/** The real `trade-showcase` schema, copied from what the service returned. */
const TRADE_SHOWCASE = JSON.stringify({
  type: "object",
  required: ["photos", "trade"],
  properties: {
    trade: { type: "string", title: "Votre métier", maxLength: 60 },
    photos: {
      type: "array",
      items: { type: "string" },
      title: "Photos de vos réalisations",
      maxItems: 6,
      minItems: 1,
    },
  },
});

describe("parseInputSchema", () => {
  it("reads the fields a real Studio declares", () => {
    const fields = parseInputSchema(TRADE_SHOWCASE);

    expect(fields.map((field) => field.key).sort()).toEqual(["photos", "trade"]);
    expect(fields.find((field) => field.key === "photos")?.isList).toBe(true);
    expect(fields.find((field) => field.key === "trade")?.isList).toBe(false);
    expect(fields.find((field) => field.key === "trade")?.maxLength).toBe(60);
  });

  it("uses the declared title, falling back to the key", () => {
    const fields = parseInputSchema(
      JSON.stringify({ properties: { a: { type: "string", title: "Le A" }, b: { type: "string" } } }),
    );

    expect(fields.find((field) => field.key === "a")?.title).toBe("Le A");
    expect(fields.find((field) => field.key === "b")?.title).toBe("b");
  });

  it("puts required fields first — on a phone, what is below the fold gets missed", () => {
    const fields = parseInputSchema(
      JSON.stringify({
        required: ["second"],
        properties: { first: { type: "string" }, second: { type: "string" } },
      }),
    );

    expect(fields[0].key).toBe("second");
  });

  it("treats an absent or unparseable schema as no fields rather than throwing", () => {
    expect(parseInputSchema(null)).toEqual([]);
    expect(parseInputSchema("")).toEqual([]);
    expect(parseInputSchema("{ not json")).toEqual([]);
  });
});

describe("toInputs", () => {
  const fields = parseInputSchema(TRADE_SHOWCASE);

  it("splits a list field on newlines", () => {
    const inputs = toInputs(fields, { trade: "plombier", photos: "photo-1\nphoto-2" });

    expect(inputs).toEqual({ trade: "plombier", photos: ["photo-1", "photo-2"] });
  });

  it("drops blank lines and surrounding space, which a phone keyboard adds freely", () => {
    const inputs = toInputs(fields, { trade: " plombier ", photos: "photo-1\n\n  photo-2  \n" });

    expect(inputs).toEqual({ trade: "plombier", photos: ["photo-1", "photo-2"] });
  });

  it("omits an unanswered optional field instead of sending an empty string", () => {
    // An empty string is an answer; the absence of the key is not. Sending "" would make the
    // service validate a value the actor never gave.
    expect(toInputs(fields, { trade: "plombier" })).toEqual({ trade: "plombier" });
  });
});

describe("isComplete", () => {
  const fields = parseInputSchema(TRADE_SHOWCASE);

  it("refuses a run the service would reject with a 400", () => {
    // This is the whole point: the screen used to send {} and the run came back
    // "missing required inputs: [photos, trade]" — a refusal the actor could do nothing about.
    expect(isComplete(fields, {})).toBe(false);
    expect(isComplete(fields, { trade: "plombier" })).toBe(false);
  });

  it("accepts once every required field has an answer", () => {
    expect(isComplete(fields, { trade: "plombier", photos: "photo-1" })).toBe(true);
  });

  it("does not count whitespace as an answer", () => {
    expect(isComplete(fields, { trade: "   ", photos: "photo-1" })).toBe(false);
  });
});

describe("the video Studio's inputs (orazaka-mobile-client#22)", () => {
  const schema = JSON.stringify({
    required: ["prompt"],
    properties: {
      prompt: { type: "string", title: "Prompt" },
      aspect: { type: "string", title: "Format", enum: ["9:16", "16:9", "1:1"], default: "16:9" },
      seed: { type: "integer", title: "Seed" },
    },
  });

  it("reads an enum as options with its default, and an integer as a number field", () => {
    const fields = parseInputSchema(schema);
    expect(fields.find((f) => f.key === "aspect")).toMatchObject({
      options: ["9:16", "16:9", "1:1"],
      defaultValue: "16:9",
    });
    expect(fields.find((f) => f.key === "seed")?.isNumber).toBe(true);
  });

  it("sends the seed as a number and the aspect as chosen", () => {
    const fields = parseInputSchema(schema);
    expect(toInputs(fields, { prompt: "une lampe", aspect: "9:16", seed: "42" })).toEqual({
      prompt: "une lampe",
      aspect: "9:16",
      seed: 42,
    });
  });
});

describe("parseInputSchema — closed sets are choices", () => {
  const schema = JSON.stringify({
    properties: {
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
            "x-meta": { runsOn: "local", available: false, unavailableReason: "FLUX is not running" },
          },
        ],
      },
      seed: { type: "string", "x-control": "seed", "x-advanced": true },
      unloaded: { type: "string", "x-options": { source: "models", category: "video" } },
    },
  });
  const field = (key: string) => parseInputSchema(schema).find((f) => f.key === key)!;

  it("reads the labelled list the server wrote, with what a picker shows", () => {
    expect(field("model").choices).toEqual([
      {
        value: "sd-hosted",
        label: "SD hosted",
        hint: "Hosted.",
        runsOn: "cloud",
        speed: "fast",
        typicalSeconds: 8,
        priceCents: 8,
        available: true,
        unavailableReason: null,
      },
      {
        value: "flux",
        label: "FLUX",
        hint: null,
        runsOn: "local",
        speed: null,
        typicalSeconds: null,
        priceCents: null,
        available: false,
        unavailableReason: "FLUX is not running",
      },
    ]);
    expect(field("model")).toMatchObject({ defaultValue: "sd-hosted", advanced: true, fedByCatalogue: true });
  });

  it("keeps a catalogue-fed field a picker when its list did not load", () => {
    expect(field("unloaded")).toMatchObject({ fedByCatalogue: true });
    expect(field("unloaded").choices).toBeUndefined();
  });

  it("reads the seed control", () => {
    expect(field("seed")).toMatchObject({ control: "seed", advanced: true });
  });
});
