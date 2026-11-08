import type * as React from "react";
import "@testing-library/jest-dom";
import { mockReducedMotion } from "@/features/landing/utils/reduced-motion.testkit";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { SessionContext } from "next-auth/react";
import { ThemeProvider } from "@krizaka/ui/theme";
import { translations, type Locale } from "@/core/context/translations";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import type { AuthFlags } from "@/core/config/auth-flags";
import { LandingPage } from "@/features/landing/components/LandingPage";
import { LandingNav } from "@/features/landing/components/LandingNav";

const mockPath = { current: "/" };
jest.mock("next/navigation", () => ({ usePathname: () => mockPath.current, useRouter: () => ({ push: jest.fn() }) }));

let mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

beforeAll(mockReducedMotion);

const CLOSED: AuthFlags = { login: false, signup: false, github: false, google: false };

type SessionValue = React.ContextType<typeof SessionContext>;
const SIGNED_OUT = { data: null, status: "unauthenticated", update: async () => null };
const mockSession = { current: SIGNED_OUT as SessionValue };

function renderLanding(locale: Locale, page: React.ReactNode = <LandingPage />, flags: AuthFlags = CLOSED) {
  mockLocale = locale;
  return render(
    <AuthFlagsProvider flags={flags}>
      <SessionContext.Provider value={mockSession.current}>
        <ThemeProvider>{page}</ThemeProvider>
      </SessionContext.Provider>
    </AuthFlagsProvider>,
  );
}

const nav = translations.en.landing.nav;
const bar = () => document.querySelector("header[data-floating]") as HTMLElement;

describe("the one action of the bar: using the app", () => {
  afterEach(() => {
    mockSession.current = SIGNED_OUT as SessionValue;
  });

  it("is « Sign in » while sign-up is closed, with no « Talk to us » or Contact in the bar or the panel", () => {
    renderLanding("en", <LandingNav />);
    expect(within(bar()).getAllByRole("link", { name: nav.signIn })[0]).toHaveAttribute("href", "/login");
    expect(within(bar()).queryByRole("link", { name: nav.tryIt })).toBeNull();
    expect(within(bar()).queryByRole("link", { name: nav.openApp })).toBeNull();
    expect(within(bar()).queryByText(/talk to us|contact/i)).toBeNull();
  });

  it("is « Try Sovkora » once sign-up is open, read from the flag", () => {
    renderLanding("en", <LandingNav />, { ...CLOSED, login: true, signup: true });
    expect(within(bar()).getAllByRole("link", { name: nav.tryIt })[0]).toHaveAttribute("href", "/register");
  });

  it("is « Open the app » with the avatar when signed in, and « Sign in » again once signed out or expired", () => {
    mockSession.current = {
      data: { user: { name: "Oussama", email: "o@orazaka.io" }, expires: "2999-01-01" },
      status: "authenticated",
      update: async () => null,
    } as SessionValue;
    const { unmount } = renderLanding("en", <LandingNav />);
    expect(within(bar()).getAllByRole("link", { name: nav.openApp })[0]).toHaveAttribute("href", "/");
    expect(within(bar()).queryByRole("link", { name: nav.signIn })).toBeNull();
    expect(bar().querySelector("[data-slot=avatar], span[class*=rounded-full]")).not.toBeNull();
    unmount();
    mockSession.current = SIGNED_OUT as SessionValue;
    renderLanding("en", <LandingNav />);
    expect(within(bar()).getAllByRole("link", { name: nav.signIn }).length).toBeGreaterThan(0);
    expect(within(bar()).queryByRole("link", { name: nav.openApp })).toBeNull();
  });

  it("while next-auth is still loading the session the bar draws NEITHER state: a signed-in person never sees « Sign in » flash (krizaka-ui#800)", () => {
    mockSession.current = { data: undefined, status: "loading", update: async () => null } as unknown as SessionValue;
    renderLanding("en", <LandingNav />, { ...CLOSED, login: true, signup: true });
    expect(within(bar()).queryByRole("link", { name: nav.signIn })).toBeNull();
    expect(within(bar()).queryByRole("link", { name: nav.tryIt })).toBeNull();
    expect(within(bar()).queryByRole("link", { name: nav.openApp })).toBeNull();
    expect(bar().querySelector("[data-session=pending]")).not.toBeNull();
  });

  it("keeps the signed-in avatar visible in the mobile bar and open panel", () => {
    mockSession.current = {
      data: { user: { name: "Oussama", email: "o@orazaka.io" }, expires: "2999-01-01" },
      status: "authenticated",
      update: async () => null,
    } as SessionValue;
    renderLanding("en", <LandingNav />);
    fireEvent.click(within(bar()).getByRole("button", { name: nav.openMenu }));
    const avatars = bar().querySelectorAll("span[class*=rounded-full]");
    expect(avatars).toHaveLength(2);
    expect(avatars[0]).not.toHaveClass("hidden");
  });

  it("puts Contact in the footer, pointing to krizaka.com/contact", () => {
    renderLanding("en", <LandingPage />, CLOSED);
    expect(
      within(screen.getByRole("contentinfo")).getByRole("link", { name: translations.en.landing.footer.contact }),
    ).toHaveAttribute("href", "https://www.krizaka.com/en/contact?topic=sovkora&source=sovkora-app");
  });
});
