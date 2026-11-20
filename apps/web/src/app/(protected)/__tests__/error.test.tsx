import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import ConsoleError from "@/app/(protected)/error";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.fr }),
}));

describe("the console error boundary (krizaka-internal#2414)", () => {
  it("says what happened in the page's language and retries on demand", () => {
    const reset = jest.fn();
    render(<ConsoleError error={new Error("boom")} reset={reset} />);
    expect(screen.getByRole("heading", { level: 1, name: "Cette page n'a pas pu s'afficher" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Réessayer" }));
    expect(reset).toHaveBeenCalledTimes(1);
  });
});
