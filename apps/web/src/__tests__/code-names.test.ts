/** @jest-environment node */
import { findCodeNames } from "@krizaka/org";
import { translations } from "@/core/context/translations";
import { llmsText, SITE_DESCRIPTION, SITE_NAME, SITE_TITLE } from "@/app/_seo/seo";

/** Every string of a dictionary, with its path, so a hit says where it is. */
function strings(value: unknown, path = ""): [string, string][] {
  if (typeof value === "string") {
    return [[path, value]];
  }
  if (value && typeof value === "object") {
    return Object.entries(value).flatMap(([key, child]) => strings(child, path ? `${path}.${key}` : key));
  }
  return [];
}

/** Fields drawn as code (a command to type): the CLI keeps its code name, like the built pages' `<code>`. */
const COMMAND_FIELD = /\.(code|terminal)$/;

/**
 * The commercial names (krizaka-internal#2103): a customer reads Sovkora, never the code name. The built pages are
 * checked by `scripts/check-code-names.mjs`; this is the same rule on the words, in both languages, before a build.
 */
describe("no code name in what the customer reads", () => {
  for (const locale of ["en", "fr"] as const) {
    it(`the ${locale} messages`, () => {
      const hits = strings(translations[locale])
        .filter(([path]) => !COMMAND_FIELD.test(path))
        .flatMap(([path, text]) => findCodeNames(text).map((h) => `${path}: ${h.word} -> ${h.use}`));
      expect(hits).toEqual([]);
    });
  }

  it("the site name, title, description and llms.txt say Sovkora", () => {
    expect(SITE_NAME).toBe("Sovkora");
    expect(SITE_TITLE).toContain("Sovkora");
    for (const text of [SITE_NAME, SITE_TITLE, SITE_DESCRIPTION]) {
      expect(findCodeNames(text)).toEqual([]);
    }
    expect(llmsText()).toContain("Sovkora");
  });
});
