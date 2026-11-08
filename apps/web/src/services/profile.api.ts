/**
 * @file profile.api.ts
 * @description Stateless outbound adapter for user profile data.
 * Extracts network logic previously inlined inside `ProfileView.tsx`.
 */

import { restRequest } from "./rest-client";

// ── Types ────────────────────────────────────────────────────────────────────

export interface UserProfile {
  id: string;
  username: string;
  email: string;
  authorities: string[];
  preferences: Record<string, unknown>;
}

/**
 * Stateless adapter exposing user profile network operations.
 */
export const ProfileApi = {
  /**
   * Fetches the authenticated user's full profile from the BFF REST proxy
   * (`GET /api/v1/profile`).
   *
   * @returns The user profile including identity, authorities, and preferences.
   */
  fetch: async (): Promise<UserProfile> => {
    return restRequest<UserProfile>("/api/v1/profile");
  },
} as const;
