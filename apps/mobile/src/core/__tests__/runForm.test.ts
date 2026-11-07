import { initialAnswers, isAdvanced, runPercent, splitFields } from "../runForm";
import type { SchemaField } from "../inputSchema";

const field = (key: string, required = false): SchemaField => ({
  key,
  title: key,
  required,
  isList: false,
  isNumber: false,
});

describe("a run form on a phone", () => {
  it("keeps the required prompt and the format in view, tuning behind More options", () => {
    const { basic, advanced } = splitFields([
      field("prompt", true),
      field("size"),
      field("seed"),
      field("model"),
      field("negativePrompt"),
    ]);
    expect(basic.map((f) => f.key)).toEqual(["prompt", "size"]);
    expect(advanced.map((f) => f.key)).toEqual(["seed", "model", "negativePrompt"]);
  });

  it("never hides a required field, whatever its name", () => {
    expect(isAdvanced(field("model", true))).toBe(false);
  });
});

describe("run progress", () => {
  it("is unknown before the steps are", () => {
    expect(runPercent([])).toBeNull();
  });

  it("counts the steps that are over, skipped ones included", () => {
    expect(
      runPercent([{ status: "SUCCEEDED" }, { status: "SKIPPED" }, { status: "RUNNING" }, { status: "PENDING" }]),
    ).toBe(50);
  });
});

describe("what a form sends before the person touches it", () => {
  it("is the declared default, else the first option of a choice — the one shown selected", () => {
    const fields: SchemaField[] = [
      { ...field("aspect"), options: ["9:16", "16:9"], defaultValue: "16:9" },
      { ...field("model"), options: ["wan2-2-t2v-a14b", "wan2.1-t2v-1.3b"] },
      field("prompt", true),
    ];
    expect(initialAnswers(fields)).toEqual({ aspect: "16:9", model: "wan2-2-t2v-a14b" });
  });
});

describe("what the schema says is rare is behind More options", () => {
  it("honours the blueprint's own x-advanced, beyond the well-known names", () => {
    const { basic, advanced } = splitFields([field("prompt", true), { ...field("quality"), advanced: true }]);
    expect(basic.map((f) => f.key)).toEqual(["prompt"]);
    expect(advanced.map((f) => f.key)).toEqual(["quality"]);
  });
});

describe("a catalogue-fed choice starts on automatic", () => {
  it("does not pre-select a model: the platform keeps choosing the best available one when the run starts", () => {
    const fields: SchemaField[] = [
      {
        ...field("model"),
        defaultValue: "sd-hosted",
        fedByCatalogue: true,
        choices: [
          {
            value: "sd-hosted",
            label: "SD",
            hint: null,
            runsOn: "cloud",
            speed: null,
            typicalSeconds: null,
            priceCents: null,
            available: true,
            unavailableReason: null,
          },
        ],
      },
      { ...field("aspect"), options: ["9:16", "16:9"], defaultValue: "16:9" },
    ];
    expect(initialAnswers(fields)).toEqual({ aspect: "16:9" });
  });
});
