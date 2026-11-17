import { landing } from "@/core/context/translations.landing";
import { LICENCE_ID } from "@/core/context/translations.licence";

/** Every leaf of a dictionary as a dot path, array items by index. */
function paths(node: unknown, prefix = ""): string[] {
  if (typeof node !== "object" || node === null) {
    return [prefix];
  }
  return Object.entries(node).flatMap(([key, value]) => paths(value, prefix ? `${prefix}.${key}` : key));
}

describe("landing words: one tree, two languages", () => {
  it("gives both languages the same keys", () => {
    expect(paths(landing.fr)).toEqual(paths(landing.en));
  });

  it("names the product's own licence in the footer of both languages", () => {
    expect(landing.en.footer.license).toBe(`Licence: ${LICENCE_ID}`);
    expect(landing.fr.footer.license).toBe(`Licence ${LICENCE_ID}`);
  });

  it("calls the source link by what it is in both languages", () => {
    expect(landing.en.nav.openSource).toBe("Source code");
    expect(landing.fr.nav.openSource).toBe("Code source");
  });
});
