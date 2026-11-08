import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { translations, type Locale } from "@/core/context/translations";
import { Clip } from "@/core/clips/Clip";

let mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));
jest.mock("@/core/clips/useLook", () => ({ useLook: () => "dark" }));

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
  window.IntersectionObserver = class {
    observe() {}
    disconnect() {}
    unobserve() {}
    takeRecords() {
      return [];
    }
  } as unknown as typeof IntersectionObserver;
});

describe("a demo clip (krizaka/orazaka#443: no caption drawn over the screen)", () => {
  it.each(["en", "fr"] as const)(
    "keeps its captions off the picture, its caption as text and its alt in %s",
    (locale) => {
      mockLocale = locale;
      const { container } = render(<Clip id="take" />);
      const copy = translations[locale].clips.take;
      // The cues stay available as tracks (the viewer turns them on), but none is `default`: the browser draws a default track over the UI.
      const tracks = container.querySelectorAll("track");
      expect(tracks.length).toBe(2);
      for (const track of tracks) expect(track).not.toHaveAttribute("default");
      // The caption is real text under the picture, the alt describes it for assistive technology.
      expect(screen.getByText(copy.caption)).toBeInTheDocument();
      expect(screen.getByTestId("clip-take")).toHaveAttribute("aria-label", copy.alt);
    },
  );
});
