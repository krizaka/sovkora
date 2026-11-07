/**
 * @file auth.api.test.ts
 * @description Tests for the authentication API adapter (REST).
 */

import { AuthApi } from "@/services/auth.api";

const mockFetch = jest.fn();
global.fetch = mockFetch;

describe("AuthApi", () => {
  beforeEach(() => {
    mockFetch.mockClear();
  });

  describe("register", () => {
    it("sends registration payload to /api/auth/register", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ id: "new-user-123" }),
      });

      const payload = {
        username: "testuser",
        email: "test@example.com",
        password: "securepass",
        language: "en",
      };

      const result = await AuthApi.register(payload);

      expect(mockFetch).toHaveBeenCalledWith("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      expect(result).toEqual({ id: "new-user-123" });
    });

    it("throws error object on registration failure", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 409,
        json: async () => ({ error: "Email already registered" }),
      });

      const payload = {
        username: "testuser",
        email: "existing@example.com",
        password: "securepass",
        language: "en",
      };

      try {
        await AuthApi.register(payload);
        fail("Expected error to be thrown");
      } catch (err: unknown) {
        const error = err as Error & { status: number };
        expect(error.status).toBe(409);
        expect(error.message).toBe("Email already registered");
      }
    });
  });

  describe("verifyEmail", () => {
    it("POSTs the token to /api/auth/verify and returns true", async () => {
      mockFetch.mockResolvedValueOnce({ ok: true });

      const result = await AuthApi.verifyEmail("valid-token");

      expect(result).toBe(true);
      expect(mockFetch).toHaveBeenCalledWith("/api/auth/verify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token: "valid-token" }),
      });
    });

    it("trims whitespace from the token", async () => {
      mockFetch.mockResolvedValueOnce({ ok: true });

      await AuthApi.verifyEmail("  token-with-spaces  ");

      expect(mockFetch).toHaveBeenCalledWith(
        "/api/auth/verify",
        expect.objectContaining({
          body: JSON.stringify({ token: "token-with-spaces" }),
        }),
      );
    });

    it("throws when verification fails", async () => {
      mockFetch.mockResolvedValueOnce({ ok: false, status: 400 });

      await expect(AuthApi.verifyEmail("invalid-token")).rejects.toThrow("Invalid or expired verification token.");
    });
  });
});
