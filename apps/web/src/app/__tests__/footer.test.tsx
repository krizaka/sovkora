import "@testing-library/jest-dom";
import { render, screen, within } from "@testing-library/react";
import { ThemeProvider } from "@krizaka/ui/theme";
import { translations, type Locale } from "@/core/context/translations";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import PrivacyPage from "@/app/privacy/page";
import TermsPage from "@/app/terms/page";
import { LandingPage } from "@/features/landing/components/LandingPage";

/* One footer, one contentinfo: the public pages end with @krizaka/ui's SiteFooter (LandingFooter) and nothing else — the
   hand-made legal footers of privacy, terms are gone. The signed-in shell (sidebar) has none. */
let mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));
jest.mock("@krizaka/ui/toast", () => ({ toast: { error: jest.fn(), success: jest.fn() } }));

beforeAll(() => {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: query.includes("reduce"),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      onchange: null,
      dispatchEvent: () => false,
    }),
  });
});

const FLAGS = { login: false, signup: false, github: false, google: false };

describe.each<[string, React.ReactElement]>([
  ["/", <LandingPage key="home" />],
  ["/privacy", <PrivacyPage key="privacy" />],
  ["/terms", <TermsPage key="terms" />],
])("%s", (_route, page) => {
  it.each<Locale>(["en", "fr"])("renders exactly one contentinfo, the product's (%s), outside main", (locale) => {
    mockLocale = locale;
    const { container } = render(
      <AuthFlagsProvider flags={FLAGS}>
        <ThemeProvider>{page}</ThemeProvider>
      </AuthFlagsProvider>,
    );
    const footers = screen.getAllByRole("contentinfo");
    expect(footers).toHaveLength(1);
    expect(container.querySelectorAll("footer")).toHaveLength(1);
    expect(footers[0].closest("main")).toBeNull();
    expect(footers[0]).toHaveTextContent(/Krizaka/);
    expect(screen.queryByRole("navigation", { name: /famil/i })).toBeNull();
    // Open source is this site's own page (#290); the contact form lives on krizaka.com (krizaka/orazaka#443).
    expect(
      within(screen.getByRole("contentinfo")).getByRole("link", {
        name: translations[locale].landing.footer.openSource,
      }),
    ).toHaveAttribute("href", "/open-source");
    expect(screen.getByRole("link", { name: translations[locale].landing.footer.contact })).toHaveAttribute(
      "href",
      `https://www.krizaka.com/${locale}/contact?topic=sovkora&source=sovkora-app`,
    );
  });
});
