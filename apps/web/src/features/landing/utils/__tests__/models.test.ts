import {
  capabilitiesOf,
  featured,
  filterModels,
  filtersHref,
  MODELS,
  providerCount,
  readFilters,
  type ModelEntry,
} from "@/features/landing/utils/models";

const CAPS = [
  "chat",
  "reasoning",
  "vision",
  "code",
  "embeddings",
  "image",
  "video",
  "speech-to-text",
  "text-to-speech",
  "music",
  "video-to-audio",
];

describe("the registry the site shows (models.generated.json)", () => {
  it("is not empty and every model carries what the page prints", () => {
    expect(MODELS.length).toBeGreaterThan(0);
    for (const m of MODELS) {
      expect(
        [m.id, m.name, m.vendor, m.family, m.runtime, m.licence.name].every(
          (v) => typeof v === "string" && v.trim().length > 0,
        ),
      ).toBe(true);
      expect(m.capabilities.length).toBeGreaterThan(0);
      for (const c of m.capabilities) expect(CAPS).toContain(c);
      expect(["local", "provider"]).toContain(m.runsOn);
      expect(["default", "available", "planned"]).toContain(m.status);
      expect(typeof m.openWeights).toBe("boolean");
      if (m.contextWindow !== null) expect(Number.isInteger(m.contextWindow) && m.contextWindow > 0).toBe(true);
      if (m.licence.url) expect(m.licence.url).toMatch(/^https:\/\//);
    }
  });

  it("has unique ids, and never an internal row", () => {
    expect(new Set(MODELS.map((m) => m.id)).size).toBe(MODELS.length);
    expect(MODELS.map((m) => m.status as string)).not.toContain("internal");
  });

  it("never makes a non-commercial model a default — we sell to firms", () => {
    for (const m of MODELS) {
      expect(["yes", "conditions", "no"]).toContain(m.commercialUse);
      if (m.status === "default") expect(m.commercialUse).not.toBe("no");
    }
  });

  it("claims no verification the data does not hold", () => {
    for (const m of MODELS) if (m.lastVerified !== null) expect(m.lastVerified).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

const sample = (over: Partial<ModelEntry>): ModelEntry => ({
  id: "x",
  name: "X",
  vendor: "V",
  family: "F",
  capabilities: ["chat"],
  runsOn: "local",
  runtime: "Ollama",
  openWeights: true,
  licence: { name: "Apache-2.0", url: null },
  commercialUse: "yes",
  contextWindow: 1000,
  status: "available",
  lastVerified: null,
  ...over,
});

describe("filters", () => {
  const models = [
    sample({ id: "a", capabilities: ["chat"], runsOn: "local" }),
    sample({ id: "b", capabilities: ["chat", "reasoning"], runsOn: "provider" }),
    sample({ id: "c", capabilities: ["image"], runsOn: "local" }),
  ];

  it("filters by capability (any of) and by where it runs", () => {
    expect(filterModels(models, { capabilities: [], runsOn: null }).map((m) => m.id)).toEqual(["a", "b", "c"]);
    expect(filterModels(models, { capabilities: ["chat"], runsOn: null }).map((m) => m.id)).toEqual(["a", "b"]);
    expect(filterModels(models, { capabilities: ["chat", "image"], runsOn: "local" }).map((m) => m.id)).toEqual([
      "a",
      "c",
    ]);
    expect(filterModels(models, { capabilities: ["reasoning"], runsOn: "local" })).toEqual([]);
  });

  it("reads the address and ignores what it does not know", () => {
    expect(readFilters(new URLSearchParams("cap=chat,telepathy,chat&where=local"), models)).toEqual({
      capabilities: ["chat"],
      runsOn: "local",
    });
    expect(readFilters(new URLSearchParams("cap=video&where=mars"), models)).toEqual({
      capabilities: [],
      runsOn: null,
    });
  });

  it("writes the address back, and the empty filter is /models", () => {
    expect(filtersHref({ capabilities: ["chat", "code"], runsOn: "provider" })).toBe(
      "/models?cap=chat%2Ccode&where=provider",
    );
    expect(filtersHref({ capabilities: [], runsOn: null })).toBe("/models");
  });

  it("lists the capabilities present in the registry, in reading order", () => {
    expect(capabilitiesOf(models)).toEqual(["chat", "reasoning", "image"]);
  });
});

describe("the home page's selection", () => {
  it("shows at most six local models, one per capability, never a planned one", () => {
    const six = featured();
    expect(six.length).toBeGreaterThan(0);
    expect(six.length).toBeLessThanOrEqual(6);
    for (const m of six) {
      expect(m.runsOn).toBe("local");
      expect(m.status).not.toBe("planned");
    }
    expect(new Set(six.map((m) => m.capabilities[0])).size).toBe(six.length);
  });

  it("prefers a default, then a permissive licence, then the plainest family — read from the data", () => {
    const rows = [
      sample({ id: "fine-tune", family: "Qwen 2.5 (unfiltered)" }),
      sample({ id: "base", family: "Qwen 2.5" }),
      sample({ id: "restricted", family: "Q", licence: { name: "Community Licence", url: null } }),
      sample({
        id: "vision-default",
        capabilities: ["vision"],
        status: "default",
        licence: { name: "Custom", url: null },
      }),
    ];
    expect(featured(rows, 6).map((m) => m.id)).toEqual(["base", "vision-default"]);
    expect(featured(rows, 1).map((m) => m.id)).toEqual(["base"]);
  });

  it("counts the optional provider models without listing them", () => {
    expect(providerCount([sample({ runsOn: "provider" }), sample({}), sample({ runsOn: "provider" })])).toBe(2);
  });
});
