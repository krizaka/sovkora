import { negotiateLocale, splitLocalePrefix } from "@/core/i18n/locale-negotiation";

describe("negotiateLocale (Loi 96)", () => {
  test("the stored choice wins", () => {
    expect(negotiateLocale("en", ["fr-CA"])).toBe("en");
    expect(negotiateLocale("fr", ["en-US"])).toBe("fr");
  });
  test("a French browser, no browser, any other language: French", () => {
    expect(negotiateLocale(null, ["fr-CA", "en"])).toBe("fr");
    expect(negotiateLocale(null, [])).toBe("fr");
    expect(negotiateLocale(null, ["de-DE"])).toBe("fr");
  });
  test("English only when the browser asks for English and never for French", () => {
    expect(negotiateLocale(null, ["en-US", "en"])).toBe("en");
    expect(negotiateLocale(undefined, ["en", "fr"])).toBe("fr");
  });
});

describe("splitLocalePrefix", () => {
  test("names the language and keeps the page", () => {
    expect(splitLocalePrefix("/fr/login")).toEqual({ locale: "fr", path: "/login" });
    expect(splitLocalePrefix("/en")).toEqual({ locale: "en", path: "/" });
    expect(splitLocalePrefix("/french")).toEqual({ locale: null, path: "/french" });
    expect(splitLocalePrefix("/login")).toEqual({ locale: null, path: "/login" });
  });
});
