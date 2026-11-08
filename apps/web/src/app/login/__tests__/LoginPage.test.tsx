/* eslint-disable react/display-name */
import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import type { ReactNode } from "react";
import LoginPage from "@/app/login/page";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import type { AuthFlags } from "@/core/config/auth-flags";

const withFlags = (flags: Partial<AuthFlags>) => (ui: ReactNode) => (
  <AuthFlagsProvider flags={{ login: true, signup: true, github: true, google: true, ...flags }}>
    {ui}
  </AuthFlagsProvider>
);
const open = withFlags({});

const mockPush = jest.fn();
const mockLoginWithGithub = jest.fn();
const mockLoginWithGoogle = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/login",
}));

jest.mock("next/link", () => {
  return ({ children, ...props }: { children: React.ReactNode; href: string }) => <a {...props}>{children}</a>;
});

jest.mock("next-auth/react", () => ({
  signIn: jest.fn(),
}));

const mockUseAuth = jest.fn(() => ({
  isAuthenticated: false,
  loginWithGithub: mockLoginWithGithub,
  loginWithGoogle: mockLoginWithGoogle,
}));
jest.mock("@/core/hooks/useAuth", () => ({ useAuth: () => mockUseAuth() }));

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      landing: {
        closed: jest.requireActual("@/core/context/translations.landing").landing.en.closed,
        status: jest.requireActual("@/core/context/translations.landing").landing.en.status,
        footer: { license: "Apache-2.0", by: "A Krizaka product" },
      },
      a11y: jest.requireActual("@/core/context/translations.automation").a11y.en,
      auth: {
        loginTitle: "Welcome Back",
        loginSubtitle: "Sign in to your account",
        emailLabel: "Email",
        passwordLabel: "Password",
        signInBtn: "Sign in",
        signingInBtn: "Signing In...",
        orContinueWith: "or continue with",
        noAccount: "No account?",
        registerLink: "Register",
        forgotPasswordLink: "Forgot password?",
        invalidCredentials: "Invalid credentials",
        unexpectedError: "Unexpected error",
        heroHeadline: "Own your intelligence.",
        heroTagline: "Runs on your own hardware.",
        heroFeature1: "Absolute privacy — data",
        heroFeature2: "Unified engine — one runtime",
        heroFeature3: "Infinite reach — MCP",
        legalPrivacy: "Privacy",
        legalTerms: "Terms",
        legalContact: "Contact",
        langSwitchLabel: "Language",
      },
    },
    locale: "en",
    setLocale: jest.fn(),
  }),
}));

describe("LoginPage", () => {
  afterEach(() => jest.clearAllMocks());

  it("renders title and subtitle", () => {
    render(open(<LoginPage />));
    expect(screen.getByText("Welcome Back")).toBeInTheDocument();
    expect(screen.getByText("Sign in to your account")).toBeInTheDocument();
  });

  it("renders email and password fields", () => {
    render(open(<LoginPage />));
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toBeInTheDocument();
  });

  it("renders sign in button", () => {
    render(open(<LoginPage />));
    expect(screen.getByText("Sign in")).toBeInTheDocument();
  });

  it("renders OAuth buttons", () => {
    render(open(<LoginPage />));
    expect(screen.getByText("GitHub")).toBeInTheDocument();
    expect(screen.getByText("Google")).toBeInTheDocument();
  });

  it("hides the Register way and the OAuth buttons while sign-up is closed", () => {
    render(withFlags({ signup: false, github: false, google: false })(<LoginPage />));
    expect(screen.queryByText("Register")).not.toBeInTheDocument();
    expect(screen.queryByText("GitHub")).not.toBeInTheDocument();
    expect(screen.queryByText("Google")).not.toBeInTheDocument();
    expect(screen.queryByText("or continue with")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
  });

  it("shows only the providers that are offered", () => {
    render(withFlags({ google: false })(<LoginPage />));
    expect(screen.getByText("GitHub")).toBeInTheDocument();
    expect(screen.queryByText("Google")).not.toBeInTheDocument();
  });

  it("is built on the entry template with the animated Sovkora mark in its brand panel", () => {
    const { container } = render(open(<LoginPage />));
    expect(container.querySelector(".brand-orazaka")).toBeInTheDocument();
    expect(screen.getByText("Own your intelligence.")).toBeInTheDocument();
    expect(container.querySelector("main")).toBeInTheDocument();
    expect(container.querySelector("[class*=login-]")).toBeNull();
  });

  it("renders register link", () => {
    render(open(<LoginPage />));
    expect(screen.getByText("Register")).toBeInTheDocument();
    expect(screen.getByText("Register").closest("a")).toHaveAttribute("href", "/register");
  });

  it("renders forgot password link", () => {
    render(open(<LoginPage />));
    expect(screen.getByText("Forgot password?")).toBeInTheDocument();
  });

  it("renders divider text", () => {
    render(open(<LoginPage />));
    expect(screen.getByText("or continue with")).toBeInTheDocument();
  });

  it("calls loginWithGithub", () => {
    render(open(<LoginPage />));
    fireEvent.click(screen.getByText("GitHub"));
    expect(mockLoginWithGithub).toHaveBeenCalled();
  });

  it("calls loginWithGoogle", () => {
    render(open(<LoginPage />));
    fireEvent.click(screen.getByText("Google"));
    expect(mockLoginWithGoogle).toHaveBeenCalled();
  });

  it("allows email input", () => {
    render(open(<LoginPage />));
    const emailInput = screen.getByLabelText("Email") as HTMLInputElement;
    fireEvent.change(emailInput, { target: { value: "test@orazaka.com" } });
    expect(emailInput.value).toBe("test@orazaka.com");
  });

  it("allows password input", () => {
    render(open(<LoginPage />));
    const passInput = screen.getByLabelText("Password") as HTMLInputElement;
    fireEvent.change(passInput, { target: { value: "secret123" } });
    expect(passInput.value).toBe("secret123");
  });
});

describe("LoginPage while sign-in is closed (the default)", () => {
  const banner = jest.requireActual("@/core/context/translations.landing").landing.en.closed.signInBanner as string;
  const mockFetch = jest.fn();
  beforeEach(() => {
    jest.clearAllMocks();
    global.fetch = mockFetch as unknown as typeof fetch;
  });

  it("shows the real sign-in screen under a calm banner, not only a notice", () => {
    render(<LoginPage />);
    expect(screen.getByText("Welcome Back")).toBeInTheDocument();
    expect(screen.getByText("Sign in to your account")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(banner);
    expect(banner).toMatch(/dev services/);
    expect(screen.queryByRole("link", { name: /watch/i })).toBeNull();
  });

  it("disables every field and button of the form", () => {
    render(<LoginPage />);
    expect(screen.getByLabelText("Email")).toBeDisabled();
    expect(screen.getByLabelText("Password")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Sign in" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "GitHub" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Google" })).not.toBeInTheDocument();
  });

  it("makes no backend call: no session hook, no sign-in, no fetch", () => {
    render(<LoginPage />);
    fireEvent.submit(document.getElementById("form-login") as HTMLFormElement);
    expect(mockUseAuth).not.toHaveBeenCalled();
    expect(mockLoginWithGithub).not.toHaveBeenCalled();
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("has the banner in French too", () => {
    const fr = jest.requireActual("@/core/context/translations.landing").landing.fr.closed.signInBanner as string;
    expect(fr.trim()).not.toBe("");
    expect(fr).not.toBe(banner);
  });
});
