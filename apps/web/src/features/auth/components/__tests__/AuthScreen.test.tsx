import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { AuthScreen } from "@/features/auth/components/AuthScreen";
import { translations } from "@/core/context/translations";

// Dev preview `ui.themes-devices` (orazaka-web-client#124): the UI crawl found two console errors on /login of the
// deployed site — a <div> inside the <p> of AuthLayout's legal line (React error #418, the whole tree re-rendered on the
// client) and a prefetch of /contact, which answers a redirect to www.krizaka.com (a CORS error on every visit).
const t = translations.en;

jest.mock("next/link", () => {
  const Link = ({ children, prefetch, ...props }: { children: React.ReactNode; href: string; prefetch?: boolean }) => (
    <a data-prefetch={String(prefetch)} {...props}>
      {children}
    </a>
  );
  return Link;
});

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: jest.requireActual("@/core/context/translations").translations.en,
    locale: "en",
    setLocale: jest.fn(),
  }),
}));

describe("AuthScreen", () => {
  it("renders without invalid HTML nesting (no <div> inside a <p>)", () => {
    const errors = jest.spyOn(console, "error").mockImplementation(() => {});
    const { container } = render(
      <AuthScreen title="Sovkora">
        <form aria-label="sign in" />
      </AuthScreen>,
    );
    expect(container.querySelector("p div")).toBeNull();
    expect(errors.mock.calls.flat().join(" ")).not.toMatch(/cannot be a descendant|cannot contain a nested/);
    errors.mockRestore();
  });

  it("does not prefetch Contact: it leaves for www.krizaka.com", () => {
    render(
      <AuthScreen title="Sovkora">
        <span />
      </AuthScreen>,
    );
    const contact = screen.getByRole("link", { name: t.auth.legalContact });
    expect(contact).toHaveAttribute("href", "/contact");
    expect(contact).toHaveAttribute("data-prefetch", "false");
  });

  it("says Sovkora is in development, like Yomizaka and Akinaza, and shows the duck where they show their bird (orazaka#516)", () => {
    const { container } = render(
      <AuthScreen title="Sovkora">
        <span />
      </AuthScreen>,
    );
    expect(screen.getByTestId("development-status")).toHaveTextContent(t.landing.status.development);
    expect(t.landing.status.development).toMatch(/^In development/);
    expect(translations.fr.landing.status.development).toMatch(/^En développement/);
    expect(
      container.querySelector('[data-bird="duck"], [data-testid="mascot-duck"], svg[aria-label*="duck" i]'),
    ).not.toBeNull();
  });
});
