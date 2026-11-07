/* eslint-disable react/display-name */
import "@testing-library/jest-dom";
import { render, screen, fireEvent, within } from "@testing-library/react";
import { navWords } from "@krizaka/ui/app-frame";
import { AppShell } from "@/components/layout/AppShell";
import { translations } from "@/core/context/translations";

const mockLogout = jest.fn();
const mockUser = {
  current: { name: "Admin", email: "admin@orazaka.io", role: "admin" } as {
    name: string;
    email: string;
    role: string;
  },
};
const t = translations.en;

jest.mock("next/link", () => ({ children, ...props }: { children: React.ReactNode; href: string }) => (
  <a {...props}>{children}</a>
));
jest.mock("next/navigation", () => ({ usePathname: () => "/jobs" }));
jest.mock("@/core/hooks/useAuth", () => ({
  useAuth: () => ({
    user: mockUser.current,
    isAuthenticated: true,
    logout: mockLogout,
  }),
}));
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    locale: "en",
    setLocale: jest.fn(),
    t: jest.requireActual("@/core/context/translations").translations.en,
  }),
}));
jest.mock("@/components/layout/NotificationBell", () => ({
  NotificationBell: () => <button data-testid="notif-bell">Bell</button>,
}));

const railWords = () =>
  within(screen.getByRole("navigation", { name: t.appNav.label }))
    .getAllByRole("link")
    .map((a) => a.getAttribute("aria-label") ?? a.textContent);
const openMenu = () =>
  fireEvent.keyDown(screen.getByRole("button", { name: navWords.en.account }), {
    key: "Enter",
  });

describe("AppShell (the shared AppFrame)", () => {
  afterEach(() => {
    jest.clearAllMocks();
    mockUser.current = {
      name: "Admin",
      email: "admin@orazaka.io",
      role: "admin",
    };
  });

  it("puts where you work on the rail, in the app's order, and Settings and Admin under the avatar only", () => {
    render(<AppShell>page</AppShell>);
    const words = railWords();
    expect(words).toEqual(expect.arrayContaining(["Studios", "Chat", "Jobs"]));
    expect(words).not.toContain(t.appNav.settings);
    expect(words).not.toContain(t.appNav.admin);
  });

  it("draws the account menu: who you are, the places, Sign out last", () => {
    render(<AppShell>page</AppShell>);
    openMenu();
    const menu = screen.getByRole("menu");
    expect(within(menu).getByRole("menuitem", { name: t.profile.title })).toHaveAttribute("href", "/settings/account");
    expect(within(menu).getByRole("menuitem", { name: t.appNav.settings })).toHaveAttribute(
      "href",
      "/settings/appearance",
    );
    expect(within(menu).getByRole("menuitem", { name: t.appNav.admin })).toHaveAttribute("href", "/dashboard/admin");
    expect(within(menu).getAllByRole("menuitem").at(-1)).toHaveTextContent(t.appNav.signOut);
  });

  it("offers Administration to an admin only", () => {
    mockUser.current = {
      name: "Eric",
      email: "eric@example.com",
      role: "user",
    };
    render(<AppShell>page</AppShell>);
    openMenu();
    expect(
      within(screen.getByRole("menu")).queryByRole("menuitem", {
        name: t.appNav.admin,
      }),
    ).not.toBeInTheDocument();
  });

  it("signs out from the menu and keeps the notifications in the bar", () => {
    render(<AppShell>page</AppShell>);
    expect(screen.getByTestId("notif-bell")).toBeInTheDocument();
    openMenu();
    fireEvent.click(screen.getByRole("menuitem", { name: t.appNav.signOut }));
    expect(mockLogout).toHaveBeenCalled();
  });
});
