import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import ForgotPasswordPage from "@/app/forgot-password/page";

const mockFetch = jest.fn();

jest.mock("next/navigation", () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock(
  "next/link",
  () =>
    function MockLink({ children, ...props }: { children: ReactNode; href: string }) {
      return <a {...props}>{children}</a>;
    },
);
jest.mock("@/core/hooks/useAuth", () => ({ useAuth: () => ({ isAuthenticated: false }) }));
jest.mock("@/features/auth/components/AuthGate", () => ({
  AuthGate: ({ children }: { children: ReactNode }) => children,
}));
jest.mock("@/features/auth/components/AuthScreen", () => ({
  AuthScreen: ({ title, children }: { title: string; children: ReactNode }) => (
    <main>
      <h1>{title}</h1>
      {children}
    </main>
  ),
}));
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      auth: {
        forgotTitle: "Reset",
        forgotSubtitle: "We send a link",
        backToLogin: "Back",
        emailLabel: "Email",
        forgotSubmit: "Send link",
        forgotSuccess: "Sent",
        forgotSuccessDetail: "Check your inbox",
        forgotError: "Could not send",
      },
    },
  }),
}));

describe("ForgotPasswordPage", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.fetch = mockFetch as unknown as typeof fetch;
  });

  it("keeps the button disabled until an address is typed", () => {
    render(<ForgotPasswordPage />);
    expect(screen.getByRole("button", { name: "Send link" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "a@b.com" } });
    expect(screen.getByRole("button", { name: "Send link" })).toBeEnabled();
  });

  it("posts the address and confirms", async () => {
    mockFetch.mockResolvedValue({ ok: true });
    render(<ForgotPasswordPage />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "a@b.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Send link" }));
    expect(await screen.findByText("Check your inbox")).toBeInTheDocument();
    expect(mockFetch).toHaveBeenCalledWith(
      "/api/v1/auth/forgot",
      expect.objectContaining({ method: "POST", body: JSON.stringify({ email: "a@b.com" }) }),
    );
  });

  it("shows the server's refusal", async () => {
    mockFetch.mockResolvedValue({ ok: false, text: () => Promise.resolve("Too many tries") });
    render(<ForgotPasswordPage />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "a@b.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Send link" }));
    expect(await screen.findByText("Too many tries")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("button", { name: "Send link" })).toBeEnabled());
  });

  it("says so when the request fails", async () => {
    mockFetch.mockRejectedValue(new Error("offline"));
    render(<ForgotPasswordPage />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "a@b.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Send link" }));
    expect(await screen.findByText("Could not send")).toBeInTheDocument();
  });
});
