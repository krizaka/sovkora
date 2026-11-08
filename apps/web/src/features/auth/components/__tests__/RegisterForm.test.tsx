import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { RegisterForm } from "@/features/auth/components/RegisterForm";

const mockRegister = jest.fn();

jest.mock("@/features/auth/hooks/useRegister", () => ({
  useRegister: () => ({
    register: mockRegister,
    isPending: false,
  }),
}));

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      auth: {
        usernameLabel: "Username",
        emailLabel: "Email",
        passwordLabel: "Password",
        confirmPasswordLabel: "Confirm Password",
        preferredLanguageLabel: "Language",
        createAccountBtn: "Create Account",
        creatingAccountBtn: "Creating...",
        usernameMinLength: "Username min 3 chars",
        passwordMinLength: "Password min 8 chars",
        passwordsDoNotMatch: "Passwords do not match",
        termsNotice: "Terms notice",
      },
    },
    locale: "en",
  }),
}));

jest.mock("@krizaka/orazaka-design-system", () => ({
  ...jest.requireActual("@krizaka/orazaka-design-system"),
  Input: (props: React.InputHTMLAttributes<HTMLInputElement>) => <input {...props} />,
  Button: ({ children, ...props }: { children: React.ReactNode } & React.ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button {...props}>{children}</button>
  ),
}));

describe("RegisterForm", () => {
  const onSuccess = jest.fn();
  const onError = jest.fn();

  afterEach(() => jest.clearAllMocks());

  it("renders all form fields", () => {
    render(<RegisterForm onSuccess={onSuccess} onError={onError} />);
    expect(screen.getByLabelText(/Username/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Email/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Password/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Confirm Password/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Language/)).toBeInTheDocument();
  });

  it("renders submit button", () => {
    render(<RegisterForm onSuccess={onSuccess} onError={onError} />);
    expect(screen.getByText("Create Account")).toBeInTheDocument();
  });

  it("renders language options", () => {
    render(<RegisterForm onSuccess={onSuccess} onError={onError} />);
    expect(screen.getByText("English")).toBeInTheDocument();
    expect(screen.getByText("Français")).toBeInTheDocument();
  });

  it("renders terms notice", () => {
    render(<RegisterForm onSuccess={onSuccess} onError={onError} />);
    expect(screen.getByText("Terms notice")).toBeInTheDocument();
  });

  it("validates short username", () => {
    render(<RegisterForm onSuccess={onSuccess} onError={onError} />);
    fireEvent.change(screen.getByLabelText(/Username/), { target: { value: "ab" } });
    fireEvent.change(screen.getByLabelText(/Email/), { target: { value: "a@b.com" } });
    fireEvent.change(screen.getByLabelText(/^Password/), { target: { value: "12345678" } });
    fireEvent.change(screen.getByLabelText(/Confirm Password/), { target: { value: "12345678" } });
    const form = document.getElementById("form-register")!;
    fireEvent.submit(form);
    expect(onError).toHaveBeenCalledWith("Username min 3 chars");
  });
});
