import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { SettingsView } from "@/features/profile/components/SettingsView";
import { translations } from "@/core/context/translations";

const mockSave = jest.fn();
const mockDiscard = jest.fn();
const mockReplace = jest.fn();
let mockAuthorities = ["ROLE_ADMIN"];
let mockDirty = true;

jest.mock("next/navigation", () => ({ useRouter: () => ({ replace: mockReplace, push: jest.fn() }) }));
jest.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));
jest.mock("@/features/profile/hooks/useProfile", () => ({
  useProfile: () => ({
    profile: {
      id: "user-123",
      username: "testadmin",
      email: "admin@orazaka.io",
      authorities: mockAuthorities,
      preferences: {},
    },
    isLoading: false,
    error: null,
  }),
}));
jest.mock("@/features/profile/hooks/useProfileForm", () => ({
  useProfileForm: () => ({
    form: {},
    setField: jest.fn(),
    setLanguage: jest.fn(),
    setTheme: jest.fn(),
    availableThemes: [],
    isLoading: false,
    isUpdating: false,
    isDirty: mockDirty,
    save: mockSave,
    discard: mockDiscard,
  }),
}));
jest.mock("@/features/profile/components/AccountTab", () => ({ AccountTab: () => <div data-testid="AccountTab" /> }));
jest.mock("@/features/profile/components/AppearanceTab", () => ({
  AppearanceTab: () => <div data-testid="AppearanceTab" />,
}));
jest.mock("@/features/profile/components/WorkspaceTab", () => ({
  WorkspaceTab: () => <div data-testid="WorkspaceTab" />,
}));
jest.mock("@/features/profile/components/IntegrationsTab", () => ({
  IntegrationsTab: () => <div data-testid="IntegrationsTab" />,
}));
jest.mock("@/features/profile/components/ApiKeysTab", () => ({ ApiKeysTab: () => <div data-testid="ApiKeysTab" /> }));
jest.mock("@/features/profile/components/WebhooksTab", () => ({
  WebhooksTab: () => <div data-testid="WebhooksTab" />,
}));

const t = translations.en;

describe("SettingsView (sub-nav + one section per route)", () => {
  beforeEach(() => {
    mockAuthorities = ["ROLE_ADMIN"];
    mockDirty = true;
  });
  afterEach(() => jest.clearAllMocks());

  it("lists the sections as links to their own routes, the current one marked", () => {
    render(<SettingsView section="appearance" />);
    const nav = screen.getByRole("navigation", { name: t.profile.navLabel });
    expect(nav.querySelector('a[href="/settings/account"]')).toBeInTheDocument();
    expect(nav.querySelector('a[href="/settings/workspace"]')).toBeInTheDocument();
    expect(nav.querySelector('a[href="/settings/api-keys"]')).toBeInTheDocument();
    expect(nav.querySelector('a[href="/settings/webhooks"]')).toBeInTheDocument();
    expect(nav.querySelector('a[href="/settings/appearance"]')).toHaveAttribute("aria-current", "page");
    expect(screen.getByTestId("AppearanceTab")).toBeInTheDocument();
    expect(screen.queryByTestId("AccountTab")).not.toBeInTheDocument();
  });

  it("has no profile hero and no segmented tabs", () => {
    render(<SettingsView section="account" />);
    expect(screen.queryByRole("tablist")).not.toBeInTheDocument();
    expect(screen.queryByText("TE")).not.toBeInTheDocument();
    expect(screen.queryByText(t.profile.enterpriseTier)).not.toBeInTheDocument();
  });

  it("the section list never draws a scrollbar", () => {
    render(<SettingsView section="account" />);
    const nav = screen.getByRole("navigation", { name: t.profile.navLabel });
    // @krizaka/ui 3.49: past a few sections the list is a menu (no scrolling row at all); else the row hides its scrollbar.
    if (nav.querySelector("details")) return;
    expect(nav.className).toContain("overflow-y-hidden");
    expect(nav.className).toContain("[scrollbar-width:none]");
  });

  it("hides the workspace from non-admins and sends their /settings/workspace to account", () => {
    mockAuthorities = ["ROLE_USER"];
    render(<SettingsView section="workspace" />);
    expect(
      screen.getByRole("navigation", { name: t.profile.navLabel }).querySelector('a[href="/settings/workspace"]'),
    ).toBeNull();
    expect(mockReplace).toHaveBeenCalledWith("/settings/account");
  });

  it("shows the sticky save bar on pending edits and saves", () => {
    render(<SettingsView section="appearance" />);
    fireEvent.click(screen.getByRole("button", { name: t.profile.saveChanges }));
    expect(mockSave).toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: t.profile.discard }));
    expect(mockDiscard).toHaveBeenCalled();
  });
});
