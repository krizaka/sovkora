import { translations } from "@/core/context/translations";
import { hasMedia, mediaPaths } from "@/core/media/cdn";
import { COMPARE_IMAGES, COMPARE_INSPIRATIONS, COMPARE_MODELS, imagesOf } from "@/features/landing/utils/model-compare";

describe("Compare models", () => {
  it("lists every image the media manifest publishes — no hand list — each with the record of its run", () => {
    const published = mediaPaths("sites/orazaka/compare/");
    expect(published.length).toBeGreaterThanOrEqual(12);
    expect(COMPARE_IMAGES.map((image) => `${image.stem}.webp`).sort()).toEqual(published);
    for (const image of COMPARE_IMAGES) {
      expect(image.runId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
      expect(image.seconds).toBeGreaterThan(0);
      expect(image.width * image.height).toBeGreaterThan(0);
      expect(hasMedia(`${image.stem}.webp`)).toBe(true);
      expect(image.src).toMatch(
        /^https:\/\/krizaka-assets\.b-cdn\.net\/sites\/orazaka\/compare\/[a-z0-9-]+--[a-z0-9-]+\.[0-9a-f]{10}\.webp$/,
      );
    }
  });

  it("says honestly where each model ran and what it cost: local models on the Mac with no price, the hosted one on DigitalOcean at $0.08", () => {
    const where = Object.fromEntries(COMPARE_MODELS.map((model) => [model.id, [model.where, model.costUsd]]));
    expect(where["flux-2-klein-4b"]).toEqual(["mac", undefined]);
    expect(where["v1-5-pruned-emaonly"]).toEqual(["mac", undefined]);
    expect(where["stable-diffusion-3.5-large"]).toEqual(["digitalocean", 0.08]);
    expect(COMPARE_MODELS.find((model) => model.id === "stable-diffusion-3.5-large")?.seedHonoured).toBe(false);
  });

  it("gives every inspiration the same prompt for every model, and no brand or copyrighted name", () => {
    for (const locale of ["en", "fr"] as const) expect(translations[locale].landing.records.prompt.trim()).toBeTruthy();
    for (const inspiration of COMPARE_INSPIRATIONS)
      expect(inspiration.prompt).not.toMatch(/iron man|marvel|snk|king of fighters/i);
    expect(new Set(COMPARE_INSPIRATIONS.map((item) => item.id)).size).toBe(COMPARE_INSPIRATIONS.length);
  });

  it("returns an inspiration's images in the order of the models, and each model once", () => {
    for (const inspiration of COMPARE_INSPIRATIONS) {
      const images = imagesOf(inspiration.id);
      expect(images.map((image) => COMPARE_MODELS.indexOf(image.model))).toEqual(
        [...images.map((image) => COMPARE_MODELS.indexOf(image.model))].sort((a, b) => a - b),
      );
      expect(new Set(images.map((image) => image.model.id)).size).toBe(images.length);
    }
  });
});
