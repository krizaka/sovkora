/**
 * @file auth.api.ts
 * @description Stateless outbound adapter for authentication operations.
 * Extracts network logic previously inlined inside `RegisterForm.tsx` and `verify/page.tsx`.
 */

// ── Types ────────────────────────────────────────────────────────────────────

export interface RegisterPayload {
  username: string;
  email: string;
  password: string;
  language: string;
}

/**
 * Stateless adapter exposing authentication network operations.
 */
export const AuthApi = {
  /**
   * Submits a new user registration to the BFF REST proxy.
   *
   * @param payload - The registration form data.
   * @returns The parsed response data from the server.
   * @throws An error object with `status` and `error` fields on failure.
   */
  register: async (payload: RegisterPayload): Promise<unknown> => {
    const response = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    const data = await response.json();

    if (!response.ok) {
      const error = new Error(data.error || `Registration failed with status ${response.status}`);
      (error as Error & { status: number }).status = response.status;
      throw error;
    }

    return data;
  },

  /**
   * Verifies an email activation token via the unauthenticated BFF REST proxy
   * (`POST /api/auth/verify` → Router `/api/v1/auth/verify`).
   *
   * @param token - The verification token string.
   * @returns `true` if verification succeeded.
   * @throws {Error} If the token is invalid, expired, or the request fails.
   */
  verifyEmail: async (token: string): Promise<boolean> => {
    const response = await fetch("/api/auth/verify", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: token.trim() }),
    });

    if (!response.ok) {
      throw new Error("Invalid or expired verification token.");
    }

    return true;
  },
} as const;
