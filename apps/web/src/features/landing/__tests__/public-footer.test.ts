import { org } from "@krizaka/org";
import { landing } from "@/core/context/translations.landing";
import { publicFooter } from "@/features/landing/utils/public-footer";

const words = (locale: "en" | "fr") => landing[locale].footer;
const hrefs = (locale: "en" | "fr") =>
  publicFooter(locale, words(locale)).columns.flatMap((c) => c.links.map((l) => l.href));

describe("the public export's footer (content/public-footer.json)", () => {
  it("groups its links in columns, with only the pages the export holds", () => {
    const footer = publicFooter("fr", words("fr"));
    expect(footer.columns.map((c) => c.id)).toEqual(["product", "code", "publisher"]);
    expect(footer.columns[0].links.map((l) => l.label)).toEqual(["Accueil", "Documentation", "Tarifs"]);
    const internal = hrefs("fr").filter((h) => h.startsWith("/"));
    expect(internal).toEqual(["/", "/docs", "/pricing"]);
    for (const absent of ["/download", "/desktop", "/terms", "/login", "/open-source", "/docs/api", "/docs/cli"]) {
      expect(hrefs("fr")).not.toContain(absent);
      expect(hrefs("en")).not.toContain(absent);
    }
  });

  it("drops a link to a page the export does not hold, and a column left empty", () => {
    const data = {
      labelKey: "label",
      columns: [
        { id: "a", titleKey: "product", links: [{ labelKey: "terms", page: "terms" }] },
        { id: "b", titleKey: "product", links: [{ labelKey: "docs", page: "docs" }] },
      ],
      legal: { showVersion: false, links: [{ labelKey: "terms", page: "terms" }] },
      social: [],
    };
    const footer = publicFooter("en", words("en"), data, ["", "docs"]);
    expect(footer.columns.map((c) => c.id)).toEqual(["b"]);
    expect(footer.legal.links).toEqual([]);
  });

  it("says the legal line from @krizaka/org, with no version, and the product's status once", () => {
    const footer = publicFooter("en", words("en"));
    expect(footer.legal).toMatchObject({
      legalName: org.company.legalName,
      neq: org.company.neq,
      city: org.company.city,
      showVersion: false,
    });
    expect(footer.legal.links).toEqual([{ label: "Privacy", href: "/privacy" }]);
    expect(footer.columns[0].status).toBe(org.products.find((p) => p.id === "sovkora")?.status);
    expect(footer.columns.slice(1).every((c) => c.status === undefined)).toBe(true);
  });

  it("reads every outside address from @krizaka/org or the contact config, never from the data", () => {
    const repo = org.products.find((p) => p.id === "sovkora")?.repo;
    const external = publicFooter("fr", words("fr")).columns.flatMap((c) => c.links.filter((l) => l.external));
    expect(external.map((l) => l.href)).toEqual([
      repo,
      `${repo}/blob/main/NOTICE`,
      "https://www.krizaka.com/fr/contact?topic=sovkora&source=sovkora-app",
      "https://www.krizaka.com/fr",
    ]);
  });

  it("refuses a word key the catalogues do not hold", () => {
    const data = { labelKey: "nope", columns: [], legal: { showVersion: false, links: [] }, social: [] };
    expect(() => publicFooter("en", words("en"), data)).toThrow();
  });
});
