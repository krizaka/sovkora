import { CLIPS } from "@/core/clips/clips";
import { MEDIA_REGISTRY } from "@/core/clips/media-registry";
import { productPageWords } from "@/core/context/translations.productPage";
import {
  PRODUCT_PAGE,
  homePageData,
  overBudget,
  parseTemplate,
  productScenes,
} from "@/features/landing/utils/product-page";

const LOCALES = ["fr", "en"] as const;
const LOOKS = ["dark", "light"] as const;
const blocked = MEDIA_REGISTRY.media.filter((m) => m.blocked !== undefined).map((m) => m.id);

describe("the product page template (content/product-page.json)", () => {
  it("refuses a template with no limit or a limit too many", () => {
    expect(() => parseTemplate({ ...PRODUCT_PAGE, limits: [] })).toThrow();
    expect(() => parseTemplate({ ...PRODUCT_PAGE, limits: ["a", "b", "c", "d"] })).toThrow();
  });

  it.each(LOCALES)("keeps every section of the %s words inside its budget", (locale) => {
    expect(overBudget(productPageWords(locale), PRODUCT_PAGE.budget)).toEqual([]);
  });

  it("has words for every limit and every scene, in both languages", () => {
    for (const locale of LOCALES) {
      const words = productPageWords(locale);
      for (const id of PRODUCT_PAGE.limits) {
        expect(words.limits.items[id as keyof typeof words.limits.items]).toBeTruthy();
      }
    }
  });

  it.each(LOCALES)("says nothing in the %s words with a tu form or a date promise", (locale) => {
    const text = JSON.stringify(productPageWords(locale));
    expect(text).not.toMatch(/\b(tu|ton|ta|tes|toi|ton)\b/i);
    expect(text).not.toMatch(/\b(20\d\d|bientôt|soon|coming)\b/i);
  });
});

describe("the scenes of the page (the media registry, by role)", () => {
  it.each(LOCALES.flatMap((l) => LOOKS.map((k) => [l, k] as const)))(
    "shows %s / %s recordings that may be shown",
    (locale, look) => {
      const scenes = productScenes(locale, look, productPageWords(locale).scenes);
      expect(scenes.map((s) => s.id)).toEqual(["phone-studio", "phone-jobs"]);
      for (const scene of scenes) {
        expect(blocked).not.toContain(scene.id);
        expect(scene.device).toBe("phone");
        expect(scene.sources.webm).toContain(look);
        expect(scene.width).toBe(CLIPS[scene.id as "phone-studio"].width);
      }
    },
  );

  it("leaves out a recording that has no words", () => {
    expect(productScenes("en", "dark", {})).toEqual([]);
  });
});

describe("the page's data", () => {
  it.each(LOCALES)("builds the %s page from the words, the registry and the template", (locale) => {
    const data = homePageData(locale, "dark", { docs: "/docs", features: "#what" });
    const words = productPageWords(locale);
    expect(data.brand).toBe("sovkora");
    expect(data.hero.title).toBe(words.hero.title);
    expect(data.hero.primary.href).toBe("/docs");
    expect(data.hero.secondary?.href).toBe("#what");
    expect(data.scenes?.items.length).toBe(2);
    expect(data.limits?.items).toEqual(
      PRODUCT_PAGE.limits.map((id) => words.limits.items[id as keyof typeof words.limits.items]),
    );
    expect(data.closing.action.href).toBe("/docs");
  });
});
