import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import VerifyPage from "@/app/verify/page";

const mockVerify = jest.fn();
let token: string | null = null;
let status: "idle" | "loading" | "success" | "error" = "idle";

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn() }),
  useSearchParams: () => ({ get: () => token }),
}));
jest.mock("@/features/auth/hooks/useVerifyEmail", () => ({
  useVerifyEmail: () => ({ verify: mockVerify, status, errorMsg: "Bad token" }),
}));
jest.mock("@/features/auth/components/AuthGate");
jest.mock("@/features/auth/components/AuthScreen");
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      verify: {
        loadingMessage: "Verifying",
        successTitle: "Verified",
        successDescription: "Done",
        errorTitle: "Failed",
        idleTitle: "Activate",
        idleDescription: "Enter your token",
        goToLogin: "Go to login",
        enterTokenManually: "Enter the token manually",
        tokenLabel: "Token",
        tokenPlaceholder: "Paste it",
        retryVerification: "Retry",
        activateAccount: "Activate account",
      },
    },
  }),
}));

describe("VerifyPage", () => {
  beforeEach(() => {
    token = null;
    status = "idle";
    jest.clearAllMocks();
  });

  it("verifies the typed token", async () => {
    render(<VerifyPage />);
    fireEvent.change(screen.getByLabelText("Token"), { target: { value: "abc123" } });
    fireEvent.click(screen.getByRole("button", { name: "Activate account" }));
    await waitFor(() => expect(mockVerify).toHaveBeenCalledWith("abc123"));
  });

  it("verifies nothing for an empty token", async () => {
    render(<VerifyPage />);
    fireEvent.click(screen.getByRole("button", { name: "Activate account" }));
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(mockVerify).not.toHaveBeenCalled();
  });

  it("verifies the token of the link at once and shows it", async () => {
    token = "from-link";
    render(<VerifyPage />);
    await waitFor(() => expect(mockVerify).toHaveBeenCalledWith("from-link"));
    expect(screen.getByLabelText("Token")).toHaveValue("from-link");
  });

  it("marks the field invalid and offers a retry once refused", () => {
    status = "error";
    render(<VerifyPage />);
    expect(screen.getByLabelText("Enter the token manually")).toBeInvalid();
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });
});
