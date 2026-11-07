import "@testing-library/jest-dom";
import { mockReducedMotion } from "@/features/landing/utils/reduced-motion.testkit";
import { render, screen } from "@testing-library/react";
import { ThemeProvider } from "@krizaka/ui/theme";
import { translations, type Locale } from "@/core/context/translations";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import { DesktopPage } from "@/features/landing/components/DesktopPage";

let mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

beforeAll(mockReducedMotion);

const IOS = "https://apps.apple.com/app/orazaka/id1234567890";
const ANDROID = "https://play.google.com/store/apps/details?id=com.krizaka.orazaka";

function renderPage(locale: Locale, stores?: { ios: string | null; android: string | null }) {
  mockLocale = locale;
  return render(
    <AuthFlagsProvider flags={{ login: false, signup: false, github: false, google: false }}>
      <ThemeProvider>
        <DesktopPage release={null} stores={stores} />
      </ThemeProvider>
    </AuthFlagsProvider>,
  );
}

describe.each<Locale>(["en", "fr"])("the mobile apps on the Download page (%s)", (locale) => {
  const m = translations[locale].desktop.mobile;

  it("says « coming soon » with no button and no link while no store URL is set", () => {
    renderPage(locale, { ios: null, android: null });
    expect(screen.getByText(m.soon)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: m.ios.button })).toBeNull();
    expect(screen.queryByRole("link", { name: m.android.button })).toBeNull();
    expect(
      document.querySelector(
        "a[href*='apple.com'], a[href*='play.google.com'], a[href*='testflight'], a[href$='.apk']",
      ),
    ).toBeNull();
  });

  it("is the same without any configuration passed", () => {
    renderPage(locale);
    expect(screen.getByText(m.soon)).toBeInTheDocument();
  });

  it("shows the App Store button alone when only its URL is set, and the line for the other", () => {
    renderPage(locale, { ios: IOS, android: null });
    expect(screen.getByRole("link", { name: m.ios.button })).toHaveAttribute("href", IOS);
    expect(screen.queryByRole("link", { name: m.android.button })).toBeNull();
    expect(screen.getByText(m.android.soon)).toBeInTheDocument();
  });

  it("shows both buttons, and no « coming soon », once both URLs are set", () => {
    renderPage(locale, { ios: IOS, android: ANDROID });
    expect(screen.getByRole("link", { name: m.ios.button })).toHaveAttribute("href", IOS);
    expect(screen.getByRole("link", { name: m.android.button })).toHaveAttribute("href", ANDROID);
    expect(screen.queryByText(m.soon)).toBeNull();
  });
});

it("has the same words in English and French", () => {
  const shape = (v: unknown): unknown =>
    v && typeof v === "object" ? Object.fromEntries(Object.entries(v).map(([k, x]) => [k, shape(x)])) : typeof v;
  expect(shape(translations.fr.desktop.mobile)).toEqual(shape(translations.en.desktop.mobile));
  expect(translations.fr.desktop.mobile.soon).toBe("Bientôt sur iPhone et Android");
  expect(translations.en.desktop.mobile.soon).toBe("Coming to iPhone and Android");
});
