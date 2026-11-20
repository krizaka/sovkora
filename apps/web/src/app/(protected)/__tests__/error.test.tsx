import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { translations, type Locale } from "@/core/context/translations";
import ConsoleError from "@/app/(protected)/error";

let mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

describe("the console error boundary (krizaka-internal#2414)", () => {
  it.each(["en", "fr"] as const)("says what happened and retries in %s", (locale) => {
    mockLocale = locale;
    const reset = jest.fn();
    render(<ConsoleError error={new Error("boom")} reset={reset} />);
    const e = translations[locale].routeError;
    expect(screen.getByRole("heading", { level: 1, name: e.title })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: e.retry }));
    expect(reset).toHaveBeenCalledTimes(1);
  });
});
