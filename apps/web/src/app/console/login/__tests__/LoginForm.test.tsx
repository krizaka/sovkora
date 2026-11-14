import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { LoginForm } from "@/app/console/login/LoginForm";
import { consoleCopy } from "@/console/core/copy/console";

const mockReplace = jest.fn();
const mockRefresh = jest.fn();
const mockSignIn = jest.fn();
let callbackUrl: string | null = null;

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace, refresh: mockRefresh }),
  useSearchParams: () => ({ get: () => callbackUrl }),
}));

jest.mock("next-auth/react", () => ({
  signIn: (...args: unknown[]) => mockSignIn(...args),
}));

const copy = consoleCopy.login;

function fill(email: string, password: string) {
  fireEvent.change(screen.getByLabelText(copy.email), { target: { value: email } });
  fireEvent.change(screen.getByLabelText(copy.password), { target: { value: password } });
  fireEvent.click(screen.getByRole("button", { name: copy.submit }));
}

describe("console LoginForm", () => {
  beforeEach(() => {
    callbackUrl = null;
    jest.clearAllMocks();
  });

  it("signs in with the credentials and goes to the console", async () => {
    mockSignIn.mockResolvedValue({ ok: true });
    render(<LoginForm />);
    fill("ops@sovkora.com", "secret");
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/"));
    expect(mockSignIn).toHaveBeenCalledWith("credentials", {
      email: "ops@sovkora.com",
      password: "secret",
      redirect: false,
    });
    expect(mockRefresh).toHaveBeenCalled();
  });

  it("never follows a callback that leaves the console", async () => {
    callbackUrl = "//evil.example";
    mockSignIn.mockResolvedValue({ ok: true });
    render(<LoginForm />);
    fill("ops@sovkora.com", "secret");
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/"));
  });

  it("says the credentials are refused and unlocks the fields", async () => {
    mockSignIn.mockResolvedValue({ ok: false });
    render(<LoginForm />);
    fill("ops@sovkora.com", "wrong");
    expect(await screen.findByText(copy.refused)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText(copy.email)).toBeEnabled());
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("says the identity service did not answer", async () => {
    mockSignIn.mockRejectedValue(new Error("down"));
    render(<LoginForm />);
    fill("ops@sovkora.com", "secret");
    expect(await screen.findByText(copy.unreachable)).toBeInTheDocument();
  });

  it("is disabled and calls nothing in the closed preview", () => {
    render(<LoginForm preview={{ banner: "Closed", link: "Demo", href: "/demo", locale: "en" }} />);
    expect(screen.getByLabelText(copy.email)).toBeDisabled();
    expect(screen.getByLabelText(copy.password)).toBeDisabled();
    expect(screen.getByRole("button", { name: copy.submit })).toBeDisabled();
    expect(mockSignIn).not.toHaveBeenCalled();
  });
});
