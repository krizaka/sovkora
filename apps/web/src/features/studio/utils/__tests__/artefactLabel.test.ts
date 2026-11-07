import { artefactLabel, attachArtefactLabels } from "@/features/studio/utils/artefactLabel";

type Artefact = Parameters<typeof artefactLabel>[0];

const artefact = {
  key: "image",
  label: "Votre image",
  type: "IMAGE",
  value: "x",
  labels: { en: "Your image", fr: "Votre image" },
} as Artefact;

describe("artefactLabel", () => {
  it("shows the label in the page's language when the pack wrote it", () => {
    expect(artefactLabel(artefact, "en")).toBe("Your image");
    expect(artefactLabel(artefact, "fr")).toBe("Votre image");
  });

  it("falls back to English, then to the base label", () => {
    expect(artefactLabel({ ...artefact, labels: { en: "Your image" } } as Artefact, "fr")).toBe("Your image");
    expect(artefactLabel({ ...artefact, labels: undefined } as Artefact, "en")).toBe("Votre image");
    expect(artefactLabel({ ...artefact, labels: {} } as Artefact, "en")).toBe("Votre image");
  });
});

describe("attachArtefactLabels", () => {
  it("puts back the per-language labels the shared schema strips when it parses a run", () => {
    const parsed = {
      id: "r",
      outputs: [
        { key: "image", label: "Votre image", type: "IMAGE", value: "x" },
        { key: "seed", label: "Graine", type: "TEXT", value: "1" },
      ],
    };
    const raw = {
      id: "r",
      outputs: [{ key: "image", labels: { en: "Your image", fr: "Votre image" } }, { key: "seed" }],
    };
    const merged = attachArtefactLabels(parsed, raw);
    expect(artefactLabel(merged.outputs[0] as Artefact, "en")).toBe("Your image");
    expect(artefactLabel(merged.outputs[1] as Artefact, "en")).toBe("Graine");
  });

  it("leaves a run alone when the payload has no outputs or is not an object", () => {
    const parsed = { id: "r", outputs: [{ key: "a", label: "A", type: "TEXT", value: "" }] };
    expect(attachArtefactLabels(parsed, null)).toBe(parsed);
    expect(attachArtefactLabels(parsed, { outputs: "nope" })).toBe(parsed);
  });
});
