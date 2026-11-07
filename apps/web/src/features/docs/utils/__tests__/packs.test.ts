import { filterPacks, packAnchor, parsePack, parsePackIndex, type PackIndexItem } from "@/features/docs/utils/packs";

const items: PackIndexItem[] = [
  { key: "a", title: "Real-Estate Studio", tagline: null, tier: "DATA", category: "Sales", studios: 1 },
  { key: "b", title: "Document Verification", tagline: null, tier: "WORKER", category: "Legal", studios: 2 },
  { key: "c", title: "Media Studios", tagline: null, tier: "DATA", category: "Creative", studios: 10 },
];

describe("pack blocks", () => {
  it("filters by tier and category", () => {
    expect(filterPacks(items, null, null)).toHaveLength(3);
    expect(filterPacks(items, "DATA", null).map((i) => i.key)).toEqual(["a", "c"]);
    expect(filterPacks(items, "DATA", "Creative").map((i) => i.key)).toEqual(["c"]);
    expect(filterPacks(items, "WORKER", "Sales")).toEqual([]);
  });

  it("anchors a pack the way the page's headings are slugged", () => {
    expect(packAnchor("Real-Estate Studio")).toBe("real-estate-studio");
    expect(packAnchor("Document Verification")).toBe("document-verification");
  });

  it("refuses a block that is not the shape the cards need (the build fails)", () => {
    expect(() => parsePack("not json")).toThrow(/not valid JSON/);
    expect(() => parsePack('{"key":"x"}')).toThrow(/expected/);
    expect(() => parsePackIndex('[{"nope":1}]')).toThrow(/expected/);
    expect(parsePackIndex(JSON.stringify(items))).toHaveLength(3);
  });
});

describe("packAnchor edges", () => {
  it("trims the dashes of the slug and keeps the inside ones", () => {
    expect(packAnchor("  -Hello, World!-  ")).toBe("hello-world");
    expect(packAnchor("***")).toBe("");
  });
});
