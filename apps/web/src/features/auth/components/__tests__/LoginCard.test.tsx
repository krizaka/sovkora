import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { LoginCard } from "@/features/auth/components/LoginForm";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";

jest.mock(
  "next/link",
  () =>
    function MockLink({ children, ...props }: { children: ReactNode; href: string }) {
      return <a {...props}>{children}</a>;
    },
);
jest.mock("@/features/auth/components/AuthScreen");
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      a11y: { socialLogin: "Social" },
      interception: { fieldRequired: (label: string) => `${label} is required` },
      auth: {
        loginTitle: "Welcome",
        loginSubtitle: "Sign in",
        emailLabel: "Email",
        passwordLabel: "Password",
        forgotPasswordLink: "Forgot password?",
        signInBtn: "Sign in",
        signingInBtn: "Signing in",
        orContinueWith: "or",
      },
    },
  }),
}));

const renderCard = (props: Parameters<typeof LoginCard>[0]) =>
  render(
    <AuthFlagsProvider flags={{ login: true, signup: false, github: false, google: false }}>
      <LoginCard {...props} />
    </AuthFlagsProvider>,
  );

describe("LoginCard", () => {
  it("hands the credentials to the screen's owner", async () => {
    const onSubmit = jest.fn();
    renderCard({ onSubmit });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "a@b.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "pw" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith("a@b.com", "pw"));
  });

  it("asks for both fields before handing anything over", async () => {
    const onSubmit = jest.fn();
    renderCard({ onSubmit });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Email is required")).toBeInTheDocument();
    expect(screen.getByText("Password is required")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("keeps the forgotten-password link beside the field and its error", async () => {
    renderCard({ onSubmit: jest.fn() });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    await screen.findByText("Password is required");
    expect(screen.getByRole("link", { name: "Forgot password?" })).toHaveAttribute("href", "/forgot-password");
  });

  it("marks the fields invalid when the screen shows a refusal", () => {
    renderCard({ error: "Invalid credentials", onSubmit: jest.fn() });
    expect(screen.getByText("Invalid credentials")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toBeInvalid();
  });

  it("does not submit in the preview", async () => {
    const onSubmit = jest.fn();
    renderCard({ preview: true, onSubmit });
    expect(screen.getByRole("button", { name: "Sign in" })).toBeDisabled();
    expect(screen.getByLabelText("Email")).toBeDisabled();
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
