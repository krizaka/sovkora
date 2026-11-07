import "@testing-library/jest-dom";
import { mockReducedMotion } from "@/features/landing/utils/reduced-motion.testkit";
import { render, screen, within } from "@testing-library/react";
import { ThemeProvider } from "@krizaka/ui/theme";
import { translations, type Locale } from "@/core/context/translations";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import { contactUrl } from "@/core/config/contact";
import { PricingEditions } from "@/features/landing/components/PricingEditions";
import type { PublicPrice } from "@/features/landing/utils/public-prices";
import { publicPaths } from "@/app/_seo/seo";

jest.mock("next/navigation", () => ({ usePathname: () => "/pricing", useRouter: () => ({ push: jest.fn() }) }));
let mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

beforeAll(mockReducedMotion);

function renderPage(locale: Locale, prices: PublicPrice[] = []) {
  mockLocale = locale;
  return render(
    <AuthFlagsProvider flags={{ login: false, signup: false, github: false, google: false }}>
      <ThemeProvider>
        <PricingEditions prices={prices} />
      </ThemeProvider>
    </AuthFlagsProvider>,
  );
}

describe.each(["en", "fr"] as const)("/pricing (%s)", (locale) => {
  it("shows the three editions, one h1, and no amount of money", () => {
    const { container } = renderPage(locale);
    const p = translations[locale].pricing;
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    for (const name of [p.community.name, p.business.name, p.enterprise.name])
      expect(screen.getAllByText(name).length).toBeGreaterThan(0);
    // Prices are data (ADR-009) and the catalogue has no Sovkora plan: nothing on the page looks like a price.
    expect(container.textContent).not.toMatch(/[$€£]|\d\s?(\$|CAD|USD|EUR)|\bper month\b|\/\s?mois/i);
  });

  it("sends anything custom to Krizaka's Enterprise offer, through the contact form", () => {
    renderPage(locale);
    const links = screen.getAllByRole("link", { name: translations[locale].pricing.enterprise.cta });
    expect(links.length).toBeGreaterThan(0);
    for (const l of links) expect(l).toHaveAttribute("href", contactUrl(locale));
  });

  it("says Business is not published, and Community links the Mac app", () => {
    renderPage(locale);
    const p = translations[locale].pricing;
    expect(screen.getAllByText(p.business.soon).length).toBeGreaterThan(0);
    expect(
      screen.getAllByRole("link", { name: p.community.download }).some((a) => a.getAttribute("href") === "/desktop"),
    ).toBe(true);
  });

  it("the footer links the page", () => {
    renderPage(locale);
    const footer = screen.getAllByRole("contentinfo")[0];
    expect(within(footer).getByRole("link", { name: translations[locale].landing.footer.pricing })).toHaveAttribute(
      "href",
      "/pricing",
    );
  });
});

describe("/pricing reads the price catalogue (krizaka/orazaka#417)", () => {
  const entry = (over: Partial<PublicPrice>): PublicPrice => ({
    key: "orazaka.business",
    unit: "year",
    amountCents: 290000,
    availability: "public",
    limits: {},
    ...over,
  });

  it("keeps a published catalogue price off the page while Sovkora is in development (krizaka-com#468)", () => {
    const { container } = renderPage("en", [entry({})]);
    expect(container.textContent?.replace(/\s/g, " ")).not.toContain("2,900.00");
    expect(container.textContent).not.toContain(
      `${translations.en.pricing.units.year} · ${translations.en.pricing.plusTaxes}`,
    );
    expect(container.textContent).toContain(translations.en.pricing.business.soon);
  });

  it("keeps the edition's own words when the catalogue says preparing or internal", () => {
    const { container } = renderPage("fr", [
      entry({ availability: "preparing", amountCents: null }),
      entry({ key: "orazaka.enterprise", availability: "internal" }),
    ]);
    expect(container.textContent).not.toMatch(/[$€£]/);
    expect(screen.getAllByText(translations.fr.pricing.business.soon).length).toBeGreaterThan(0);
  });
});

describe("/pricing in the public surfaces", () => {
  it("is in the sitemap", () => expect(publicPaths()).toContain("/pricing"));
  it("has the same keys in both languages", () => {
    const keys = (o: unknown): string[] =>
      o && typeof o === "object" ? Object.entries(o).flatMap(([k, v]) => [k, ...keys(v).map((x) => `${k}.${x}`)]) : [];
    expect(keys(translations.fr.pricing)).toEqual(keys(translations.en.pricing));
  });
});
