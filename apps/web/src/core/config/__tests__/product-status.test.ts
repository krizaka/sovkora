import { translations, type Locale } from "@/core/context/translations";
import { PRODUCT_STATUS, inDevelopment } from "@/core/config/product-status";

/**
 * krizaka/krizaka-com#468: while Sovkora is in development, no public copy (landing, pricing, desktop, mobile,
 * open source) may say it is available or can be bought. Words are listed in en and fr.
 */
const FORBIDDEN = [
  /\bavailable\b/i,
  /\bdisponibles?\b/i,
  /\bbuy\b/i,
  /\bpurchase\b/i,
  /\bsubscribe\b/i,
  /\bcheckout\b/i,
  /\bacheter\b/i,
  /\bs['’]abonner\b/i,
  /\bget started\b/i,
  /\bcommencer\b/i,
];

const PUBLIC_SECTIONS = ["landing", "pricing", "desktop", "mobile", "openSource"] as const;

function leaves(value: unknown, path: string): [string, string][] {
  if (typeof value === "string") return [[path, value]];
  if (Array.isArray(value)) return value.flatMap((v, i) => leaves(v, `${path}[${i}]`));
  if (value && typeof value === "object") return Object.entries(value).flatMap(([k, v]) => leaves(v, `${path}.${k}`));
  return [];
}

describe("product status (one source)", () => {
  it("is « development » and keeps public prices closed", () => {
    expect(PRODUCT_STATUS).toBe("development");
    expect(inDevelopment()).toBe(true);
    expect(inDevelopment("available")).toBe(false);
  });

  describe.each(["en", "fr"] as const)("public copy (%s)", (locale: Locale) => {
    it.each(PUBLIC_SECTIONS)("%s makes no availability, buy or sign-up claim", (section) => {
      if (!inDevelopment()) return;
      const hits = leaves((translations[locale] as unknown as Record<string, unknown>)[section], section).filter(
        ([, text]) => FORBIDDEN.some((re) => re.test(text)),
      );
      expect(hits).toEqual([]);
    });
  });
});
