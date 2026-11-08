import { renderHook } from "@testing-library/react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useVerifyEmail } from "@/features/auth/hooks/useVerifyEmail";

jest.mock("@/services/auth.api", () => ({
  AuthApi: {
    verifyEmail: jest.fn().mockResolvedValue({}),
  },
}));

function wrapper({ children }: { children: React.ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("useVerifyEmail", () => {
  it("returns verify function and idle status", () => {
    const { result } = renderHook(() => useVerifyEmail(), { wrapper });
    expect(typeof result.current.verify).toBe("function");
    expect(result.current.status).toBe("idle");
    expect(result.current.errorMsg).toBeNull();
  });
});
