import { render, screen } from "@testing-library/react";
import { ThemeProvider } from "@krizaka/ui/theme";
import { PhoneSection } from "@/features/landing/components/PhoneSection";
import { translations } from "@/core/context/translations";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations.en, locale: "en", setLocale: jest.fn() }),
}));

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
  class IO {
    observe() {}
    disconnect() {}
    unobserve() {}
    takeRecords() {
      return [];
    }
  }
  Object.defineProperty(window, "IntersectionObserver", { writable: true, value: IO });
});

describe("PhoneSection", () => {
  it("says the phone app once, in words and in one shared Clip, with its captions in both languages", () => {
    const { container } = render(
      <ThemeProvider>
        <PhoneSection id="phone-ask" words={translations.en.desktop.phone} testId="p" />
      </ThemeProvider>,
    );
    expect(screen.getByRole("heading", { level: 2 }).textContent).toBe(translations.en.desktop.phone.title);
    expect(screen.getByTestId("clip-phone-ask")).toBeTruthy();
    expect(container.querySelectorAll("track")).toHaveLength(2);
    expect(container.querySelectorAll("video")).toHaveLength(1);
  });

  it("has the French words as different text", () => {
    for (const k of ["eyebrow", "title", "lead"] as const) {
      expect(translations.fr.landing.phone[k]).not.toBe(translations.en.landing.phone[k]);
      expect(translations.fr.desktop.phone[k]).not.toBe(translations.en.desktop.phone[k]);
    }
  });
});
