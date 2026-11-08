import { translations } from "@/core/context/translations";

describe("landing copy", () => {
  /** The shape of a value: keys for objects, length and item shape for arrays. */
  const shape = (v: unknown): unknown =>
    Array.isArray(v)
      ? v.map(shape)
      : v && typeof v === "object"
        ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, shape(x)]))
        : typeof v;

  it("has the same shape in English and French, with no empty string", () => {
    expect(shape(translations.fr.landing)).toEqual(shape(translations.en.landing));
    const strings = (v: unknown): string[] =>
      typeof v === "string" ? [v] : v && typeof v === "object" ? Object.values(v).flatMap(strings) : [];
    for (const locale of ["en", "fr"] as const) {
      expect(strings(translations[locale].landing).filter((s) => s.trim() === "")).toEqual([]);
    }
  });
});
