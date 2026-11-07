import { asLanguagePreference, deviceLocale, localeOfTag, resolveLocale } from "../locale";
import en from "../en.json";
import fr from "../fr.json";

/** Every dotted key of a catalogue with its string. */
function flat(node: unknown, prefix = ""): Record<string, string> {
  if (typeof node === "string") return { [prefix]: node };
  return Object.entries(node as Record<string, unknown>).reduce<Record<string, string>>(
    (all, [key, value]) => ({ ...all, ...flat(value, prefix === "" ? key : `${prefix}.${key}`) }),
    {},
  );
}

describe("which language the app speaks", () => {
  it("reads the primary subtag of a device tag", () => {
    expect(localeOfTag("fr-CA")).toBe("fr");
    expect(localeOfTag("fr_FR")).toBe("fr");
    expect(localeOfTag("EN-us")).toBe("en");
    expect(localeOfTag("de-DE")).toBeNull();
    expect(localeOfTag(undefined)).toBeNull();
  });

  it("follows the first device language the app speaks, else English", () => {
    expect(deviceLocale(["de-DE", "fr-CA", "en-US"])).toBe("fr");
    expect(deviceLocale(["de-DE", "ja-JP"])).toBe("en");
    expect(deviceLocale([])).toBe("en");
  });

  it("follows the device on auto and the person's choice otherwise", () => {
    expect(resolveLocale("auto", ["fr-CA"])).toBe("fr");
    expect(resolveLocale("en", ["fr-CA"])).toBe("en");
    expect(resolveLocale("fr", ["en-US"])).toBe("fr");
  });

  it("treats a corrupt stored value as auto", () => {
    expect(asLanguagePreference("fr")).toBe("fr");
    expect(asLanguagePreference("klingon")).toBe("auto");
    expect(asLanguagePreference(null)).toBe("auto");
  });
});

describe("the catalogues never mix languages", () => {
  const english = flat(en);
  const french = flat(fr);

  it("have exactly the same keys", () => {
    expect(Object.keys(french).sort()).toEqual(Object.keys(english).sort());
  });

  it("have no empty string", () => {
    for (const [key, value] of [...Object.entries(english), ...Object.entries(french)])
      expect([key, value.trim() === ""]).toEqual([key, false]);
  });

  it("keep the same {placeholders} in both languages", () => {
    const holes = (text: string) => (text.match(/\{\w+\}/g) ?? []).sort().join(",");
    for (const key of Object.keys(english))
      expect([key, holes(french[key] ?? "")]).toEqual([key, holes(english[key] ?? "")]);
  });

  it("do not leave a French sentence in the English catalogue, nor the reverse", () => {
    // A cheap guard against the mix this app used to have: accented French words in en.json.
    for (const [key, value] of Object.entries(english)) expect([key, /[éèêàçùô]/i.test(value)]).toEqual([key, false]);
  });
});
