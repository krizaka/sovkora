import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { translations, type Locale } from "@/core/context/translations";
import NotFound from "@/app/not-found";

let mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));
jest.mock("@/features/landing/components/LandingNav", () => ({ LandingNav: () => <nav aria-label="site" /> }));
jest.mock("@/features/landing/components/LandingFooter", () => ({ LandingFooter: () => <footer /> }));

describe("the 404 page (krizaka/orazaka#443)", () => {
  it.each(["en", "fr"] as const)("is the Error template with its code, one h1 and the ways back in %s", (locale) => {
    mockLocale = locale;
    render(<NotFound />);
    const n = translations[locale].notFound;
    expect(screen.getByRole("heading", { level: 1, name: n.title })).toBeInTheDocument();
    expect(screen.getByText("404")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: n.home })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: n.docs })).toHaveAttribute("href", "/docs");
    expect(screen.getByRole("link", { name: n.signIn })).toHaveAttribute("href", "/login");
  });
});
