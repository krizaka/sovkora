import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { ConsoleLocaleProvider, localeFromLanguages, useConsoleLocale } from "@/console/core/providers/ConsoleLocale";

// The console's language (orazaka-web-client#275): the browser's first choice, French or English; a test or a future
// switch passes it.
function Probe() {
  return <p>{useConsoleLocale()}</p>;
}

describe("ConsoleLocale", () => {
  it("is French by default: English only when the browser asks for English and never for French (Loi 96)", () => {
    expect(localeFromLanguages(["fr-CA", "en"])).toBe("fr");
    expect(localeFromLanguages(["en-CA", "fr"])).toBe("fr");
    expect(localeFromLanguages(["en-US", "en"])).toBe("en");
    expect(localeFromLanguages(["de"])).toBe("fr");
    expect(localeFromLanguages([])).toBe("fr");
  });

  it("follows the browser without a locale, the given one with it", () => {
    const languages = jest.spyOn(navigator, "languages", "get").mockReturnValue(["fr-CA"]);
    const { unmount } = render(
      <ConsoleLocaleProvider>
        <Probe />
      </ConsoleLocaleProvider>,
    );
    expect(screen.getByText("fr")).toBeInTheDocument();
    unmount();
    render(
      <ConsoleLocaleProvider locale="en">
        <Probe />
      </ConsoleLocaleProvider>,
    );
    expect(screen.getByText("en")).toBeInTheDocument();
    languages.mockRestore();
  });

  it("is French outside a provider", () => {
    render(<Probe />);
    expect(screen.getByText("fr")).toBeInTheDocument();
  });
});
