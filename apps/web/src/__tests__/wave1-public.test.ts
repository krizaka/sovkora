/** @jest-environment node */
import { NextRequest } from "next/server";
import robots from "@/app/robots";
import sitemap from "@/app/sitemap";
import { siteMetadata } from "@/app/_seo/site-metadata";
import { contactUrl } from "@/core/config/contact";
import { developmentDeployment, indexable, isCodeNameAddress, NOINDEX_HEADER } from "@/core/config/indexing";
import { landing } from "@/core/context/translations.landing";
import { translations } from "@/core/context/translations";
import { localeHref, splitLocalePrefix } from "@/core/i18n/locale-negotiation";
import { proxy } from "@/proxy";

/** Wave 1 of the public-pages meeting (2026-11-05, krizaka-internal#2630): www.orazaka.com says one line and breaks no link. */

const strings = (value: unknown): string[] => {
  if (typeof value === "string") {
    return [value];
  }
  if (value && typeof value === "object") {
    return Object.values(value).flatMap(strings);
  }
  return [];
};

describe("French elision: a brand is never glued to an apostrophe", () => {
  it("no message writes qu'{brand}, d'{brand} or l'{brand} (Sovkora starts with a consonant, a brand may not)", () => {
    const hits = strings(translations.fr).filter((s) => /\b(qu|d|l|n|s|j|m|t|c)'\{brand\}/i.test(s));
    expect(hits).toEqual([]);
  });

  it("Ce que Sovkora sait faire", () => {
    expect(landing.fr.capabilities.title).toBe("Ce que {brand} sait faire, en mots simples");
    expect(landing.fr.capabilities.eyebrow).toBe("Ce que {brand} sait faire");
  });
});

describe("the hero says the product's one line, and no host", () => {
  it("L'IA qui reste chez vous / The AI that stays home", () => {
    expect(landing.fr.hero.title).toBe("L'IA qui reste chez vous.");
    expect(landing.fr.hero.lead).toBe("Vos modèles, vos machines : vos données ne sortent pas.");
    expect(landing.en.hero.title).toBe("The AI that stays home.");
    expect(landing.en.hero.lead).toBe("Your models, your machines: your data stays in.");
  });

  it("the mock-up label is the product's, not the old code-name host", () => {
    for (const locale of ["en", "fr"] as const) {
      const label = landing[locale].hero.visualLabel;
      expect(label).not.toMatch(/orazaka|www\.|\.com/i);
      expect(label).toMatch(/^\{brand\}/);
    }
  });
});

describe("the contact link follows the page's language and says where it comes from", () => {
  it("krizaka.com/{fr|en}/contact with the product and the site", () => {
    expect(contactUrl("fr")).toBe("https://www.krizaka.com/fr/contact?topic=sovkora&source=sovkora-app");
    expect(contactUrl("en")).toBe("https://www.krizaka.com/en/contact?topic=sovkora&source=sovkora-app");
    // No code name in a public address either (krizaka-internal#2630).
    expect(contactUrl("fr") + contactUrl("en")).not.toMatch(/orazaka/i);
  });
});

describe("the language switch has a real address, never #", () => {
  it("names the language in the path and keeps the page", () => {
    expect(localeHref("fr", "/pricing")).toBe("/fr/pricing");
    expect(localeHref("en", "/fr/pricing")).toBe("/en/pricing");
    expect(localeHref("en", "/")).toBe("/en");
    expect(localeHref("fr", "/en")).toBe("/fr");
    expect(splitLocalePrefix(localeHref("en", "/fr/docs/101"))).toEqual({ locale: "en", path: "/docs/101" });
  });
});

describe("the title and the Open Graph follow the page's language", () => {
  it("fr: title, description and og:locale in French", () => {
    const m = siteMetadata("fr", true);
    expect(m.title).toBe("Sovkora : l'IA qui reste chez vous");
    expect(String(m.description)).toMatch(/l'IA qui reste chez vous/);
    expect(m.openGraph).toMatchObject({ locale: "fr_CA", alternateLocale: "en_US", title: m.title });
    expect(m.twitter).toMatchObject({ title: m.title });
  });

  it("en: unchanged language, same line", () => {
    const m = siteMetadata("en", true);
    expect(String(m.title)).toMatch(/^Sovkora/);
    expect(m.openGraph).toMatchObject({ locale: "en_US", alternateLocale: "fr_CA" });
  });

  it("a closed site says noindex in the page", () => {
    expect(siteMetadata("fr", false).robots).toEqual({ index: false, follow: false, noarchive: true });
    expect(siteMetadata("fr", true).robots).toBeUndefined();
  });
});

describe("www.orazaka.com is a code-name host: noindex, meta, header and robots; no code name on screen", () => {
  const live = { APP_PUBLIC_URL: "https://www.orazaka.com" };

  it("knows the code-name hosts", () => {
    for (const host of ["www.orazaka.com", "orazaka.com", "www.yomizaka.com", "www.akinaza.com"]) {
      expect(`${host} ${isCodeNameAddress(host)}`).toBe(`${host} true`);
    }
    for (const host of ["www.sovkora.com", "www.krizaka.com", "", undefined, null]) {
      expect(`${host} ${isCodeNameAddress(host)}`).toBe(`${host} false`);
    }
  });

  it("is not indexable, yet it is not the development deployment (no banner, no code-name line)", () => {
    expect(indexable(live)).toBe(false);
    expect(developmentDeployment(live)).toBe(false);
    expect(developmentDeployment({ APP_SEARCH_INDEXING: "off" })).toBe(true);
    expect(developmentDeployment({ APP_PUBLIC_URL: "https://dev.orazaka.com" })).toBe(true);
    expect(indexable({ APP_PUBLIC_URL: "https://www.sovkora.com" })).toBe(true);
  });

  it("robots.txt disallows everything, the sitemap is empty, every answer carries X-Robots-Tag", () => {
    const keep = process.env.APP_PUBLIC_URL;
    process.env.APP_PUBLIC_URL = live.APP_PUBLIC_URL;
    try {
      expect(robots().rules).toEqual([{ userAgent: "*", disallow: "/" }]);
      expect(robots().sitemap).toBeUndefined();
      expect(sitemap()).toEqual([]);
      for (const path of ["/", "/fr", "/fr/pricing", "/login", "/robots.txt"]) {
        const res = proxy(new NextRequest(`https://www.orazaka.com${path}`, { headers: { host: "www.orazaka.com" } }));
        expect(`${path} ${res.headers.get("x-robots-tag")}`).toBe(`${path} ${NOINDEX_HEADER}`);
      }
    } finally {
      if (keep === undefined) {
        delete process.env.APP_PUBLIC_URL;
      } else {
        process.env.APP_PUBLIC_URL = keep;
      }
    }
  });
});
