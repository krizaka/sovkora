import { renderHook } from "@testing-library/react";
import { useAuth } from "@/core/hooks/useAuth";

const mockSignIn = jest.fn();
const mockSignOut = jest.fn();

jest.mock("next-auth/react", () => ({
  useSession: () => ({
    data: {
      user: { id: "u1", name: "Test User", email: "test@orazaka.com" },
    },
    status: "authenticated",
  }),
  signIn: (...args: unknown[]) => mockSignIn(...args),
  signOut: (...args: unknown[]) => mockSignOut(...args),
}));

describe("useAuth", () => {
  it("returns authenticated state", () => {
    const { result } = renderHook(() => useAuth());
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.isLoading).toBe(false);
  });

  it("returns user from session", () => {
    const { result } = renderHook(() => useAuth());
    expect(result.current.user).toEqual({
      id: "u1",
      name: "Test User",
      email: "test@orazaka.com",
    });
  });

  it("calls signIn with github on loginWithGithub", () => {
    const { result } = renderHook(() => useAuth());
    result.current.loginWithGithub();
    expect(mockSignIn).toHaveBeenCalledWith("github", { callbackUrl: "/" });
  });

  it("calls signIn with google on loginWithGoogle", () => {
    const { result } = renderHook(() => useAuth());
    result.current.loginWithGoogle();
    expect(mockSignIn).toHaveBeenCalledWith("google", { callbackUrl: "/" });
  });

  it("calls signOut on logout", () => {
    const { result } = renderHook(() => useAuth());
    result.current.logout();
    expect(mockSignOut).toHaveBeenCalledWith({ callbackUrl: "/login" });
  });
});
