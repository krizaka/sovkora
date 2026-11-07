import "@testing-library/jest-dom";
import { render, screen, within } from "@testing-library/react";
import { AccountTab } from "@/features/profile/components/AccountTab";
import type { UserProfile } from "@/services/profile.api";
import { translations } from "@/core/context/translations";

jest.mock("@/core/hooks/useAuth", () => ({ useAuth: () => ({ logout: jest.fn(), user: { id: "u1", name: "Eric" } }) }));
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

const profile: UserProfile = {
  id: "0b7e2f4c-9c1d-4e0a-8a52-5f3a6d7c1e90",
  username: "eric",
  email: "eric@orazaka.io",
  authorities: ["ROLE_ADMIN"],
  preferences: { theme: "dark" },
};

describe("AccountTab (a calm settings page)", () => {
  const t = translations.en;

  it("is rows, not a hero: no giant initials tile, no tier pill", () => {
    render(<AccountTab profile={profile} t={t} />);
    expect(screen.queryByText("ER")).not.toBeInTheDocument();
    expect(screen.getByText("eric@orazaka.io")).toBeInTheDocument();
    expect(screen.getByText(t.profile.roles.ROLE_ADMIN)).toBeInTheDocument();
  });

  it("puts the Account ID last, under Advanced, in mono, after every other setting", () => {
    const { container } = render(<AccountTab profile={profile} t={t} />);
    const advanced = screen.getByRole("region", { name: t.profile.advancedTitle });
    const id = within(advanced).getByText(profile.id);
    expect(id.className).toContain("font-mono");
    // The ID is not in the first screen: it comes after the username, the e-mail and the danger zone.
    const html = container.innerHTML;
    expect(html.indexOf(profile.id)).toBeGreaterThan(html.indexOf("eric@orazaka.io"));
    expect(html.indexOf(profile.id)).toBeGreaterThan(html.indexOf(t.profile.dangerTitle));
    // exactly one copy action for it
    expect(within(advanced).getAllByRole("button", { name: t.a11y.copy })).toHaveLength(1);
  });

  it("says the plan quietly, with a link, not a pill", () => {
    render(<AccountTab profile={profile} t={t} />);
    expect(screen.getByRole("link", { name: t.profile.planLink })).toBeInTheDocument();
  });

  it("offers to sign out in the danger zone", () => {
    render(<AccountTab profile={profile} t={t} />);
    const danger = screen.getByRole("region", { name: t.profile.dangerTitle });
    expect(within(danger).getByRole("button", { name: t.profile.signOut })).toBeInTheDocument();
  });
});
