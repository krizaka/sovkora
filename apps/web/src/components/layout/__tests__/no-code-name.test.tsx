/* eslint-disable react/display-name */
import "@testing-library/jest-dom";
import type * as React from "react";
import { render } from "@testing-library/react";
import { SessionContext } from "next-auth/react";
import { ThemeProvider } from "@krizaka/ui/theme";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import { translations } from "@/core/context/translations";
import { AppShell } from "@/components/layout/AppShell";
import { PublicShell } from "@/components/layout/PublicShell";
import { mockReducedMotion } from "@/features/landing/utils/reduced-motion.testkit";
import { LandingNav } from "@/features/landing/components/LandingNav";

jest.mock("next/link", () => ({ children, ...props }: { children: React.ReactNode; href: string }) => (
  <a {...props}>{children}</a>
));
jest.mock("next/navigation", () => ({
  usePathname: () => "/",
  useRouter: () => ({ push: jest.fn() }),
  useSelectedLayoutSegment: () => null,
}));
jest.mock("@/core/hooks/useAuth", () => ({
  useAuth: () => ({
    user: { name: "Mira", email: "mira@sovkora.com", role: "admin" },
    isAuthenticated: true,
    logout: jest.fn(),
  }),
}));
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    locale: "en",
    setLocale: jest.fn(),
    t: jest.requireActual("@/core/context/translations").translations.en,
  }),
}));
jest.mock("@/components/layout/NotificationBell", () => ({ NotificationBell: () => <button>Bell</button> }));
jest.mock("@/core/providers/Providers", () => ({
  Providers: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

/** What a person reads: the text and the words of the attributes a screen reader or a tooltip speaks. */
function visible(root: HTMLElement): string {
  const words = [root.textContent ?? ""];
  for (const el of root.querySelectorAll("[aria-label],[title],[alt],title")) {
    words.push(
      el.getAttribute("aria-label") ?? "",
      el.getAttribute("title") ?? "",
      el.getAttribute("alt") ?? "",
      el.tagName === "title" ? (el.textContent ?? "") : "",
    );
  }
  return words.join("\n");
}

beforeAll(mockReducedMotion);

const flags = { login: false, signup: false, github: false, google: false };

describe("no rendered shell shows the code name, on a development deployment too", () => {
  it("the landing bar", () => {
    const { container } = render(
      <AuthFlagsProvider flags={flags}>
        <SessionContext.Provider value={{ data: null, status: "unauthenticated", update: async () => null }}>
          <ThemeProvider>
            <PublicShell authFlags={flags} brand="Sovkora" showDevNotice>
              <LandingNav />
            </PublicShell>
          </ThemeProvider>
        </SessionContext.Provider>
      </AuthFlagsProvider>,
    );
    expect(visible(container)).toContain("Sovkora");
    expect(visible(container)).not.toMatch(/orazaka/i);
    expect(visible(container)).not.toMatch(/code name/i);
  });

  it("the public shell with its development banner", () => {
    const { container } = render(
      <PublicShell authFlags={flags} brand="Sovkora" showDevNotice>
        <p>page</p>
      </PublicShell>,
    );
    expect(visible(container)).toContain(translations.en.landing.stage.title);
    expect(visible(container)).not.toMatch(/orazaka/i);
  });

  it("the signed-in app shell", () => {
    const { container } = render(<AppShell>page</AppShell>);
    expect(visible(container)).not.toMatch(/orazaka/i);
  });
});
