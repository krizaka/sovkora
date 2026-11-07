import { renderHook } from "@testing-library/react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useRegister } from "@/features/auth/hooks/useRegister";

const mockPush = jest.fn();
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

jest.mock("next-auth/react", () => ({
  signIn: jest.fn().mockResolvedValue({ ok: true }),
}));

jest.mock("@/services/auth.api", () => ({
  AuthApi: {
    register: jest.fn().mockResolvedValue({}),
  },
}));

function wrapper({ children }: { children: React.ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client }, children);
}

describe("useRegister", () => {
  it("returns register function and isPending", () => {
    const { result } = renderHook(() => useRegister({ onSuccess: jest.fn(), onError: jest.fn() }), { wrapper });
    expect(typeof result.current.register).toBe("function");
    expect(result.current.isPending).toBe(false);
  });
});
