/**
 * @jest-environment node
 */
import { hasMedia, mediaPaths, mediaUrl } from "@/core/media/cdn";
import data from "@/core/media/media.generated.json";

// Bunny layout v2 (ADR-014, krizaka/.github#368): media are `sites/<site>/<kind>/…` at the root of the zone.
// The legacy `site/products/…` objects are deleted once released: nothing may name them again.
describe("media layout v2", () => {
  it("every recorded file sits under sites/<site>/ and the manifest has no legacy root", () => {
    expect(Object.keys(data)).toEqual(["files"]);
    for (const path of Object.keys(data.files)) expect(path).toMatch(/^sites\/[a-z]+\/[a-z-]+\//);
  });

  it("an address is the hashed object at the root of the zone, never under site/", () => {
    const [path] = mediaPaths("sites/orazaka/mobile/");
    expect(hasMedia(path!)).toBe(true);
    expect(mediaUrl(path!)).toMatch(
      /^https:\/\/krizaka-assets\.b-cdn\.net\/sites\/orazaka\/mobile\/.+\.[0-9a-f]{10}\.(webm|mp4|webp)$/,
    );
    expect(() => mediaUrl("products/orazaka/mobile/ask-dark-916.mp4")).toThrow();
  });
});

describe("mediaPaths", () => {
  it("lists only the files under the prefix, sorted and without duplicates", () => {
    const paths = mediaPaths("sites/orazaka/");
    expect(paths.length).toBeGreaterThan(0);
    for (const path of paths) {
      expect(path.startsWith("sites/orazaka/")).toBe(true);
    }
    expect(paths).toEqual([...paths].sort((a, b) => a.localeCompare(b)));
    expect(new Set(paths).size).toBe(paths.length);
  });

  it("is empty for a prefix nothing is recorded under", () => {
    expect(mediaPaths("sites/nobody/")).toEqual([]);
  });
});
