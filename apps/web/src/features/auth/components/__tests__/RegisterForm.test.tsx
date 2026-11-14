import "@testing-library/jest-dom";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
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
      interception: { fieldRequired: (label: string) => `${label} is required` },
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

  const fill = (values: { username: string; email: string; password: string; confirm: string }) => {
    fireEvent.change(screen.getByLabelText(/Username/), { target: { value: values.username } });
    fireEvent.change(screen.getByLabelText(/Email/), { target: { value: values.email } });
    fireEvent.change(screen.getByLabelText(/^Password/), { target: { value: values.password } });
    fireEvent.change(screen.getByLabelText(/Confirm Password/), { target: { value: values.confirm } });
    fireEvent.click(screen.getByRole("button", { name: "Create Account" }));
  };

  it("validates short username", async () => {
    render(<RegisterForm onSuccess={onSuccess} onError={onError} />);
    fill({ username: "ab", email: "a@b.com", password: "12345678", confirm: "12345678" });
    await waitFor(() => expect(onError).toHaveBeenCalledWith("Username min 3 chars"));
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it("validates a short password", async () => {
    render(<RegisterForm onSuccess={onSuccess} onError={onError} />);
    fill({ username: "alice", email: "a@b.com", password: "1234", confirm: "1234" });
    await waitFor(() => expect(onError).toHaveBeenCalledWith("Password min 8 chars"));
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it("validates that the passwords match", async () => {
    render(<RegisterForm onSuccess={onSuccess} onError={onError} />);
    fill({ username: "alice", email: "a@b.com", password: "12345678", confirm: "87654321" });
    await waitFor(() => expect(onError).toHaveBeenCalledWith("Passwords do not match"));
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it("asks for an email before registering", async () => {
    render(<RegisterForm onSuccess={onSuccess} onError={onError} />);
    fill({ username: "alice", email: "", password: "12345678", confirm: "12345678" });
    expect(await screen.findByText("Email is required")).toBeInTheDocument();
    expect(mockRegister).not.toHaveBeenCalled();
  });

  it("registers the trimmed username with the chosen language", async () => {
    render(<RegisterForm onSuccess={onSuccess} onError={onError} />);
    fireEvent.change(screen.getByLabelText(/Language/), { target: { value: "fr" } });
    fill({ username: " alice ", email: "a@b.com", password: "12345678", confirm: "12345678" });
    await waitFor(() =>
      expect(mockRegister).toHaveBeenCalledWith({
        username: "alice",
        email: "a@b.com",
        password: "12345678",
        language: "fr",
      }),
    );
  });
});
